# Building ORKtoSCAD

## Prerequisites

- JDK 16 or newer (`javac` and `jar` on the `PATH`).

## Build

Run these commands from the project root (the folder that contains `src/` and `openscad/`).

### 1. Compile

Linux / macOS:
```
rm -rf bin && mkdir bin
javac --release 16 -d bin src/module-info.java src/orkToScad/*.java
```

Windows (PowerShell):
```
Remove-Item -Recurse -Force bin -ErrorAction SilentlyContinue; New-Item -ItemType Directory bin | Out-Null
javac --release 16 -d bin src/module-info.java (Get-ChildItem src/orkToScad/*.java)
```

### 2. Package

```
jar cfm orkToScad.jar manifest.txt -C bin . openscad
```

The trailing `openscad` argument bundles the nose cone template inside the jar. Without it,
`.scad` nose cones can only be generated if an `openscad/` folder sits next to the jar or in the
current directory.

Check the content if needed:
```
jar tf orkToScad.jar
```
It must list `orkToScad/*.class` and `openscad/noseCone.scad`.

### 3. Run

```
java -jar orkToScad.jar
```

## Running from Eclipse

The project files (`.project`, `.classpath`) are included. Run `orkToScad.Main` with the project
root as working directory (Eclipse's default): the template is then read from `openscad/`.

## Testing a conversion without the window

`Converter` has no user interface, so it can be called from a test or a small program:

```java
// third argument: also write STL files
ConversionReport report = Converter.convert(Paths.get("tests/simplerocket.ork"), Paths.get("out"), true);
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
