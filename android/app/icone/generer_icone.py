"""Generate Groma's adaptive launcher icon (same style as Marculus).

Navy hexagon outline with a blackletter capital G (UnifrakturCook Bold, OFL),
drawn in the 108 dp adaptive-icon canvas, white background. Writes the
foreground PNG for each density into ../src/main/res/mipmap-*/.

    python android/app/icone/generer_icone.py
"""

from __future__ import annotations

import logging
import math
import urllib.request
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

log = logging.getLogger("generer_icone")

ICI = Path(__file__).resolve().parent
RES = ICI.parent / "src/main/res"
POLICE = ICI / "UnifrakturCook-Bold.ttf"
URL_POLICE = "https://raw.githubusercontent.com/google/fonts/main/ofl/unifrakturcook/UnifrakturCook-Bold.ttf"

ENCRE = (26, 26, 46, 255)  # #1A1A2E, Marculus' ink
DENSITES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}

# Geometry measured on Marculus' 432 px foreground, as fractions of the canvas.
CENTRE = 216 / 432
RAYON_EXT = 129.5 / 432  # flat-top hexagon, centre to outer vertex
EPAISSEUR = 15.5 / 432
HAUTEUR_M = 129 / 432    # cap height of Marculus' M; G uses the same body size


def dessiner(taille: int, lettre: str = "G", sur: int = 4) -> Image.Image:
    """Render the foreground at `taille` px (supersampled by `sur`)."""
    t = taille * sur
    im = Image.new("RGBA", (t, t), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = CENTRE * t
    ep = EPAISSEUR * t
    r = RAYON_EXT * t - ep / 2
    pts = [(c + r * math.cos(math.radians(a)), c + r * math.sin(math.radians(a))) for a in range(0, 360, 60)]
    d.line(pts + pts[:2], fill=ENCRE, width=round(ep), joint="curve")

    ref = ImageFont.truetype(str(POLICE), 1000)
    _, haut, _, bas = ref.getbbox("M")
    police = ImageFont.truetype(str(POLICE), round(HAUTEUR_M * t / (bas - haut) * 1000))
    g, h, dr, b = police.getbbox(lettre)
    d.text((c - (g + dr) / 2, c - (h + b) / 2), lettre, font=police, fill=ENCRE)
    return im.resize((taille, taille), Image.LANCZOS)


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(message)s")
    if not POLICE.exists():
        log.info("Téléchargement de la police %s", URL_POLICE)
        urllib.request.urlretrieve(URL_POLICE, POLICE)
    for densite, taille in DENSITES.items():
        sortie = RES / f"mipmap-{densite}/ic_launcher_foreground.png"
        sortie.parent.mkdir(parents=True, exist_ok=True)
        dessiner(taille).save(sortie, optimize=True)
        log.info("%s (%d px)", sortie.relative_to(ICI.parent), taille)


if __name__ == "__main__":
    main()
