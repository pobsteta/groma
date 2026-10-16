"""HTTP API: resumable package upload, task state, results for nemetonshiny.

Upload protocol (resumable after a network cut, the phone resends from the
offset the server reports):

1. ``POST /televersements`` {projet_id?, plot_id, taille, sha256} -> {id, taille_morceau, recu}
2. ``PUT /televersements/{id}?offset=N`` body = bytes from N; N must equal
   the number of bytes already received (409 otherwise, with the expected offset)
3. ``GET /televersements/{id}`` -> {recu, taille}
4. ``POST /televersements/{id}/terminer`` -> checks size and SHA-256, queues
   the task -> {tache_id}

Results: ``GET /placettes[?projet_id=]`` (state of every plot),
``GET /taches/{id}``,
``GET /projets/{projet_id}/placettes/{plot_id}/resultat.gpkg | rapport.json | rapport.html``.

QField projects for the phones (nemetonshiny -> server -> Groma, see
``projets_qfield``): ``PUT /projets/{projet_id}/qfield/fichiers/{nom}`` (raw
body), ``POST /projets/{projet_id}/qfield/publier``, ``GET /projets``,
``GET /projets/{projet_id}/qfield`` (manifest),
``GET /projets/{projet_id}/qfield/{version}/{nom}`` (``Range`` supported).

Plots are keyed by (``projet_id``, ``plot_id``): two sampling plans may both
have a ``P001``. Without ``projet_id`` (phones older than the project list),
the default project ``""`` is used, which the routes without ``/projets/``
keep serving.
"""

from __future__ import annotations

import hashlib
import json
import re
import uuid
from pathlib import Path

from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.responses import FileResponse, JSONResponse
from pydantic import BaseModel, Field

from .config import Config
from .file_attente import FileAttente
from .projets_qfield import ErreurProjet, Fichier, ProjetsQfield

PLOT_ID = re.compile(r"^[A-Za-z0-9_.-]{1,64}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")


class DemandeTeleversement(BaseModel):
    projet_id: str = Field(default="", pattern=r"^(|[A-Za-z0-9_.-]{1,64})$")
    plot_id: str = Field(pattern=PLOT_ID.pattern)
    taille: int = Field(gt=0)
    sha256: str = Field(pattern=SHA256.pattern)


class FichierPublie(BaseModel):
    nom: str
    sha256: str = Field(pattern=SHA256.pattern)
    taille: int = Field(gt=0)


class DemandePublication(BaseModel):
    projet_nom: str = Field(min_length=1, max_length=200)
    fichiers: list[FichierPublie] = Field(min_length=1, max_length=4)
    genere_le: str | None = None
    nemeton_version: str | None = None


def creer_app(config: Config | None = None) -> FastAPI:
    cfg = config or Config()
    file = FileAttente(cfg.base)
    dossier_tel = cfg.dossier_donnees / "televersements"
    dossier_paquets = cfg.dossier_donnees / "paquets"
    for d in (dossier_tel, dossier_paquets):
        d.mkdir(parents=True, exist_ok=True)

    projets = ProjetsQfield(cfg.dossier_donnees / "projets_qfield", cfg.taille_max_projet, cfg.versions_projet)

    app = FastAPI(title="Groma", version="0.1.0")

    @app.exception_handler(ErreurProjet)
    def erreur_projet(_request: Request, e: ErreurProjet):
        return JSONResponse({"detail": str(e)}, status_code=e.code)

    def auth(authorization: str | None = Header(default=None)) -> None:
        if cfg.jeton and authorization != f"Bearer {cfg.jeton}":
            raise HTTPException(401, "Jeton invalide.")

    def meta(tid: str) -> tuple[Path, dict]:
        if not re.fullmatch(r"[0-9a-f-]{36}", tid):
            raise HTTPException(404, "Téléversement inconnu.")
        m = dossier_tel / f"{tid}.json"
        if not m.exists():
            raise HTTPException(404, "Téléversement inconnu.")
        return dossier_tel / f"{tid}.part", json.loads(m.read_text())

    @app.post("/televersements", dependencies=[Depends(auth)])
    def creer(d: DemandeTeleversement):
        if d.taille > cfg.taille_max:
            raise HTTPException(413, "Paquet trop volumineux.")
        tid = str(uuid.uuid4())
        (dossier_tel / f"{tid}.json").write_text(d.model_dump_json())
        (dossier_tel / f"{tid}.part").write_bytes(b"")
        return {"id": tid, "taille_morceau": cfg.taille_morceau, "recu": 0}

    @app.get("/televersements/{tid}", dependencies=[Depends(auth)])
    def etat(tid: str):
        part, m = meta(tid)
        return {"recu": part.stat().st_size, "taille": m["taille"]}

    @app.put("/televersements/{tid}", dependencies=[Depends(auth)])
    async def morceau(tid: str, offset: int, request: Request):
        part, m = meta(tid)
        recu = part.stat().st_size
        if offset != recu:
            return JSONResponse({"detail": "Décalage inattendu.", "recu": recu}, status_code=409)
        donnees = await request.body()
        if recu + len(donnees) > m["taille"]:
            raise HTTPException(400, "Le morceau dépasse la taille annoncée.")
        with open(part, "ab") as f:
            f.write(donnees)
        return {"recu": recu + len(donnees)}

    @app.post("/televersements/{tid}/terminer", dependencies=[Depends(auth)])
    def terminer(tid: str):
        part, m = meta(tid)
        if part.stat().st_size != m["taille"]:
            raise HTTPException(409, "Téléversement incomplet.")
        h = hashlib.sha256()
        with open(part, "rb") as f:
            for bloc in iter(lambda: f.read(1 << 20), b""):
                h.update(bloc)
        if h.hexdigest() != m["sha256"]:
            raise HTTPException(422, "Somme de contrôle SHA-256 incorrecte.")
        projet_id = m.get("projet_id", "")
        cible = dossier_paquets / (projet_id or "_") / f"{m['plot_id']}_{tid}.groma.zip"
        cible.parent.mkdir(exist_ok=True)
        part.replace(cible)
        (dossier_tel / f"{tid}.json").unlink()
        return {"tache_id": file.ajouter(m["plot_id"], str(cible), projet_id)}

    @app.get("/taches/{tid}", dependencies=[Depends(auth)])
    def tache(tid: str):
        t = file.obtenir(tid)
        if not t:
            raise HTTPException(404, "Tâche inconnue.")
        return _public(t)

    @app.get("/placettes", dependencies=[Depends(auth)])
    def placettes(projet_id: str | None = None):
        return [_public(t) for t in file.lister(projet_id)]

    def resultat(projet_id: str, plot_id: str, champ: str, media: str):
        if not PLOT_ID.match(plot_id) or (projet_id and not PLOT_ID.match(projet_id)):
            raise HTTPException(404, "Placette inconnue.")
        t = file.derniere(plot_id, "terminee", projet_id)
        if not t or not t[champ]:
            raise HTTPException(404, "Aucun résultat définitif pour cette placette.")
        return FileResponse(t[champ], media_type=media, filename=Path(t[champ]).name)

    fichiers = {
        "resultat.gpkg": ("gpkg", "application/geopackage+sqlite3"),
        "rapport.json": ("rapport_json", "application/json"),
        "rapport.html": ("rapport_html", "text/html"),
    }

    @app.get("/projets/{projet_id}/placettes/{plot_id}/{fichier}", dependencies=[Depends(auth)])
    def resultat_projet(projet_id: str, plot_id: str, fichier: str):
        if fichier not in fichiers:
            raise HTTPException(404, "Fichier inconnu.")
        return resultat(projet_id, plot_id, *fichiers[fichier])

    @app.get("/placettes/{plot_id}/{fichier}", dependencies=[Depends(auth)])
    def resultat_defaut(plot_id: str, fichier: str):
        if fichier not in fichiers:
            raise HTTPException(404, "Fichier inconnu.")
        return resultat("", plot_id, *fichiers[fichier])

    @app.put("/projets/{projet_id}/qfield/fichiers/{nom}", dependencies=[Depends(auth)])
    async def deposer_fichier(projet_id: str, nom: str, request: Request):
        cible = projets.chemin_attente(projet_id, nom)
        tmp = cible.with_name(cible.name + ".part")
        taille = 0
        try:
            with open(tmp, "wb") as f:
                async for bloc in request.stream():
                    taille += len(bloc)
                    if taille > cfg.taille_max_projet:
                        raise ErreurProjet(413, "Projet trop volumineux.")
                    f.write(bloc)
            if taille == 0:
                raise ErreurProjet(400, "Fichier vide.")
            tmp.replace(cible)
        finally:
            tmp.unlink(missing_ok=True)
        f = projets.fin_depot(cible)
        return {"nom": f.nom, "sha256": f.sha256, "taille": f.taille}

    @app.post("/projets/{projet_id}/qfield/publier", dependencies=[Depends(auth)])
    def publier(projet_id: str, d: DemandePublication):
        fichiers = [Fichier(f.nom, f.sha256, f.taille) for f in d.fichiers]
        m = projets.publier(projet_id, d.projet_nom, fichiers, d.genere_le, d.nemeton_version)
        return {"projet_id": projet_id, "version": m["version"]}

    @app.get("/projets", dependencies=[Depends(auth)])
    def lister_projets():
        return projets.lister()

    @app.get("/projets/{projet_id}/qfield", dependencies=[Depends(auth)])
    def manifeste_projet(projet_id: str):
        m = projets.manifeste(projet_id)
        if m is None:
            raise HTTPException(404, "Aucun projet publié sous cet identifiant.")
        return m

    @app.get("/projets/{projet_id}/qfield/{version}/{nom}", dependencies=[Depends(auth)])
    def telecharger_projet(projet_id: str, version: str, nom: str):
        p = projets.fichier(projet_id, version, nom)
        return FileResponse(p, media_type="application/zip" if nom.endswith(".qgz") else "text/plain", filename=nom)

    @app.get("/sante")
    def sante():
        return {"ok": True}

    return app


def _public(t: dict) -> dict:
    return {k: t[k] for k in ("id", "projet_id", "plot_id", "etat", "tentatives", "cree", "maj", "erreur", "verdict")}
