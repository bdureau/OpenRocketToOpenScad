#!/usr/bin/env python3
"""Builds the PDF user manuals: manual/ORKtoSCAD-manual-<lang>.pdf.

Sources: docs/manual/src/manual_<lang>.html (body of the manual), docs/manual/manual.css.
Placeholders in the sources: {{version}}, {{author}}, {{email}}, {{screenshot}}, {{icon}}.

Steps:
  1. screenshots (optional, --shoot): opens the window in each language on an X display with
     ManualScreenshots (docs/manual/tools) and saves docs/manual/img/window-<lang>.png;
  2. PDF: prints each manual with headless Chrome/Chromium.

Usage (from the project root, after compiling into bin/):
  python3 docs/manual/build_manual.py --shoot          # all languages, new screenshots (needs DISPLAY)
  python3 docs/manual/build_manual.py fr de            # some languages, existing screenshots
Requires: Pillow, Google Chrome or Chromium; for --shoot a JDK and an X display (Xvfb works) of at least
2800 x 1700 pixels.
"""
import argparse
import base64
import io
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
HERE = ROOT / "docs" / "manual"
LANGS = ["en", "fr", "de", "es", "it", "pt", "nl", "pl", "ru", "tr", "hu"]
SAMPLE = ROOT / "tests" / "PML_AMRAAM3_38mm.ork"

# Footer (left part) of each page; the page number is added on the right.
FOOTER = {
    "en": "ORKtoSCAD {v} — User manual",
    "fr": "ORKtoSCAD {v} — Manuel d’utilisation",
    "de": "ORKtoSCAD {v} — Benutzerhandbuch",
    "es": "ORKtoSCAD {v} — Manual de usuario",
    "it": "ORKtoSCAD {v} — Manuale d’uso",
    "pt": "ORKtoSCAD {v} — Manual do usuário",
    "nl": "ORKtoSCAD {v} — Gebruikershandleiding",
    "pl": "ORKtoSCAD {v} — Instrukcja obsługi",
    "ru": "ORKtoSCAD {v} — Руководство пользователя",
    "tr": "ORKtoSCAD {v} — Kullanım kılavuzu",
    "hu": "ORKtoSCAD {v} — Felhasználói kézikönyv",
}


def main_constants():
    src = (ROOT / "src" / "orkToScad" / "Main.java").read_text(encoding="utf-8")
    get = lambda name: re.search(name + r'\s*=\s*"([^"]+)"', src).group(1)
    return get("VERSION"), get("AUTHOR"), get("EMAIL")


def shoot(langs):
    tools = Path(tempfile.mkdtemp())
    subprocess.run(["javac", "-cp", str(ROOT / "bin"), "-d", str(tools),
                    str(HERE / "tools" / "ManualScreenshots.java")], check=True)
    (HERE / "img").mkdir(exist_ok=True)
    subprocess.run(["java", "-Dsun.java2d.uiScale=2", "-cp", os.pathsep.join([str(ROOT / "bin"), str(tools)]),
                    "orkToScad.ManualScreenshots", str(SAMPLE), str(HERE / "img")] + langs, check=True)
    shutil.rmtree(tools)


def font_faces(tmp):
    """@font-face rules for Inter converted to TrueType outlines.

    Chrome embeds CFF (.otf) fonts as Type 3 fonts, which some PDF readers show blurred; TrueType outlines are
    embedded as real fonts. Falls back to the installed fonts if Inter or fontTools is missing."""
    try:
        from fontTools.ttLib import TTFont
        from fontTools.pens.cu2quPen import Cu2QuPen
        from fontTools.pens.ttGlyphPen import TTGlyphPen
    except ImportError:
        return ""
    found = {}
    for path in Path("/usr/share/fonts").rglob("Inter-*.otf"):
        found[path.stem] = path
    for d in (Path.home() / ".fonts", Path.home() / ".local/share/fonts", Path("C:/Windows/Fonts")):
        if d.is_dir():
            for path in d.rglob("Inter-*.otf"):
                found[path.stem] = path
    rules = []
    for style, weight, italic in (("Regular", 400, False), ("Italic", 400, True), ("SemiBold", 600, False),
                                  ("Bold", 700, False), ("BoldItalic", 700, True)):
        src = found.get("Inter-" + style)
        if not src:
            continue
        out = Path(tmp) / ("Inter-%s.ttf" % style)
        font = TTFont(src)
        glyph_set = font.getGlyphSet()
        glyf = {}
        for name in font.getGlyphOrder():
            pen = TTGlyphPen(glyph_set)
            glyph_set[name].draw(Cu2QuPen(pen, 1.0, reverse_direction=True))
            glyf[name] = pen.glyph()
        from fontTools.ttLib import newTable
        font["loca"] = newTable("loca")
        font["glyf"] = newTable("glyf")
        font["glyf"].glyphOrder = font.getGlyphOrder()
        font["glyf"].glyphs = glyf
        for tag in ("CFF ", "VORG"):
            if tag in font:
                del font[tag]
        font["head"].glyphDataFormat = 0
        maxp = font["maxp"]
        maxp.tableVersion = 0x00010000
        for attr in ("maxZones", "maxTwilightPoints", "maxStorage", "maxFunctionDefs", "maxInstructionDefs",
                     "maxStackElements", "maxSizeOfInstructions", "maxComponentElements", "maxPoints",
                     "maxContours", "maxCompositePoints", "maxCompositeContours", "maxComponentDepth"):
            setattr(maxp, attr, 0)
        maxp.maxZones = 1
        font["post"].formatType = 2.0
        font["post"].extraNames = []
        font["post"].mapping = {}
        font["post"].glyphOrder = font.getGlyphOrder()
        font.sfntVersion = "\x00\x01\x00\x00"
        font.save(out)
        rules.append('@font-face { font-family: "Manual Sans"; src: url("%s"); font-weight: %d; font-style: %s; }'
                     % (out.as_uri(), weight, "italic" if italic else "normal"))
    return "\n".join(rules)


def data_uri(img, fmt, **save):
    buf = io.BytesIO()
    img.save(buf, fmt, **save)
    return "data:image/%s;base64,%s" % (fmt.lower(), base64.b64encode(buf.getvalue()).decode())


def find_chrome():
    for name in ("google-chrome", "chromium", "chromium-browser", "chrome"):
        if shutil.which(name):
            return shutil.which(name)
    sys.exit("Chrome or Chromium not found")


def build(lang, version, author, email, chrome, out_dir, faces):
    body = (HERE / "src" / ("manual_%s.html" % lang)).read_text(encoding="utf-8")
    shot = Image.open(HERE / "img" / ("window-%s.png" % lang)).convert("RGB")
    shot = shot.resize((shot.width * 3 // 4, shot.height * 3 // 4), Image.LANCZOS)  # 2x -> 1.5x
    icon = Image.open(ROOT / "src" / "orkToScad" / "res" / "icon.png")
    body = (body.replace("{{version}}", version).replace("{{author}}", author).replace("{{email}}", email)
            .replace("{{screenshot}}", data_uri(shot, "JPEG", quality=88, subsampling=0, optimize=True))
            .replace("{{icon}}", data_uri(icon, "PNG", optimize=True)))
    footer = FOOTER[lang].format(v=version).replace('"', '\\"')
    html = ('<!DOCTYPE html><html lang="%s"><head><meta charset="utf-8"><title>%s</title><style>%s\n'
            '@page { @bottom-left { content: "%s"; } }</style></head><body>%s</body></html>'
            % (lang, FOOTER[lang].format(v=version), faces + "\n" + (HERE / "manual.css").read_text(encoding="utf-8"),
               footer, body))
    with tempfile.TemporaryDirectory() as tmp:
        page = Path(tmp) / "manual.html"
        page.write_text(html, encoding="utf-8")
        pdf = out_dir / ("ORKtoSCAD-manual-%s.pdf" % lang)
        subprocess.run([chrome, "--headless", "--disable-gpu", "--no-sandbox", "--no-pdf-header-footer",
                        "--user-data-dir=" + str(Path(tmp) / "profile"),
                        "--print-to-pdf=" + str(pdf), page.as_uri()],
                       check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    print("%s  %d KB" % (pdf.relative_to(ROOT), pdf.stat().st_size // 1024))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("langs", nargs="*", default=LANGS)
    ap.add_argument("--shoot", action="store_true", help="take new screenshots first (needs DISPLAY)")
    args = ap.parse_args()
    version, author, email = main_constants()
    if args.shoot:
        shoot(args.langs)
    out_dir = ROOT / "manual"
    out_dir.mkdir(exist_ok=True)
    chrome = find_chrome()
    with tempfile.TemporaryDirectory() as fonts:
        faces = font_faces(fonts)
        for lang in args.langs:
            build(lang, version, author, email, chrome, out_dir, faces)


if __name__ == "__main__":
    main()
