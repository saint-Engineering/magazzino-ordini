"""Regressioni della pipeline, senza rete, Maven o nuove dipendenze."""

import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SPECIFICA = importlib.util.spec_from_file_location(
    "build_continua", Path(__file__).parents[1] / "scripts" / "build_continua.py")
PIPELINE = importlib.util.module_from_spec(SPECIFICA)
SPECIFICA.loader.exec_module(PIPELINE)


class TestBuildContinua(unittest.TestCase):
    """Usa un repository temporaneo e API simulate per i casi critici."""

    def setUp(self):
        """Crea un grafo Git indipendente dal repository dell'applicazione."""
        self.temporanea = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporanea.cleanup)
        self.cartella = Path(self.temporanea.name)
        precedente = Path.cwd()
        os.chdir(self.cartella)
        self.addCleanup(os.chdir, precedente)
        subprocess.run(["git", "init", "-q", "-b", "main"], check=True)
        PIPELINE.git("config", "user.email", "test@example.invalid")
        PIPELINE.git("config", "user.name", "Test")
        self.ambiente = patch.dict(os.environ, {
            "GITHUB_REPOSITORY": "esempio/magazzino",
            "GITHUB_SERVER_URL": "https://github.com",
            "GITHUB_RUN_ID": "123", "GITHUB_RUN_ATTEMPT": "2",
            "GITHUB_STEP_SUMMARY": str(self.cartella / "summary"),
            "GH_TOKEN": "valore-di-test",
        })
        self.ambiente.start()
        self.addCleanup(self.ambiente.stop)
        self.radice = self.commit("Inizio")

    def commit(self, messaggio):
        """Aggiunge una modifica e restituisce la SHA esatta."""
        (self.cartella / "dato").write_text(messaggio)
        PIPELINE.git("add", "dato")
        PIPELINE.git("commit", "-q", "-m", messaggio)
        return PIPELINE.git("rev-parse", "HEAD")

    def evento(self, prima, dopo):
        """Riproduce il payload minimo di push, senza array commits."""
        return {"ref": "refs/heads/main", "before": prima, "after": dopo}

    def test_singolo_e_multi_commit(self):
        """Non limita la selezione al tip e conserva l'ordine topologico."""
        secondo = self.commit("Secondo")
        terzo = self.commit("Terzo")
        self.assertEqual([terzo], PIPELINE.nuovi_commit(self.evento(secondo, terzo)))
        self.assertEqual([secondo, terzo],
                         PIPELINE.nuovi_commit(self.evento(self.radice, terzo)))

    def test_primo_push_e_force_push(self):
        """Il primo push include la radice; il force-push usa la differenza di grafi."""
        vecchio = self.commit("Vecchia linea")
        PIPELINE.git("checkout", "-q", "--detach", self.radice)
        nuovo = self.commit("Nuova linea")
        self.assertEqual([self.radice, nuovo],
                         PIPELINE.nuovi_commit(self.evento("0" * 40, nuovo)))
        self.assertEqual([nuovo], PIPELINE.nuovi_commit(self.evento(vecchio, nuovo)))
        self.assertEqual([], PIPELINE.nuovi_commit(self.evento(nuovo, self.radice)))

    def test_merge_include_ramo_e_note_primo_parent(self):
        """Include i nuovi commit laterali e confronta il merge col primo parent."""
        PIPELINE.git("checkout", "-q", "-b", "feature")
        laterale = self.commit("Feature")
        PIPELINE.git("checkout", "-q", "main")
        PIPELINE.git("merge", "--no-ff", "-m", "Unione", "feature")
        unione = PIPELINE.git("rev-parse", "HEAD")
        self.assertEqual([laterale, unione],
                         PIPELINE.nuovi_commit(self.evento(self.radice, unione)))
        self.assertIn(f"Primo parent: `{self.radice}`", PIPELINE.note(unione, []))
        self.assertIn("albero vuoto", PIPELINE.note(self.radice, []))

    def test_note_trattano_messaggi_shell_come_dati(self):
        """Backtick, comandi shell, HTML e newline non vengono eseguiti."""
        messaggio = 'Titolo $(touch INIEZIONE); `id`\n\n</pre><script>alert("x")</script>'
        sha = self.commit(messaggio)
        testo = PIPELINE.note(sha, [{
            "name": f"{sha}-123-2-app.jar",
        }])
        self.assertIn("$(touch INIEZIONE)", testo)
        self.assertIn("&lt;script&gt;", testo)
        self.assertIn(sha, testo)
        self.assertIn("/actions/runs/123/attempts/2", testo)
        self.assertFalse((self.cartella / "INIEZIONE").exists())

    def test_errore_non_ferma_altri_commit_e_non_pubblica_falliti(self):
        """Una build fallita resta rossa, ma le altre possono essere pubblicate."""
        secondo = self.commit("Secondo")
        risultati = self.cartella / "risultati"
        with patch.object(PIPELINE, "pubblicata", return_value=False), \
                patch.object(PIPELINE, "costruisci",
                             side_effect=[RuntimeError("Maven"), None]) as costruisci, \
                patch("builtins.print"):
            self.assertEqual(1, PIPELINE.build([self.radice, secondo], risultati))
            self.assertEqual(2, costruisci.call_count)
        with patch.object(PIPELINE, "pubblica_commit") as pubblica:
            self.assertEqual(0, PIPELINE.pubblica([self.radice, secondo], risultati))
            pubblica.assert_called_once_with(secondo, risultati / secondo)
        self.assertEqual("fallita", json.loads(
            (risultati / "stati.json").read_text())[self.radice])

    def test_rerun_salta_sha_pubblicate(self):
        """Una prerelease completata evita sia rebuild sia nuovi allegati."""
        risultati = self.cartella / "risultati"
        with patch.object(PIPELINE, "pubblicata", return_value=True), \
                patch.object(PIPELINE, "costruisci") as costruisci, \
                patch.object(PIPELINE, "api") as api:
            self.assertEqual(0, PIPELINE.build([self.radice], risultati))
            PIPELINE.pubblica_commit(self.radice, risultati / self.radice)
            costruisci.assert_not_called()
            api.assert_not_called()

    def test_riprende_bozza_e_pubblica_solo_dopo_upload(self):
        """Una bozza incompleta viene completata prima di diventare visibile."""
        sha = self.radice
        cartella = self.cartella / sha
        cartella.mkdir()
        nome = f"{sha}-123-2-app.jar"
        (cartella / nome).write_bytes(b"jar-di-test")
        bozza = {"id": 10, "draft": True, "assets": [], "target_commitish": sha}
        with patch.object(PIPELINE, "pubblicata", return_value=False), \
                patch.object(PIPELINE, "controlla_tag"), \
                patch.object(PIPELINE, "cerca_bozza", return_value=bozza), \
                patch.object(PIPELINE, "api", side_effect=[
                    {}, [{"name": nome}], {},
                ]) as api:
            PIPELINE.pubblica_commit(sha, cartella)
            self.assertEqual("POST", api.call_args_list[0].args[1])
            aggiornamento = api.call_args_list[-1].args
            self.assertEqual("PATCH", aggiornamento[1])
            self.assertFalse(aggiornamento[2]["draft"])
            self.assertTrue(aggiornamento[2]["prerelease"])
            self.assertEqual("false", aggiornamento[2]["make_latest"])

    def test_upload_fallito_non_pubblica(self):
        """Un errore di upload lascia la release in bozza per il rerun."""
        sha = self.radice
        cartella = self.cartella / sha
        cartella.mkdir()
        (cartella / f"{sha}-123-2-app.jar").write_bytes(b"jar-di-test")
        with patch.object(PIPELINE, "pubblicata", return_value=False), \
                patch.object(PIPELINE, "controlla_tag"), \
                patch.object(PIPELINE, "cerca_bozza", return_value={
                    "id": 10, "draft": True, "assets": [], "target_commitish": sha,
                }), \
                patch.object(PIPELINE, "api", side_effect=RuntimeError("rete")) as api:
            with self.assertRaises(RuntimeError):
                PIPELINE.pubblica_commit(sha, cartella)
            self.assertEqual(1, api.call_count)

    def test_timeout_globale_visibile_senza_perdita_silenziosa(self):
        """Il limite di tempo lascia esplicitamente le SHA da elaborare."""
        risultati = self.cartella / "risultati"
        with patch.object(PIPELINE.time, "monotonic", side_effect=[0, 18001]), \
                patch.object(PIPELINE, "costruisci") as costruisci, \
                patch("builtins.print"):
            self.assertEqual(1, PIPELINE.build([self.radice], risultati))
            costruisci.assert_not_called()
        self.assertEqual({self.radice: "da elaborare"},
                         json.loads((risultati / "stati.json").read_text()))

    def test_nessun_limite_matrix_o_payload(self):
        """L'elenco completo Git può superare 256 job e 2048 commit del payload."""
        sha = "a" * 40
        elenco = [f"{numero:040x}" for numero in range(1, 3001)]
        with patch.object(PIPELINE, "git", return_value="\n".join(elenco)):
            self.assertEqual(elenco, PIPELINE.nuovi_commit(self.evento("0" * 40, sha)))

    def test_ambiente_maven_senza_token(self):
        """Il codice costruito non eredita il token usato dalle API."""
        with patch.dict(os.environ, {"GITHUB_TOKEN": "altro-valore-di-test"}):
            ambiente = PIPELINE.ambiente_build()
        self.assertNotIn("GH_TOKEN", ambiente)
        self.assertNotIn("GITHUB_TOKEN", ambiente)

    def test_rifiuta_artefatti_di_unaltra_sha(self):
        """Il publisher non allega file estranei al commit o al tentativo."""
        cartella = self.cartella / "artefatti"
        cartella.mkdir()
        (cartella / "sha-sbagliata.jar").write_bytes(b"jar-di-test")
        with patch.object(PIPELINE, "pubblicata", return_value=False), \
                patch.object(PIPELINE, "controlla_tag"):
            with self.assertRaises(ValueError):
                PIPELINE.pubblica_commit(self.radice, cartella)

    def test_build_esegue_wrapper_della_sha_e_seleziona_jar(self):
        """Esercita davvero worktree, subprocess, raccolta report e pulizia."""
        wrapper = self.cartella / "mvnw"
        wrapper.write_text(
            "#!/bin/sh\n"
            "test -z \"$GH_TOKEN\" && test \"$*\" = '-B clean package' || exit 1\n"
            "mkdir -p target/surefire-reports\n"
            "printf 'jar' > target/app.jar\n"
            "printf 'sorgenti' > target/app-sources.jar\n"
            "printf '<testsuite/>' > target/surefire-reports/TEST-prova.xml\n"
            "printf 'BUILD SUCCESS\\n'\n")
        wrapper.chmod(0o755)
        PIPELINE.git("add", "mvnw")
        sha = self.commit("Wrapper di prova")
        cartella = self.cartella / "risultati" / sha
        PIPELINE.costruisci(sha, cartella)
        self.assertEqual([f"{sha}-123-2-app.jar"],
                         [file.name for file in cartella.glob("*.jar")])
        self.assertIn("BUILD SUCCESS", (cartella / "maven.log").read_text())
        self.assertTrue((cartella / "surefire-reports" / "TEST-prova.xml").exists())
        self.assertEqual(1, PIPELINE.git("worktree", "list").count(str(self.cartella)))

    def test_build_fallita_non_raccoglie_jar(self):
        """Un wrapper fallito non può lasciare JAR pubblicabili."""
        wrapper = self.cartella / "mvnw"
        wrapper.write_text("#!/bin/sh\nmkdir -p target\ntouch target/app.jar\nexit 1\n")
        wrapper.chmod(0o755)
        PIPELINE.git("add", "mvnw")
        sha = self.commit("Wrapper fallito")
        cartella = self.cartella / "risultati" / sha
        with self.assertRaises(RuntimeError):
            PIPELINE.costruisci(sha, cartella)
        self.assertEqual([], list(cartella.glob("*.jar")))
        self.assertTrue((cartella / "maven.log").exists())

    def test_api_non_scambia_errori_di_rete_per_release_assente(self):
        """Solo HTTP 404 consente di trattare una SHA come non pubblicata."""
        for codice in (403, 429, 500):
            with self.subTest(codice=codice), \
                    patch.object(PIPELINE, "api", side_effect=subprocess.CalledProcessError(
                        1, ["gh"], stderr=f"gh: errore (HTTP {codice})")):
                with self.assertRaises(subprocess.CalledProcessError):
                    PIPELINE.release(self.radice)
        with patch.object(PIPELINE, "api", side_effect=subprocess.CalledProcessError(
                1, ["gh"], stderr="gh: Not Found (HTTP 404)")):
            self.assertIsNone(PIPELINE.release(self.radice))

    def test_tag_spostato_viene_rifiutato(self):
        """Il controllo usa GitHub, non una copia locale eventualmente obsoleta."""
        with patch.object(PIPELINE, "api", return_value={
            "object": {"type": "commit", "sha": "a" * 40},
        }):
            with self.assertRaises(ValueError):
                PIPELINE.controlla_tag(self.radice)

    def test_creazione_release_usa_sha_esatta_e_recupera_concorrenza(self):
        """Una collisione sulla creazione recupera la bozza, senza duplicarla."""
        sha = self.radice
        cartella = self.cartella / "risultati" / sha
        cartella.mkdir(parents=True)
        nome = f"{sha}-123-2-app.jar"
        (cartella / nome).write_bytes(b"jar-di-test")
        with patch.object(PIPELINE, "pubblicata", return_value=False), \
                patch.object(PIPELINE, "controlla_tag"), \
                patch.object(PIPELINE, "cerca_bozza", side_effect=[
                    None, {"id": 10, "draft": True, "assets": [], "target_commitish": sha},
                ]), \
                patch.object(PIPELINE, "api", side_effect=[
                    subprocess.CalledProcessError(1, ["gh"], stderr="HTTP 422"),
                    {}, [{"name": nome}], {},
                ]) as api:
            PIPELINE.pubblica_commit(sha, cartella)
            creazione = api.call_args_list[0].args[2]
            self.assertEqual(sha, creazione["target_commitish"])
            self.assertEqual(f"ci-{sha}", creazione["tag_name"])
            self.assertTrue(creazione["draft"])

    def test_bozze_cercate_nellelenco_paginato_non_per_tag(self):
        """Riprende anche una bozza che compare dopo la prima pagina."""
        sha = self.radice
        precedente = [{"tag_name": f"altro-{indice}", "draft": False}
                      for indice in range(100)]
        bozza = {"tag_name": f"ci-{sha}", "draft": True}
        with patch.object(PIPELINE, "BOZZE", None), \
                patch.object(PIPELINE, "api", side_effect=[precedente, [bozza]]) as api:
            self.assertEqual(bozza, PIPELINE.cerca_bozza(sha))
            self.assertEqual(bozza, PIPELINE.cerca_bozza(sha))
            self.assertEqual(2, api.call_count)
            self.assertIn("page=2", api.call_args_list[1].args[0])

    def test_before_non_recuperabile_non_inventa_un_intervallo(self):
        """Un force-push non recuperabile fallisce prima di avviare build."""
        precedente = "f" * 40
        with patch.object(PIPELINE.subprocess, "run", side_effect=[
            subprocess.CompletedProcess(["git"], 1),
            subprocess.CompletedProcess(["gh"], 0),
            subprocess.CalledProcessError(128, ["git", "fetch"]),
        ]), patch.object(PIPELINE, "git") as git:
            with self.assertRaises(subprocess.CalledProcessError):
                PIPELINE.nuovi_commit(self.evento(precedente, self.radice))
            git.assert_not_called()

    def test_upload_incompleto_non_diventa_release_pubblica(self):
        """Non basta una risposta upload riuscita: tutti i JAR devono risultare presenti."""
        sha = self.radice
        cartella = self.cartella / "risultati" / sha
        cartella.mkdir(parents=True)
        (cartella / f"{sha}-123-2-app.jar").write_bytes(b"jar-di-test")
        with patch.object(PIPELINE, "pubblicata", return_value=False), \
                patch.object(PIPELINE, "controlla_tag"), \
                patch.object(PIPELINE, "cerca_bozza", return_value={
                    "id": 10, "draft": True, "assets": [], "target_commitish": sha,
                }), \
                patch.object(PIPELINE, "api", side_effect=[{}, []]) as api:
            with self.assertRaises(ValueError):
                PIPELINE.pubblica_commit(sha, cartella)
            self.assertEqual(2, api.call_count)


if __name__ == "__main__":
    unittest.main()
