<#
.SYNOPSIS
    Construye el Lector de archivos como aplicacion nativa de Windows.

.DESCRIPTION
    Produce una carpeta autocontenida en dist\ con "Lector de archivos.exe" y
    su propio runtime de Java embebido. Quien la use no necesita instalar nada.

    El JDK y Maven que hacen falta para compilar se descargan como ZIP dentro
    de build\ y se usan solo desde ahi: no se instala nada en el sistema, no se
    toca el registro ni la variable PATH. Para deshacerlo todo basta con
    borrar la carpeta build\.

.PARAMETER Clean
    Borra build\ y dist\ antes de empezar, y vuelve a descargar las herramientas.

.PARAMETER SkipZip
    No genera el ZIP distribuible, solo la carpeta.

.PARAMETER Console
    Construye el ejecutable con consola adjunta, de modo que los mensajes de
    error de Java se vean. Solo para diagnosticar: no distribuyas asi.

.EXAMPLE
    .\build-windows.ps1

.EXAMPLE
    .\build-windows.ps1 -Clean
#>
[CmdletBinding()]
param(
    [switch]$Clean,
    [switch]$SkipZip,
    [switch]$Console
)

$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

# TLS 1.2 explicito: Windows PowerShell 5.1 aun negocia TLS 1.0 por omision y
# los servidores de descarga ya no lo aceptan.
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

# --- Configuracion --------------------------------------------------------

$AppName     = 'Lector de archivos'
$AppVersion  = '2.0.0'
$MainClass   = 'com.unadm.lector.Launcher'
$Vendor      = 'Luis Angel De Santiago Guerrero'
$Description = 'Lector y editor de archivos de texto'

$MavenVersion = '3.9.9'
$MavenUrl     = "https://archive.apache.org/dist/maven/maven-3/$MavenVersion/binaries/apache-maven-$MavenVersion-bin.zip"
$JdkUrl       = 'https://api.adoptium.net/v3/binary/latest/21/ga/windows/x64/jdk/hotspot/normal/eclipse?project=jdk'

# Los jmods de JavaFX. Deben coincidir con javafx.version del pom.xml.
$JavaFxVersion = '21.0.5'
$JmodsUrl      = "https://download2.gluonhq.com/openjfx/$JavaFxVersion/openjfx-${JavaFxVersion}_windows-x64_bin-jmods.zip"

# Modulos que se incluyen en el runtime recortado. jlink resuelve por su cuenta
# las dependencias transitivas; aqui solo van las raices que no puede deducir:
#   jdk.charsets    windows-1252 y demas codificaciones fuera de java.base
#   jdk.localedata  formato de numeros en espanol
#   jdk.unsupported sun.misc.Unsafe, que usa JavaFX internamente
$Modules = 'javafx.controls,javafx.fxml,java.logging,jdk.charsets,jdk.localedata,jdk.unsupported'

$BuildDir = Join-Path $PSScriptRoot 'build'
$DistDir  = Join-Path $PSScriptRoot 'dist'

# --- Utilidades -----------------------------------------------------------

function Write-Paso    { param([string]$Texto) Write-Host "`n==> $Texto" -ForegroundColor Cyan }
function Write-Detalle { param([string]$Texto) Write-Host "    $Texto" -ForegroundColor DarkGray }
function Write-Bien    { param([string]$Texto) Write-Host "    $Texto" -ForegroundColor Green }

function Get-Archivo {
    param([string]$Url, [string]$Destino, [string]$Descripcion)

    if (Test-Path $Destino) {
        Write-Detalle "$Descripcion ya descargado."
        return
    }

    Write-Detalle "Descargando $Descripcion..."
    Write-Detalle $Url

    $temporal = "$Destino.parcial"
    try {
        # La barra de progreso de Invoke-WebRequest ralentiza muchisimo las
        # descargas grandes en PowerShell 5.1; se desactiva mientras dura.
        $progresoPrevio = $ProgressPreference
        $ProgressPreference = 'SilentlyContinue'
        Invoke-WebRequest -Uri $Url -OutFile $temporal -UseBasicParsing
        $ProgressPreference = $progresoPrevio

        Move-Item -Path $temporal -Destination $Destino -Force
    } catch {
        if (Test-Path $temporal) { Remove-Item $temporal -Force }
        throw "No se pudo descargar $Descripcion : $($_.Exception.Message)"
    }

    $mb = [math]::Round((Get-Item $Destino).Length / 1MB, 1)
    Write-Bien "$Descripcion descargado ($mb MB)."
}

function Expand-Una {
    param([string]$Zip, [string]$Destino, [string]$Descripcion)

    if (Test-Path $Destino) {
        Write-Detalle "$Descripcion ya extraido."
        return
    }
    Write-Detalle "Extrayendo $Descripcion..."
    Expand-Archive -Path $Zip -DestinationPath $Destino -Force
}

<#
    Convierte el logotipo PNG en un .ico cuadrado.

    El formato ICO admite desde Windows Vista incrustar un PNG tal cual, asi que
    basta con centrar la imagen en un lienzo cuadrado transparente y anteponerle
    la cabecera de 22 bytes. Evita depender de ImageMagick o similares.
#>
function New-IconoDesdePng {
    param([string]$Png, [string]$Ico)

    Add-Type -AssemblyName System.Drawing

    $origen = [System.Drawing.Image]::FromFile($Png)
    try {
        $lado = 256
        $lienzo = New-Object System.Drawing.Bitmap($lado, $lado)
        $g = [System.Drawing.Graphics]::FromImage($lienzo)
        try {
            $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $g.Clear([System.Drawing.Color]::Transparent)

            # Se escala conservando la proporcion y se centra, con un margen
            # del 6% para que el icono no toque los bordes.
            $util  = $lado * 0.88
            $razon = [math]::Min($util / $origen.Width, $util / $origen.Height)
            $ancho = [int]($origen.Width * $razon)
            $alto  = [int]($origen.Height * $razon)
            $g.DrawImage($origen, [int](($lado - $ancho) / 2), [int](($lado - $alto) / 2), $ancho, $alto)
        } finally {
            $g.Dispose()
        }

        $memoria = New-Object System.IO.MemoryStream
        $lienzo.Save($memoria, [System.Drawing.Imaging.ImageFormat]::Png)
        $bytesPng = $memoria.ToArray()
        $memoria.Dispose()
        $lienzo.Dispose()

        $salida = [System.IO.File]::Create($Ico)
        try {
            $w = New-Object System.IO.BinaryWriter($salida)
            # ICONDIR
            $w.Write([uint16]0)   # reservado
            $w.Write([uint16]1)   # tipo: 1 = icono
            $w.Write([uint16]1)   # numero de imagenes
            # ICONDIRENTRY
            $w.Write([byte]0)     # ancho  (0 significa 256)
            $w.Write([byte]0)     # alto   (0 significa 256)
            $w.Write([byte]0)     # colores de la paleta
            $w.Write([byte]0)     # reservado
            $w.Write([uint16]1)   # planos
            $w.Write([uint16]32)  # bits por pixel
            $w.Write([uint32]$bytesPng.Length)
            $w.Write([uint32]22)  # desplazamiento: 6 + 16
            $w.Write($bytesPng)
            $w.Flush()
        } finally {
            $salida.Dispose()
        }
    } finally {
        $origen.Dispose()
    }
}

# --- Comprobaciones previas ----------------------------------------------

Write-Host ''
Write-Host "  Lector de archivos $AppVersion - empaquetado nativo para Windows" -ForegroundColor White
Write-Host '  ---------------------------------------------------------------' -ForegroundColor DarkGray

if ($Clean) {
    Write-Paso 'Limpiando'
    foreach ($carpeta in @($BuildDir, $DistDir, (Join-Path $PSScriptRoot 'target'))) {
        if (Test-Path $carpeta) {
            Remove-Item $carpeta -Recurse -Force
            Write-Detalle "Borrado: $carpeta"
        }
    }
}

New-Item -ItemType Directory -Force -Path $BuildDir | Out-Null

# --- 1. JDK ---------------------------------------------------------------

Write-Paso 'Preparando el JDK (solo para compilar)'

$JdkZip  = Join-Path $BuildDir 'temurin-jdk21.zip'
$JdkRoot = Join-Path $BuildDir 'jdk'

Get-Archivo -Url $JdkUrl -Destino $JdkZip -Descripcion 'JDK 21 (Temurin)'
Expand-Una  -Zip $JdkZip -Destino $JdkRoot -Descripcion 'JDK'

# El ZIP trae una carpeta raiz con el numero de version; se localiza por jpackage.
$JpackageExe = Get-ChildItem -Path $JdkRoot -Filter 'jpackage.exe' -Recurse -ErrorAction SilentlyContinue |
               Select-Object -First 1
if (-not $JpackageExe) {
    throw "No se encontro jpackage.exe dentro de $JdkRoot. Borra build\ y vuelve a intentarlo con -Clean."
}

$JavaHome = Split-Path (Split-Path $JpackageExe.FullName -Parent) -Parent
Write-Bien "JDK listo: $JavaHome"

# --- 2. Maven -------------------------------------------------------------

Write-Paso 'Preparando Maven (solo para compilar)'

$MavenZip  = Join-Path $BuildDir "apache-maven-$MavenVersion-bin.zip"
$MavenRoot = Join-Path $BuildDir 'maven'

Get-Archivo -Url $MavenUrl -Destino $MavenZip -Descripcion "Maven $MavenVersion"
Expand-Una  -Zip $MavenZip -Destino $MavenRoot -Descripcion 'Maven'

$MvnCmd = Get-ChildItem -Path $MavenRoot -Filter 'mvn.cmd' -Recurse -ErrorAction SilentlyContinue |
          Select-Object -First 1
if (-not $MvnCmd) {
    throw "No se encontro mvn.cmd dentro de $MavenRoot."
}
Write-Bien "Maven listo: $($MvnCmd.FullName)"

# --- 3. Compilacion -------------------------------------------------------

Write-Paso 'Compilando el proyecto'

# JAVA_HOME solo para este proceso: no se escribe en el entorno del usuario.
$env:JAVA_HOME = $JavaHome

& $MvnCmd.FullName -B -ntp clean package
if ($LASTEXITCODE -ne 0) {
    throw 'Fallo la compilacion con Maven.'
}

$Jar = Join-Path $PSScriptRoot 'target\lector-archivos.jar'
if (-not (Test-Path $Jar)) {
    throw "Maven termino pero no se genero $Jar."
}
Write-Bien "Compilado: $Jar"

# --- 4. Preparar las entradas de jpackage --------------------------------

Write-Paso 'Preparando los modulos de JavaFX'

$LibDir = Join-Path $PSScriptRoot 'target\lib'

# OpenJFX publica cada artefacto por duplicado: un jar vacio sin clasificador y
# el real con el de la plataforma. Los dos declaran el mismo modulo, y jlink
# aborta si ve javafx.* definido en dos sitios. Se descartan los vacios.
$reales = Get-ChildItem -Path $LibDir -Filter '*-win.jar' -ErrorAction SilentlyContinue
foreach ($real in $reales) {
    $stub = Join-Path $LibDir ($real.Name -replace '-win\.jar$', '.jar')
    if (Test-Path $stub) {
        Remove-Item $stub -Force
        Write-Detalle "Descartado el jar vacio: $(Split-Path $stub -Leaf)"
    }
}

if (-not $reales) {
    throw "No se encontraron los jars de JavaFX para Windows en $LibDir. " +
          "Maven deberia haberlos resuelto automaticamente al compilar en Windows."
}

Write-Bien "$($reales.Count) modulos de JavaFX para Windows."

# --- 4b. jmods de JavaFX --------------------------------------------------

Write-Paso 'Preparando el codigo nativo de JavaFX'

<#
    Las bibliotecas nativas de JavaFX (glass.dll, prism_*.dll, javafx_font.dll)
    viajan en la RAIZ de los jar con clasificador de plataforma. jlink solo sabe
    extraer codigo nativo de ficheros .jmod, no de un jar modular: si se enlaza
    JavaFX desde los jar, el runtime acaba con las clases pero sin las DLL, y la
    aplicacion arranca y muere en silencio al pedir la primera ventana.

    Por eso se enlaza desde los jmods oficiales de Gluon, que si las traen.
    Si no se pueden descargar, se recurre a dejar los jar junto a la aplicacion
    y dejar que JavaFX extraiga las DLL a %USERPROFILE%\.openjfx\cache en el
    primer arranque: funciona igual, pero deja de ser del todo autocontenida.
#>
$JmodsZip  = Join-Path $BuildDir "openjfx-$JavaFxVersion-jmods.zip"
$JmodsRoot = Join-Path $BuildDir 'jmods'
$JmodsDir  = $null

try {
    Get-Archivo -Url $JmodsUrl -Destino $JmodsZip -Descripcion "jmods de JavaFX $JavaFxVersion"
    Expand-Una  -Zip $JmodsZip -Destino $JmodsRoot -Descripcion 'jmods de JavaFX'

    $unJmod = Get-ChildItem -Path $JmodsRoot -Filter 'javafx.controls.jmod' -Recurse -ErrorAction SilentlyContinue |
              Select-Object -First 1
    if ($unJmod) {
        $JmodsDir = $unJmod.DirectoryName
        Write-Bien "jmods listos: $JmodsDir"
    } else {
        Write-Warning 'El ZIP de jmods no contenia javafx.controls.jmod.'
    }
} catch {
    Write-Warning "No se pudieron obtener los jmods: $($_.Exception.Message)"
}

# --- 4c. Entradas de jpackage --------------------------------------------

$InputDir = Join-Path $BuildDir 'input'
if (Test-Path $InputDir) { Remove-Item $InputDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $InputDir | Out-Null
Copy-Item -Path $Jar -Destination $InputDir

if ($JmodsDir) {
    # Camino preferido: JavaFX se enlaza dentro del runtime, con sus DLL.
    # Se anaden tambien los jmods del propio JDK, porque al indicar un
    # --module-path propio jlink deja de mirar los del JDK por su cuenta.
    $ModulePath = "$JmodsDir;" + (Join-Path $JavaHome 'jmods')
    $Autocontenida = $true
} else {
    # Repliegue: los jar de JavaFX acompanan a la aplicacion en el classpath.
    # Launcher no extiende Application justamente para que esto funcione.
    Copy-Item -Path (Join-Path $LibDir '*.jar') -Destination $InputDir
    $ModulePath = $null
    $Autocontenida = $false
    Write-Warning 'Se empaquetara con JavaFX en el classpath (modo de repliegue).'
}

# --- 5. Icono -------------------------------------------------------------

Write-Paso 'Generando el icono'

$Png = Join-Path $PSScriptRoot 'src\main\resources\com\unadm\lector\img\logounadm.png'
$Ico = Join-Path $BuildDir 'lector.ico'
$IconoArgs = @()

try {
    if (-not (Test-Path $Ico)) {
        New-IconoDesdePng -Png $Png -Ico $Ico
    }
    $IconoArgs = @('--icon', $Ico)
    Write-Bien "Icono generado: $Ico"
} catch {
    # Un icono es un adorno: si falla, se sigue con el de Java por omision.
    Write-Warning "No se pudo generar el icono, se usara el de Java. ($($_.Exception.Message))"
}

# --- 6. jpackage ----------------------------------------------------------

Write-Paso 'Empaquetando la aplicacion nativa'

if (Test-Path $DistDir) { Remove-Item $DistDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $DistDir | Out-Null

$argumentos = @(
    '--type', 'app-image'
    '--name', $AppName
    '--app-version', $AppVersion
    '--vendor', $Vendor
    '--description', $Description
    '--input', $InputDir
    '--main-jar', (Split-Path $Jar -Leaf)
    '--main-class', $MainClass
    '--dest', $DistDir
    '--java-options', '-Xmx512m'
    '--java-options', '-Dfile.encoding=UTF-8'
) + $IconoArgs

if ($ModulePath) {
    # --add-modules aqui le dice a jlink que meter en el runtime. No hace falta
    # repetirlo como opcion de la JVM: un modulo del runtime que exporta
    # paquetes sin restricciones ya entra en el conjunto raiz por omision, y
    # jdk.charsets y jdk.localedata se resuelven por enlace de servicios.
    $argumentos += @('--module-path', $ModulePath, '--add-modules', $Modules)
} else {
    # Sin JavaFX en el runtime, pero siguen haciendo falta las codificaciones y
    # los datos de idioma; java.se cubre el resto de la biblioteca estandar.
    $argumentos += @('--add-modules', 'java.se,jdk.charsets,jdk.localedata,jdk.unsupported')
}

if ($Console) {
    $argumentos += '--win-console'
    Write-Warning 'Compilando con consola adjunta (-Console). No distribuyas este ejecutable.'
}

Write-Detalle "jpackage $($argumentos -join ' ')"
& $JpackageExe.FullName @argumentos
if ($LASTEXITCODE -ne 0) {
    throw 'jpackage fallo al construir la imagen de la aplicacion.'
}

$AppDir = Join-Path $DistDir $AppName
$Exe    = Join-Path $AppDir "$AppName.exe"
if (-not (Test-Path $Exe)) {
    throw "jpackage termino sin errores pero no se encuentra $Exe."
}
Write-Bien "Aplicacion creada: $Exe"

# --- 7. ZIP distribuible --------------------------------------------------

if (-not $SkipZip) {
    Write-Paso 'Comprimiendo para distribuir'

    $Zip = Join-Path $DistDir "Lector-de-archivos-$AppVersion-windows-x64.zip"
    if (Test-Path $Zip) { Remove-Item $Zip -Force }

    $progresoPrevio = $ProgressPreference
    $ProgressPreference = 'SilentlyContinue'
    Compress-Archive -Path $AppDir -DestinationPath $Zip
    $ProgressPreference = $progresoPrevio

    $mb = [math]::Round((Get-Item $Zip).Length / 1MB, 1)
    Write-Bien "ZIP creado: $Zip ($mb MB)"
}

# --- Resumen --------------------------------------------------------------

$tamano = [math]::Round(((Get-ChildItem $AppDir -Recurse -File |
                          Measure-Object -Property Length -Sum).Sum / 1MB), 1)

Write-Host ''
Write-Host '  ===============================================================' -ForegroundColor Green
Write-Host '   Listo.' -ForegroundColor Green
Write-Host ''
Write-Host "   Ejecutable : $Exe"
Write-Host "   Tamano     : $tamano MB (runtime de Java incluido)"
Write-Host ''
Write-Host '   Para probarlo ahora:'
Write-Host "       & `"$Exe`"" -ForegroundColor White
Write-Host ''
Write-Host '   Se puede copiar la carpeta completa a cualquier equipo con'
Write-Host '   Windows 10 o posterior. No necesita Java ni instalacion.'
Write-Host ''
Write-Host '   Para recuperar el espacio de las herramientas de compilacion:'
Write-Host "       Remove-Item '$BuildDir' -Recurse -Force" -ForegroundColor White
Write-Host '  ===============================================================' -ForegroundColor Green
Write-Host ''
