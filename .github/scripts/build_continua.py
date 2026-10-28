"""Build per SHA e prerelease: solo libreria standard Python e CLI del runner."""

import html
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time


BOZZE = None


def git(*argomenti):
    """Interroga Git senza interpretare dati come comandi shell."""
    return subprocess.check_output(["git", *argomenti], text=True).strip()


def api(percorso, metodo="GET", dati=None, file=None):
    """Usa il token del solo step corrente tramite la CLI ufficiale GitHub."""
    comando = ["gh", "api", percorso, "--method", metodo]
    ingresso = None
    if dati is not None:
        comando += ["--input", "-"]
        ingresso = json.dumps(dati)
    if file is not None:
        comando += ["--header", "Content-Type: application/java-archive",
                    "--input", str(file)]
    risposta = subprocess.run(comando, input=ingresso, text=True,
                              capture_output=True, check=True)
    return json.loads(risposta.stdout) if risposta.stdout.strip() else None


def release(sha):
    """Cerca una release pubblicata, distinguendo assenza ed errori di servizio."""
    try:
        return api(f"repos/{os.environ['GITHUB_REPOSITORY']}/releases/tags/ci-{sha}")
    except subprocess.CalledProcessError as errore:
        if "(HTTP 404)" in errore.stderr:
            return None
        raise


def cerca_bozza(sha, aggiorna=False):
    """Le bozze non sono restituite dall'endpoint release per tag."""
    global BOZZE
    if BOZZE is None or aggiorna:
        BOZZE = {}
        pagina = 1
        while True:
            gruppo = api(f"repos/{os.environ['GITHUB_REPOSITORY']}"
                         f"/releases?per_page=100&page={pagina}")
            for elemento in gruppo:
                if elemento["draft"]:
                    BOZZE[elemento["tag_name"]] = elemento
            if len(gruppo) < 100:
                break
            pagina += 1
    return BOZZE.get(f"ci-{sha}")


def controlla_tag(sha):
    """Rifiuta tag riservati che puntino a un commit differente."""
    percorso = f"repos/{os.environ['GITHUB_REPOSITORY']}/git"
    try:
        riferimento = api(f"{percorso}/ref/tags/ci-{sha}")["object"]
    except subprocess.CalledProcessError as errore:
        if "(HTTP 404)" in errore.stderr:
            return False
        raise
    for _ in range(10):
        if riferimento["type"] != "tag":
            break
        riferimento = api(f"{percorso}/tags/{riferimento['sha']}")["object"]
    if riferimento["type"] != "commit" or riferimento["sha"] != sha:
        raise ValueError(f"Il tag ci-{sha} punta a un'altra SHA")
    return True


def pubblicata(sha):
    """Considera completate soltanto prerelease pubblicate nel namespace CI."""
    trovata = release(sha)
    if trovata and not trovata["draft"]:
        if not controlla_tag(sha):
            raise ValueError(f"La release ci-{sha} non ha un tag valido")
        if not trovata["prerelease"]:
            raise ValueError(f"ci-{sha} non è una prerelease")
        return True
    return False


def nuovi_commit(evento):
    """Enumera il grafo Git completo, inclusi i parent laterali dei merge."""
    if evento["ref"] != "refs/heads/main" or evento.get("deleted"):
        raise ValueError("È richiesto un push non di cancellazione su main")
    prima, dopo = evento["before"], evento["after"]
    if not all(re.fullmatch(r"[0-9a-f]{40}", sha) for sha in (prima, dopo)):
        raise ValueError("SHA del push non valida")
    esclusione = []
    if prima != "0" * 40:
        presente = subprocess.run(["git", "cat-file", "-e", f"{prima}^{{commit}}"],
                                 capture_output=True)
        if presente.returncode:
            # Un force-push può aver rimosso before dai riferimenti correnti.
            subprocess.run(["gh", "auth", "setup-git"], check=True,
                           stdout=subprocess.DEVNULL)
            subprocess.run(["git", "fetch", "--no-tags", "origin", prima], check=True)
        esclusione = [f"^{prima}"]
    return git("rev-list", "--reverse", "--topo-order", dopo, *esclusione, "--").splitlines()


def ambiente_build():
    """Non passa credenziali GitHub al wrapper o al codice applicativo."""
    return {chiave: valore for chiave, valore in os.environ.items()
            if chiave not in {"GH_TOKEN", "GITHUB_TOKEN", "GH_ENTERPRISE_TOKEN"}}


def costruisci(sha, cartella):
    """Verifica un checkout pulito e conserva solo JAR e report della sua SHA."""
    cartella.mkdir(parents=True)
    with tempfile.TemporaryDirectory(prefix="build-ci-") as temporanea:
        sorgenti = Path(temporanea) / "sorgenti"
        subprocess.run(["git", "worktree", "add", "--detach", str(sorgenti), sha],
                       check=True, stdout=subprocess.DEVNULL)
        try:
            with (cartella / "maven.log").open("w") as log:
                esito = subprocess.run(
                    ["timeout", "--kill-after=30s", "20m", "./mvnw", "-B", "clean", "package"],
                    cwd=sorgenti, env=ambiente_build(), stdout=log,
                    stderr=subprocess.STDOUT).returncode
            for rapporto in sorgenti.glob("target/*-reports/*.xml"):
                destinazione = cartella / rapporto.parent.name
                destinazione.mkdir(exist_ok=True)
                shutil.copyfile(rapporto, destinazione / rapporto.name)
            if esito:
                raise RuntimeError(f"Maven terminato con codice {esito}; vedere maven.log")
            jars = [file for file in sorgenti.glob("target/*.jar")
                    if not file.name.endswith(("-sources.jar", "-javadoc.jar", "-tests.jar"))]
            if not jars:
                raise RuntimeError("Build riuscita ma nessun JAR pertinente trovato")
            for file in jars:
                nome = (f"{sha}-{os.environ['GITHUB_RUN_ID']}-"
                        f"{os.environ['GITHUB_RUN_ATTEMPT']}-{file.name}")
                if not re.fullmatch(r"[A-Za-z0-9._-]+\.jar", nome):
                    raise ValueError("Nome JAR non valido")
                shutil.copyfile(file, cartella / nome)
        finally:
            subprocess.run(["git", "worktree", "remove", "--force", str(sorgenti)],
                           check=True, stdout=subprocess.DEVNULL)


def riepilogo(stati):
    """Rende visibili successi, errori e commit rimasti nel summary del run."""
    testo = "## Build continua per commit\n\n| SHA | Esito |\n|---|---|\n"
    testo += "".join(f"| `{sha}` | {stato} |\n" for sha, stato in stati.items())
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
        summary.write(testo)


def build(commit, risultati):
    """Continua dopo gli errori e fallisce esplicitamente se resta lavoro."""
    stati = dict.fromkeys(commit, "da elaborare")
    risultati.mkdir(parents=True, exist_ok=True)
    scadenza = time.monotonic() + 5 * 60 * 60
    for sha in commit:
        if time.monotonic() >= scadenza:
            print("Limite preventivo di 5 ore: rieseguire tutti i job per completare.")
            break
        try:
            if pubblicata(sha):
                stati[sha] = "già pubblicata"
            else:
                costruisci(sha, risultati / sha)
                stati[sha] = "riuscita"
        except Exception as errore:
            stati[sha] = "fallita"
            cartella = risultati / sha
            cartella.mkdir(parents=True, exist_ok=True)
            (cartella / "errore.txt").write_text(
                str(errore) + "\n" + (getattr(errore, "stderr", "") or ""),
                encoding="utf-8")
            print(f"::error::Build {sha}: {type(errore).__name__}; vedere log e artefatti")
        (risultati / "stati.json").write_text(json.dumps(stati), encoding="utf-8")
    (risultati / "stati.json").write_text(json.dumps(stati), encoding="utf-8")
    riepilogo(stati)
    return int(any(stato in {"fallita", "da elaborare"} for stato in stati.values()))


def note(sha, allegati):
    """Genera note italiane da Git, non da messaggi interpolati nella shell."""
    repository = os.environ["GITHUB_REPOSITORY"]
    base = f"{os.environ['GITHUB_SERVER_URL']}/{repository}"
    parent = git("rev-list", "--parents", "-n", "1", sha, "--").split()[1:]
    if parent:
        confronto = f"Primo parent: `{parent[0]}` (anche per i merge)."
        modifiche = git("diff", "--stat", parent[0], sha, "--")
    else:
        confronto = "Commit radice: confronto con l'albero vuoto."
        modifiche = git("show", "--format=", "--stat", "--root", sha, "--")
    messaggio = git("show", "-s", "--format=%B", sha, "--")
    # Il limite del body GitHub non deve troncare silenziosamente le note.
    def blocco(testo):
        """Quota HTML e segnala esplicitamente l'eventuale riduzione del testo."""
        limite = 6000
        ridotto = testo[:limite] + ("\n[… testo abbreviato …]" if len(testo) > limite else "")
        return "<pre>" + html.escape(ridotto) + "</pre>"

    testo = (f"# Build continua — prerelease non stabile\n\n"
             f"SHA completa: `{sha}`\n\n[Commit]({base}/commit/{sha}) · "
             f"[Run di pubblicazione]({base}/actions/runs/{os.environ['GITHUB_RUN_ID']})\n\n"
             f"## Messaggio\n\n{blocco(messaggio)}\n\n"
             f"## Modifiche\n\n{confronto}\n\n{blocco(modifiche)}\n\n"
             "## Verifiche e artefatti\n\n"
             "JDK 25; `./mvnw -B clean package` riuscito (test e packaging Maven "
             "configurati nel commit). Non sono attestati `verify`, Spotless o scansioni di sicurezza. "
             "Versione Maven invariata. I log e i report sono negli artefatti dei run.\n\n")
    for allegato in allegati:
        corrispondenza = re.fullmatch(
            rf"{sha}-(\d+)-(\d+)-[A-Za-z0-9._-]+\.jar", allegato["name"])
        if not corrispondenza:
            raise ValueError("Allegato estraneo alla SHA della prerelease")
        run, tentativo = corrispondenza.groups()
        testo += (f"- `{allegato['name']}` — "
                  f"[build, tentativo {tentativo}]({base}/actions/runs/{run}/attempts/{tentativo})\n")
    if len(testo.encode("utf-8")) > 100000:
        raise ValueError("Note troppo lunghe: pubblicazione interrotta esplicitamente")
    return testo


def pubblica_commit(sha, cartella):
    """Riprende le bozze senza sovrascrivere gli allegati di altri tentativi."""
    if pubblicata(sha):
        return
    controlla_tag(sha)
    jars = sorted(cartella.glob("*.jar"))
    prefisso = f"{sha}-{os.environ['GITHUB_RUN_ID']}-{os.environ['GITHUB_RUN_ATTEMPT']}-"
    if not jars or any(file.is_symlink() or not file.is_file()
                       or not re.fullmatch(r"[A-Za-z0-9._-]+\.jar", file.name)
                       or not file.name.startswith(prefisso) for file in jars):
        raise ValueError("JAR assenti o non appartenenti al tentativo corrente")
    percorso = f"repos/{os.environ['GITHUB_REPOSITORY']}/releases"
    bozza = cerca_bozza(sha)
    if bozza is None:
        try:
            bozza = api(percorso, "POST", {
                "tag_name": f"ci-{sha}", "target_commitish": sha,
                "name": f"Build continua {sha}", "draft": True, "prerelease": True,
            })
        except subprocess.CalledProcessError:
            # Un altro push può introdurre la stessa SHA contemporaneamente.
            if pubblicata(sha):
                return
            bozza = cerca_bozza(sha, aggiorna=True)
            if bozza is None:
                raise
    if not bozza["draft"]:
        if not pubblicata(sha):
            raise ValueError("Release concorrente non valida")
        return
    if bozza.get("target_commitish") != sha:
        raise ValueError("La bozza non identifica il commit costruito")
    identificativo = bozza["id"]
    for file in jars:
        if not any(asset["name"] == file.name for asset in bozza["assets"]):
            api(f"https://uploads.github.com/repos/{os.environ['GITHUB_REPOSITORY']}"
                f"/releases/{identificativo}/assets?name={file.name}", "POST", file=file)
    allegati = []
    pagina = 1
    while True:
        gruppo = api(f"{percorso}/{identificativo}/assets?per_page=100&page={pagina}")
        allegati.extend(gruppo)
        if len(gruppo) < 100:
            break
        pagina += 1
    if not {file.name for file in jars}.issubset({file["name"] for file in allegati}):
        raise ValueError("Upload incompleto: la release resta in bozza")
    api(f"{percorso}/{identificativo}", "PATCH", {
        "body": note(sha, allegati), "draft": False,
        "prerelease": True, "make_latest": "false",
    })


def pubblica(commit, risultati):
    """Pubblica tutti i successi anche quando un'altra build è fallita."""
    stati = json.loads((risultati / "stati.json").read_text(encoding="utf-8"))
    errori = False
    for sha in commit:
        if stati.get(sha) == "riuscita":
            try:
                if (risultati / sha).is_symlink():
                    raise ValueError("Cartella artefatti non valida")
                pubblica_commit(sha, risultati / sha)
                stati[sha] = "pubblicata"
            except Exception as errore:
                errori = True
                stati[sha] = "pubblicazione fallita"
                print(f"::error::Pubblicazione {sha}: {type(errore).__name__}")
    riepilogo(stati)
    return int(errori)


def main():
    """Ricalcola i commit dall'evento originale anche durante un rerun."""
    evento = json.loads(Path(os.environ["GITHUB_EVENT_PATH"]).read_text(encoding="utf-8"))
    commit = nuovi_commit(evento)
    risultati = Path(os.environ["RISULTATI"])
    return {"build": build, "pubblica": pubblica}[sys.argv[1]](commit, risultati)


if __name__ == "__main__":
    sys.exit(main())
