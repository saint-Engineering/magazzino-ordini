# Rilasciare una nuova versione con un tag

Un tag `v*` spinto su GitHub avvia il workflow `.github/workflows/build.yml`.
Il job `build` ripete i test e, solo se sono verdi, il job `release` crea una
**release in bozza** con il jar allegato. Pubblicarla resta una decisione di una
persona.

```
git tag v0.2.0  →  git push origin v0.2.0  →  Actions: build → release
                →  Releases: bozza con jar e note  →  tu: controlli e pubblichi
```

## 0 · Prima di iniziare

- Tutte le PR della versione sono mergiate su `main`.
- L'ultimo run di **Actions → Build e release in bozza** su `main` è verde.
- Nel tuo clone non hai modifiche in sospeso: `git status` → *working tree clean*.

## 1 · Scegli il numero di versione

Si usa il [versionamento semantico](https://semver.org/lang/it/) `vMAGGIORE.MINORE.PATCH`:

| Cosa è cambiato | Quale numero sale | Esempio |
|---|---|---|
| Solo correzioni, nessuna funzione nuova | PATCH | `v0.2.0` → `v0.2.1` |
| Funzioni nuove compatibili (es. il codice di tracking) | MINORE | `v0.1.0` → `v0.2.0` |
| Cambi che rompono chi usa l'app o le sue API | MAGGIORE | `v0.9.0` → `v1.0.0` |

Per vedere le versioni già uscite: `git tag -l "v*"` oppure la pagina **Releases**.
Il tag deve iniziare con `v` minuscola, altrimenti il workflow non parte.

## 2 · Aggiorna main e controlla in locale (facoltativo ma consigliato)

```bash
git switch main
git pull
./mvnw verify        # Windows: .\mvnw.cmd verify
```

## 3 · Crea il tag

```bash
git tag -a v0.2.0 -m "v0.2.0: codice di tracking alla spedizione"
```

- `-a` crea un tag **annotato**: con autore, data e messaggio, adatto alle release.
  Senza `-a` (`git tag v0.2.0`) il tag è "leggero", cioè solo un puntatore.
- Senza altri argomenti il tag va sull'ultimo commit del branch corrente, cioè `main`.
  Per un commit preciso: `git tag -a v0.2.0 <sha> -m "..."`.
- Controlla: `git show v0.2.0` mostra il commit giusto?

## 4 · Spingi SOLO quel tag

```bash
git push origin v0.2.0
```

Non usare `git push --tags` né il comando "Push Tags" di VS Code: spingono **tutti**
i tag locali, compresi quelli di prova o di backup (es. `backup/...`).

Il ruleset di `main` non blocca i tag: riguarda solo il branch.

## 5 · Segui il workflow

**Actions → Build e release in bozza**: compare un'esecuzione con il nome del tag.

1. `build`: test, formattazione, jar. Se è rosso, la release non viene creata
   (vedi "Se qualcosa va storto").
2. `release`: parte dopo `build` e crea la bozza.

Annota orari ed esito nel [registro della pipeline](registro.md), alla riga
"Tag e release in bozza".

## 6 · Controlla e pubblica la release

**Releases → la bozza `v0.2.0` → Edit**:

1. Leggi le note generate dalle PR mergiate: correggi o aggiungi ciò che serve.
2. Controlla l'allegato: c'è il jar? È quello giusto?
3. Facoltativo: spunta **Set as a pre-release** se non è ancora stabile.
4. **Publish release**. Se non sei pronto, lasciala in bozza: chi non ha accesso
   in scrittura al repo non la vede.

Il jar si chiama come `artifactId` e `version` del `pom.xml`
(oggi `magazzino-ordini-fork-0.0.1-SNAPSHOT.jar`). Se vuoi che il nome riporti la
versione del tag, aggiorna `<version>` nel `pom.xml` con una PR **prima** di creare
il tag (es. `<version>0.2.0</version>`).

## Se qualcosa va storto

| Problema | Cosa fare |
|---|---|
| `build` rosso | Nessuna release creata. Correggi con una PR su `main`, poi rifai il tag (riga sotto). |
| Tag sul commit sbagliato, release **non** pubblicata | Elimina la bozza in Releases, poi `git push --delete origin v0.2.0` e `git tag -d v0.2.0`, infine ripeti dal punto 3. |
| `release` rosso: *release already exists* | Esiste già una release con quel tag: eliminala in Releases e fai **Re-run failed jobs** dall'esecuzione. |
| Il workflow non parte | Il tag non inizia con `v`, oppure `build.yml` non è su `main`. |
| Release **già pubblicata** con un errore | Non spostare il tag: chi l'ha scaricata ha già quella versione. Correggi con una PR ed esci con la versione successiva (`v0.2.1`). |

## Riepilogo dei comandi

```bash
git switch main && git pull
git tag -a v0.2.0 -m "v0.2.0: ..."
git push origin v0.2.0
# poi: Actions (verde) → Releases → bozza → controlli → Publish release
```

## Documentazione

- [Gestire le release su GitHub](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository)
- [Note di rilascio generate automaticamente](https://docs.github.com/en/repositories/releasing-projects-on-github/automatically-generated-release-notes)
- [git tag](https://git-scm.com/docs/git-tag) · [Tag nel libro Pro Git](https://git-scm.com/book/en/v2/Git-Basics-Tagging)
- [`gh release create`](https://cli.github.com/manual/gh_release_create), il comando usato dal job `release`
