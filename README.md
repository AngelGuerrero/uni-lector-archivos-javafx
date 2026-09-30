# Lector de archivos

Lector y editor de archivos de texto hecho con **JavaFX 21**, empaquetado en
Docker y accesible desde el navegador. No hace falta instalar Java, Maven ni
JavaFX: todo se compila y se ejecuta dentro del contenedor.

> Actividad de *Programación orientada a objetos III* — Universidad Abierta y
> a Distancia de México. Versión 2.0, reescritura completa del proyecto
> original de 2019.

---

Hay dos formas de usarlo:

| | Cómo | Para qué |
|---|---|---|
| **Docker** | `docker compose up` y abrir el navegador | Funciona igual en Windows, macOS y Linux |
| **Windows nativo** | `.\build-windows.ps1` genera un `.exe` | Ventana nativa, sin Docker ni Java |

---

## Arrancar con Docker

Lo único que necesitas es Docker.

```bash
docker compose up --build
```

Cuando termine, abre **<http://localhost:6080>** en el navegador. La aplicación
aparece ahí dentro, con teclado y ratón funcionando con normalidad.

Para detenerla:

```bash
docker compose down
```

### Tus archivos

La carpeta `data/` del repositorio se monta dentro del contenedor como `/data`,
y es donde el selector de archivos abre por primera vez. Copia ahí lo que
quieras leer.

El contenedor trae también algunos ejemplos en `/app/ejemplos` (un fragmento
del Quijote, un CSV y un archivo con acentos) para probar sin preparar nada.

### Abrir un archivo al arrancar

```bash
docker compose run --rm --service-ports lector /data/mi-archivo.txt
```

---

## Compilar la versión nativa de Windows

```powershell
.\build-windows.ps1
```

Deja en `dist\` una carpeta autocontenida:

```
dist\Lector de archivos\
 ├─ Lector de archivos.exe
 ├─ runtime\                (Java y JavaFX embebidos)
 └─ app\
dist\Lector-de-archivos-2.0.0-windows-x64.zip
```

Son unos 121 MB, o 40 MB comprimidos. Se puede copiar a cualquier equipo con
Windows 10 o posterior: **no necesita Java ni instalación ni permisos de
administrador**.

### Qué descarga y dónde

El script no instala nada en el sistema: no toca el registro ni el `PATH`.
Descarga las herramientas como ZIP dentro de `build\`, que está en el
`.gitignore`:

| Qué | Tamaño | Para qué |
|---|---|---|
| JDK 21 (Temurin) | ~196 MB | Compilar y ejecutar `jpackage` |
| Apache Maven | ~9 MB | Resolver dependencias y compilar |
| jmods de JavaFX | ~40 MB | Enlazar JavaFX dentro del runtime |

Cuando termines, `Remove-Item build -Recurse -Force` recupera todo el espacio.
La segunda compilación reutiliza lo descargado.

Opciones:

```powershell
.\build-windows.ps1 -Clean      # borra todo y vuelve a descargar
.\build-windows.ps1 -SkipZip    # solo la carpeta, sin comprimir
.\build-windows.ps1 -Console    # ejecutable con consola, para diagnosticar
```

### Por qué hacen falta los jmods de JavaFX

Es el detalle que hace fallar a casi todo el mundo la primera vez. Las
bibliotecas nativas de JavaFX (`glass.dll`, `prism_d3d.dll`, `javafx_font.dll`)
viajan en la **raíz** de los `.jar` con clasificador de plataforma, y `jlink`
solo sabe extraer código nativo de ficheros `.jmod`, no de un jar modular.

Si se enlaza JavaFX desde los jar, el runtime queda con todas las clases pero
sin una sola DLL: la aplicación arranca, crea la JVM y muere al pedir la primera
ventana, **sin mensaje de error**. Por eso el script enlaza desde los jmods
oficiales de Gluon. Si no logra descargarlos, recurre a dejar los jar junto a la
aplicación y que JavaFX extraiga las DLL a `%USERPROFILE%\.openjfx\cache` en el
primer arranque: funciona igual, pero deja de ser del todo autocontenida.

---

## Qué hace

### Pantalla principal

- **Varios documentos a la vez**, cada uno en su pestaña.
- **Editar y guardar**, con `Guardar como` y recarga desde disco.
- **Numeración de líneas** sincronizada con el desplazamiento.
- **Arrastrar y soltar** archivos sobre la ventana.
- **Barra de estado** con caracteres, palabras, líneas, posición del cursor,
  tamaño y codificación.
- **Archivos recientes** en el menú.
- La carga ocurre en segundo plano con barra de progreso, así que un archivo
  grande no congela la interfaz.

### Buscar y reemplazar (`Ctrl+F`)

Hacia adelante y hacia atrás, distinguiendo mayúsculas o no, por palabra
completa o con **expresiones regulares**, con búsqueda en ciclo, contador de
coincidencias y reemplazo individual o total.

### Estadísticas (`Ctrl+T`)

Dos pestañas:

- **Resumen**: caracteres con y sin espacios, palabras, palabras distintas,
  líneas, párrafos, oraciones, línea más larga, longitud media de palabra,
  palabras por línea y por oración, y diversidad léxica.
- **Frecuencia de palabras**: tabla ordenable con filtro y gráfica de barras
  de las 15 más repetidas. Se pueden omitir las palabras vacías del español
  (`el`, `la`, `de`, `que`…) para que el conteo diga algo útil.

Todo se puede exportar a CSV.

### Preferencias (`Ctrl+,`)

Tema claro u oscuro, tipografía y tamaño del editor con vista previa en vivo,
codificación por omisión, detección automática de codificación, ajuste de
línea, numeración y cuántos archivos recientes recordar. Se guardan en
`~/.config/lector-archivos/settings.properties` dentro del contenedor, en un
volumen que sobrevive a `docker compose down`.

### Acerca de (`F1`)

Datos del programa, del autor y del entorno de ejecución.

---

## Atajos de teclado

| Atajo | Acción |
|---|---|
| `Ctrl+N` | Documento nuevo |
| `Ctrl+O` | Abrir archivo (admite selección múltiple) |
| `Ctrl+S` | Guardar |
| `Ctrl+Shift+S` | Guardar como |
| `F5` | Recargar desde disco |
| `Ctrl+W` | Cerrar pestaña |
| `Ctrl+Q` | Salir |
| `Ctrl+Z` / `Ctrl+Y` | Deshacer / Rehacer |
| `Ctrl+F` | Buscar y reemplazar |
| `Ctrl+G` | Ir a línea |
| `Ctrl+T` | Estadísticas |
| `Ctrl+,` | Preferencias |
| `Ctrl++` / `Ctrl+-` / `Ctrl+0` | Aumentar, reducir o restablecer el texto |
| `F1` | Acerca de |

---

## Cómo está armado

```
src/main/java/com/unadm/lector/
├── Launcher.java            Punto de entrada (separado de Application a propósito)
├── App.java                 Arranque de JavaFX
├── model/                   Result, TextStats, WordCount — datos inmutables
├── service/
│   ├── Settings.java        Preferencias como propiedades observables
│   ├── FileService.java     Lectura y escritura asíncronas, detección de codificación
│   └── TextAnalyzer.java    Métricas y frecuencia de palabras
├── ui/
│   ├── Windows.java         Carga de vistas y aplicación del tema
│   ├── Dialogs.java         Cuadros de diálogo
│   └── DocumentTab.java     Pestaña con editor y numeración de líneas
└── controller/              Un controlador por pantalla

src/main/resources/com/unadm/lector/
├── view/                    Los cinco FXML
├── css/                     base.css + light.css / dark.css
└── img/
```

`base.css` define toda la estructura visual usando colores con nombre; los
temas solo redefinen la paleta. Cambiar de tema no toca ninguna regla de
disposición.

### Cómo corre la interfaz gráfica en Docker

```
 Xvfb        pantalla virtual, sin hardware de vídeo
   ↓
 fluxbox     gestor de ventanas (bordes, foco, diálogos modales)
   ↓
 x11vnc      publica esa pantalla por VNC en el 5900
   ↓
 websockify  la traduce a WebSocket y sirve noVNC en el 6080
   ↓
 navegador
```

La imagen se construye en dos etapas: la primera trae Maven y el JDK para
compilar, la segunda solo lleva un JRE y la pila gráfica. Maven y el código
fuente no viajan en la imagen final.

Si prefieres un cliente VNC de escritorio en lugar del navegador, el puerto
`5900` está publicado y no pide contraseña.

---

## Cambios respecto a la versión de 2019

La versión original leía el archivo y mostraba su contenido; esta es una
reescritura. Además de las pantallas y funciones nuevas, se corrigieron estos
defectos del código anterior:

| Antes | Ahora |
|---|---|
| El `BufferedReader` nunca se cerraba | Lectura con recursos gestionados, sin fugas de descriptores |
| La lectura ocurría en el hilo de la interfaz | `Task` en segundo plano, con progreso y cancelación |
| `System.exit(0)` mataba la JVM sin avisar | Cierre ordenado, con confirmación de cambios sin guardar |
| `Response` guardaba `Object` y obligaba a hacer *cast* | `Result<T>` genérico e inmutable |
| Los diálogos usaban `show()`, no bloqueaban ni tenían ventana padre | `showAndWait()` con propietario, centrados y con el tema aplicado |
| Se asumía la codificación de la plataforma | Detección por BOM y validación UTF-8, con selector manual |
| Un archivo binario producía basura en pantalla | Se detecta y se rechaza con un mensaje claro |
| Sin límite de tamaño: un archivo enorme colgaba el programa | Límite de 16 MiB con aviso |
| Proyecto atado a IntelliJ (`.iml`, `out/`) | Maven, reproducible en cualquier equipo |

---

## Desarrollo

Para recompilar tras cambiar el código:

```bash
docker compose up --build
```

Las dependencias de Maven quedan en una caché de BuildKit, así que solo la
primera construcción descarga de la red.

Variables de entorno que acepta el contenedor:

| Variable | Por omisión | Para qué |
|---|---|---|
| `SCREEN_WIDTH` / `SCREEN_HEIGHT` | `1440` / `900` | Tamaño de la pantalla virtual |
| `JAVA_OPTS` | `-Xmx512m` | Opciones de la JVM |
| `WEB_PORT` | `6080` | Puerto de noVNC dentro del contenedor |
| `VNC_PORT` | `5900` | Puerto de VNC dentro del contenedor |

---

## Autor

Luis Ángel De Santiago Guerrero — matrícula ES1611300455
Universidad Abierta y a Distancia de México
