# Building ORKtoSCAD

## Prerequisites

- JDK 16 or newer (`javac` and `jar` on the `PATH`).

## Build

Run these commands from the project root (the folder that contains `src/` and `openscad/`).

### 1. Compile

Linux / macOS:
```
rm -rf bin && mkdir bin
javac --release 16 -encoding UTF-8 -d bin src/module-info.java src/orkToScad/*.java
cp -r src/orkToScad/i18n src/orkToScad/res bin/orkToScad/
```

Windows (PowerShell):
```
Remove-Item -Recurse -Force bin -ErrorAction SilentlyContinue; New-Item -ItemType Directory bin | Out-Null
javac --release 16 -encoding UTF-8 -d bin src/module-info.java (Get-ChildItem src/orkToScad/*.java)
Copy-Item src/orkToScad/i18n, src/orkToScad/res bin/orkToScad/ -Recurse
```

Windows (cmd):
```
rmdir /s /q bin
mkdir bin
javac --release 16 -encoding UTF-8 -d bin src\module-info.java src\orkToScad\*.java
xcopy /s /i /y src\orkToScad\i18n bin\orkToScad\i18n
xcopy /s /i /y src\orkToScad\res bin\orkToScad\res
```

The copy lines add the translations (`i18n/`) and the icon of the About box (`res/`): `javac` only compiles
`.java` files. Without them the window shows text keys such as `toolbar.convert` instead of the texts.
Eclipse copies them by itself.

### 2. Package

```
jar cfm orkToScad.jar manifest.txt -C bin . openscad manual
```

The trailing `openscad` argument bundles the nose cone and transition templates inside the jar, and `manual` the
PDF user manuals opened by *Help > User manual*. Without them, nose cones, transitions and the manual are only
found if the `openscad/` and `manual/` folders sit next to the jar or in the current directory.

Check the content if needed:
```
jar tf orkToScad.jar
```
It must list `orkToScad/*.class`, `orkToScad/i18n/messages_*.properties` (11 files), `orkToScad/res/icon.png`,
`openscad/noseCone.scad`, `openscad/transition.scad` and `manual/ORKtoSCAD-manual-*.pdf` (11 files).

### 3. Run

```
java -jar orkToScad.jar
```

## Windows executable (`build-exe.ps1`)

`build-exe.ps1`, at the project root, does all of the above and then creates `ORKtoSCAD.exe` with `jpackage`.
It needs a full JDK 17 or newer (a JRE or the Java embedded in Eclipse has no `jpackage`), found through
`JAVA_HOME` or the `PATH`.

```
powershell -ExecutionPolicy Bypass -File .\build-exe.ps1
```

Result:
- `dist\ORKtoSCAD\ORKtoSCAD.exe`: the application. The `dist\ORKtoSCAD` folder contains its own Java runtime,
  so copy the **whole folder**; Java does not need to be installed on the target PC.
- `dist\ORKtoSCAD-1.0.0-windows.zip`: the same folder, zipped, ready to share.

Options:
- `-Version 1.2.0`: version number written into the executable (default: `VERSION` in `Main.java`, the number
  shown in *Help > About*).
- `-Installer`: also creates a setup installer (`dist\ORKtoSCAD-1.0.0.exe`) with Start menu and desktop
  shortcuts. It needs the [WiX Toolset 3.14](https://github.com/wixtoolset/wix3/releases) on the `PATH`.
- `-JarOnly`: stops after `orkToScad.jar`.

The runtime includes `jdk.localedata`, limited to the 11 languages: without it, the `.exe` would show numbers
in the English format (`1,600.25`) in every language.

## User manual (PDF)

*Help > User manual* (<kbd>F1</kbd>) opens `manual/ORKtoSCAD-manual-<language>.pdf` in the current language
(English if missing). The PDFs are built from `docs/manual/src/manual_<language>.html` and
`docs/manual/manual.css`; the screenshots (`docs/manual/img/window-<language>.png`) are taken from the real window
with numbered markers by `docs/manual/tools/ManualScreenshots.java`.

To rebuild them after changing a text or the window (needs Python with Pillow, Google Chrome or Chromium, and for
new screenshots an X display; on Linux `Xvfb :98 -screen 0 2800x1700x24 &` then `export DISPLAY=:98`):
```
python3 docs/manual/build_manual.py --shoot      # new screenshots + all PDFs
python3 docs/manual/build_manual.py fr de         # only some PDFs, existing screenshots
```
The version, author and e-mail printed in the manuals are read from `Main.java`.

## Translations

All texts of the window and of the conversion report are in `src/orkToScad/i18n/messages_<language>.properties`
(UTF-8). English (`messages_en.properties`) is the reference: a text missing in another language is shown in
English. Placeholders `{0}`, `{1}`... are replaced as is (apostrophes need no escaping). Counted words have
plural forms: `key.one`, `key.many`, plus `key.few` for Polish and Russian (2-4). A value that must start with a
space starts with `\ ` (backslash, space).

To add a language: copy `messages_en.properties` to `messages_<code>.properties`, translate it, and add the code
to `I18n.LANGUAGES` (and its plural rule to `I18n.pluralForm` if it is not "1 / other").
For its user manual, add `docs/manual/src/manual_<code>.html` and its footer in `FOOTER` of
`docs/manual/build_manual.py` (the English manual is opened as long as there is none).

The Java sources contain only ASCII characters, so the project builds the same whatever the editor's encoding.

## Running from Eclipse

The project files (`.project`, `.classpath`) are included. Run `orkToScad.Main` with the project
root as working directory (Eclipse's default): the templates are then read from `openscad/`.

## Testing a conversion without the window

`Converter` has no user interface, so it can be called from a test or a small program:

```java
// third argument: also write STL files, fourth: also write SVG files
ConversionReport report = Converter.convert(Paths.get("tests/simplerocket.ork"), Paths.get("out"), true, true);
System.out.println(report.summary());
```

The `tests/` folder contains 170 sample designs in the three .ork formats (ZIP, GZIP, XML).
To check the generated files, open them with OpenSCAD, or in batch:

```
openscad -o /tmp/check.csg out/body-tube-3d1.scad
```

To compare a Java STL with OpenSCAD's own render of the same part:

```
openscad -o /tmp/nose-openscad.stl out/noseCone-Ogive3D.scad
```
Both files should have the same volume and size (checked on all shapes during development).
