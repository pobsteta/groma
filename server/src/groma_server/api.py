"""HTTP API: resumable package upload, task state, results for nemetonshiny.

Upload protocol (resumable after a network cut, the phone resends from the
offset the server reports):

1. ``POST /televersements`` {plot_id, taille, sha256} -> {id, taille_morceau, recu}
2. ``PUT /televersements/{id}?offset=N`` body = bytes from N; N must equal
   the number of bytes already received (409 otherwise, with the expected offset)
3. ``GET /televersements/{id}`` -> {recu, taille}
4. ``POST /televersements/{id}/terminer`` -> checks size and SHA-256, queues
   the task -> {tache_id}

Results: ``GET /placettes`` (state of every plot), ``GET /taches/{id}``,
``GET /placettes/{plot_id}/resultat.gpkg | rapport.json | rapport.html``.
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

PLOT_ID = re.compile(r"^[A-Za-z0-9_.-]{1,64}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")


class DemandeTeleversement(BaseModel):
    plot_id: str = Field(pattern=PLOT_ID.pattern)
    taille: int = Field(gt=0)
    sha256: str = Field(pattern=SHA256.pattern)


def creer_app(config: Config | None = None) -> FastAPI:
    cfg = config or Config()
    file = FileAttente(cfg.base)
    dossier_tel = cfg.dossier_donnees / "televersements"
    dossier_paquets = cfg.dossier_donnees / "paquets"
    for d in (dossier_tel, dossier_paquets):
        d.mkdir(parents=True, exist_ok=True)

    app = FastAPI(title="Groma", version="0.1.0")

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
        cible = dossier_paquets / f"{m['plot_id']}_{tid}.groma.zip"
        part.replace(cible)
        (dossier_tel / f"{tid}.json").unlink()
        return {"tache_id": file.ajouter(m["plot_id"], str(cible))}

    @app.get("/taches/{tid}", dependencies=[Depends(auth)])
    def tache(tid: str):
        t = file.obtenir(tid)
        if not t:
            raise HTTPException(404, "Tâche inconnue.")
        return _public(t)

    @app.get("/placettes", dependencies=[Depends(auth)])
    def placettes():
        return [_public(t) for t in file.lister()]

    def resultat(plot_id: str, champ: str, media: str):
        if not PLOT_ID.match(plot_id):
            raise HTTPException(404, "Placette inconnue.")
        t = file.derniere(plot_id, "terminee")
        if not t or not t[champ]:
            raise HTTPException(404, "Aucun résultat définitif pour cette placette.")
        return FileResponse(t[champ], media_type=media, filename=Path(t[champ]).name)

    @app.get("/placettes/{plot_id}/resultat.gpkg", dependencies=[Depends(auth)])
    def gpkg(plot_id: str):
        return resultat(plot_id, "gpkg", "application/geopackage+sqlite3")

    @app.get("/placettes/{plot_id}/rapport.json", dependencies=[Depends(auth)])
    def rapport_json(plot_id: str):
        return resultat(plot_id, "rapport_json", "application/json")

    @app.get("/placettes/{plot_id}/rapport.html", dependencies=[Depends(auth)])
    def rapport_html(plot_id: str):
        return resultat(plot_id, "rapport_html", "text/html")

    @app.get("/sante")
    def sante():
        return {"ok": True}

    return app


def _public(t: dict) -> dict:
    return {k: t[k] for k in ("id", "plot_id", "etat", "tentatives", "cree", "maj", "erreur", "verdict")}
