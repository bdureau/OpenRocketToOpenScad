<#
.SYNOPSIS
    Builds ORKtoSCAD: compiles the sources, packages orkToScad.jar and creates
    a Windows executable with jpackage.

.DESCRIPTION
    Steps:
      1. finds a JDK (JAVA_HOME, then PATH) with javac, jar and jpackage;
      2. compiles src\ into build\classes (UTF-8; bin\ is left to Eclipse) and copies the
         resources (translations, icon);
      3. creates orkToScad.jar (classes, translations, icon, OpenSCAD templates, PDF manuals);
      4. runs jpackage to create dist\ORKtoSCAD\ORKtoSCAD.exe
         (or a setup installer with -Installer).

    The resulting folder dist\ORKtoSCAD contains its own Java runtime:
    copy the whole folder (or zip it); Java does not need to be installed
    on the target PC.

.PARAMETER Installer
    Also creates a setup .exe installer (needs the WiX Toolset:
    WiX 3 for JDK 16-23, WiX 3 or 4/5 for JDK 24+).

.PARAMETER Version
    Application version written into the executable (numbers only, e.g. 1.2.0).
    Default: the VERSION constant of src\orkToScad\Main.java (also shown in Help > About).

.PARAMETER JarOnly
    Stops after building orkToScad.jar.

.EXAMPLE
    .\build-exe.ps1
.EXAMPLE
    .\build-exe.ps1 -Installer -Version 1.1.0

.NOTES
    If PowerShell refuses to run the script ("running scripts is disabled"),
    start it with:
        powershell -ExecutionPolicy Bypass -File .\build-exe.ps1
#>
[CmdletBinding()]
param(
    [switch]$Installer,
    [ValidatePattern('^\d+(\.\d+){0,2}$')]
    [string]$Version,
    [switch]$JarOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$AppName   = 'ORKtoSCAD'
$MainClass = 'orkToScad.Main'
$JarName   = 'orkToScad.jar'
$Languages = 'en,fr,de,es,it,pt,nl,pl,ru,tr,hu'

$Root    = $PSScriptRoot
$Src     = Join-Path $Root 'src'
# The script compiles into build\classes, never into bin\ (Eclipse's output folder: emptying it would
# leave Eclipse with classes but without the translations and the icon it copied there).
$Build   = Join-Path $Root 'build'
$Classes = Join-Path $Build 'classes'
$Dist    = Join-Path $Root 'dist'
$JarPath = Join-Path $Root $JarName
$Icon    = [IO.Path]::Combine($Root, 'assets', 'orscad600x600-trans.ico')

function Write-Step([string]$text) {
    Write-Host ''
    Write-Host "==> $text" -ForegroundColor Cyan
}

# Runs a native command and stops the script if it fails.
function Invoke-Tool([string]$exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$(Split-Path $exe -Leaf) failed (exit code $LASTEXITCODE)."
    }
}

# Finds a JDK tool: JAVA_HOME\bin first, then the PATH.
function Find-JdkTool([string]$name) {
    if ($env:JAVA_HOME) {
        $candidate = [IO.Path]::Combine($env:JAVA_HOME, 'bin', "$name.exe")
        if (Test-Path $candidate) { return $candidate }
    }
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

Push-Location $Root
try {
    # Version: -Version, otherwise Main.VERSION (shown in Help > About)
    if (-not $Version) {
        $m = Select-String -Path ([IO.Path]::Combine($Src, 'orkToScad', 'Main.java')) -Pattern 'VERSION\s*=\s*"([0-9.]+)"'
        $Version = if ($m) { $m.Matches[0].Groups[1].Value } else { '1.0.0' }
    }
    Write-Host "Version  : $Version"
    # ------------------------------------------------------------------ JDK
    Write-Step 'Looking for the JDK'
    $javac    = Find-JdkTool 'C:\Program Files\Java\jdk-23\bin\javac'
    $jar      = Find-JdkTool 'C:\Program Files\Java\jdk-23\bin\jar'
    $jpackage = Find-JdkTool 'C:\Program Files\Java\jdk-23\bin\jpackage'

    $missing = @()
    if (-not $javac) { $missing += 'javac' }
    if (-not $jar)   { $missing += 'jar' }
    if (-not $JarOnly -and -not $jpackage) { $missing += 'jpackage' }
    if ($missing) {
        Write-Host "Not found: $($missing -join ', ')" -ForegroundColor Red
        Write-Host 'Install a JDK 17 or newer (e.g. Eclipse Temurin: https://adoptium.net),'
        Write-Host 'then set JAVA_HOME to its folder, for example:'
        Write-Host '    $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot"'
        Write-Host 'A JRE, or the Java embedded in Eclipse, does not contain jpackage.'
        exit 1
    }
    Write-Host "javac    : $javac"
    Write-Host "jar      : $jar"
    if ($jpackage) { Write-Host "jpackage : $jpackage" }

    # ------------------------------------------------------------- compile
    Write-Step 'Compiling'
    if (Test-Path $Build) { Remove-Item $Build -Recurse -Force }
    New-Item -ItemType Directory -Path $Classes -Force | Out-Null

    $sources = @(Join-Path $Src 'module-info.java') +
               @(Get-ChildItem (Join-Path $Src 'orkToScad') -Filter '*.java' |
                 ForEach-Object { $_.FullName })
    Invoke-Tool $javac (@('--release', '16', '-encoding', 'UTF-8', '-d', $Classes) + $sources)
    Write-Host "$($sources.Count) source files compiled."

    # Resources next to the classes: every file of src\ that is not a .java file
    # (translations orkToScad\i18n\*.properties, icon orkToScad\res\icon.png...)
    $srcFull = (Resolve-Path $Src).Path.TrimEnd('\', '/')
    $resources = @(Get-ChildItem $Src -Recurse -File | Where-Object { $_.Extension -ne '.java' })
    foreach ($f in $resources) {
        $target = Join-Path $Classes $f.FullName.Substring($srcFull.Length + 1)
        New-Item -ItemType Directory -Path (Split-Path $target) -Force | Out-Null
        Copy-Item $f.FullName $target -Force
    }
    Write-Host "$($resources.Count) resource files copied (translations, icon)."

    # ----------------------------------------------------------------- jar
    Write-Step "Creating $JarName"
    if (Test-Path $JarPath) { Remove-Item $JarPath -Force }
    $jarArgs = @('cfm', $JarPath, (Join-Path $Root 'manifest.txt'), '-C', $Classes, '.', '-C', $Root, 'openscad')
    if (Test-Path (Join-Path $Root 'manual')) { $jarArgs += @('-C', $Root, 'manual') }
    Invoke-Tool $jar $jarArgs

    # Check the jar content
    $entries   = & $jar tf $JarPath
    $templates = @($entries | Where-Object { $_ -like 'openscad/*.scad' }).Count
    $bundles   = @($entries | Where-Object { $_ -like 'orkToScad/i18n/*.properties' }).Count
    $manuals   = @($entries | Where-Object { $_ -like 'manual/*.pdf' }).Count
    $iconInJar = @($entries | Where-Object { $_ -eq 'orkToScad/res/icon.png' }).Count
    if ($templates -lt 2 -or $bundles -lt 11 -or $iconInJar -lt 1) {
        throw "The jar is incomplete ($templates templates, $bundles translation files, $iconInJar icon). Is src\ complete?"
    }
    if ($manuals -lt 11) {
        Write-Host "Warning: $manuals PDF manuals in the jar (11 expected): copy the manual\ folder into the project." -ForegroundColor Yellow
    }
    Write-Host "$JarName OK ($templates templates, $bundles translation files, icon, $manuals manuals)."

    if ($JarOnly) {
        Write-Host ''
        Write-Host "Done: $JarPath" -ForegroundColor Green
        exit 0
    }

    # ------------------------------------------------------------ jpackage
    # jpackage copies everything in --input into the application:
    # give it a folder that contains only the jar.
    Write-Step 'Packaging with jpackage'
    $inputDir = Join-Path $Build 'input'
    New-Item -ItemType Directory -Path $inputDir -Force | Out-Null
    Copy-Item $JarPath $inputDir

    $appDir = Join-Path $Dist $AppName
    if (Test-Path $appDir) {
        try { Remove-Item $appDir -Recurse -Force }
        catch { throw "Cannot delete $appDir. Is $AppName.exe still running?" }
    }
    New-Item -ItemType Directory -Path $Dist -Force | Out-Null

    # jdk.localedata + --include-locales: decimal separators and file-chooser
    # texts in the 11 languages, without bundling every locale of the world.
    $common = @(
        '--name', $AppName,
        '--app-version', $Version,
        '--vendor', 'Boris du Reau',
        '--description', 'OpenRocket to OpenSCAD / STL / SVG converter',
        '--input', $inputDir,
        '--main-jar', $JarName,
        '--main-class', $MainClass,
        '--add-modules', 'java.base,java.desktop,java.xml,jdk.localedata',
        '--jlink-options', "--strip-debug --no-header-files --no-man-pages --strip-native-commands --include-locales=$Languages"
    )
    if (Test-Path $Icon) { $common += @('--icon', $Icon) }
    else { Write-Host "Icon not found ($Icon): default icon used." -ForegroundColor Yellow }

    Invoke-Tool $jpackage (@('--type', 'app-image', '--dest', $Dist) + $common)
    $exe = Join-Path $appDir "$AppName.exe"
    if (-not (Test-Path $exe)) { throw "jpackage finished but $exe is missing." }
    $sizeMb = [math]::Round(((Get-ChildItem $appDir -Recurse -File |
                Measure-Object Length -Sum).Sum) / 1MB, 1)
    Write-Host "Application: $exe ($sizeMb MB with its Java runtime)"

    # Zip, ready to share
    $zip = Join-Path $Dist "$AppName-$Version-windows.zip"
    if (Test-Path $zip) { Remove-Item $zip -Force }
    Compress-Archive -Path $appDir -DestinationPath $zip
    Write-Host "Zip        : $zip"

    # ------------------------------------------------------ setup installer
    if ($Installer) {
        Write-Step 'Creating the setup installer'
        $wix = (Get-Command 'candle.exe' -ErrorAction SilentlyContinue) -or
               (Get-Command 'wix.exe'    -ErrorAction SilentlyContinue)
        if (-not $wix) {
            Write-Host 'WiX Toolset not found: installer skipped.' -ForegroundColor Yellow
            Write-Host 'Install WiX 3.14 (https://github.com/wixtoolset/wix3/releases),'
            Write-Host 'add its bin folder to the PATH, then run again with -Installer.'
        } else {
            Invoke-Tool $jpackage (@('--type', 'exe', '--dest', $Dist) + $common + @(
                '--win-menu', '--win-shortcut', '--win-dir-chooser',
                '--win-menu-group', $AppName,
                '--win-upgrade-uuid', '6f1c2a4e-8d3b-4f7a-9c21-0b5e7d4a9f13'
            ))
            Get-ChildItem $Dist -Filter "$AppName-*.exe" |
                ForEach-Object { Write-Host "Installer  : $($_.FullName)" }
        }
    }

    Remove-Item $Build -Recurse -Force
    Write-Host ''
    Write-Host 'Done.' -ForegroundColor Green
    Write-Host "Start it with: $exe"
}
finally {
    Pop-Location
}
