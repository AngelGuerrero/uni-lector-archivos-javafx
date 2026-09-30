#!/usr/bin/env bash
#
# Arranca la pila grafica del contenedor y despues la aplicacion:
#
#   Xvfb        pantalla virtual sin hardware
#   fluxbox     gestor de ventanas (bordes, foco, dialogos modales)
#   x11vnc      publica esa pantalla por VNC
#   websockify  traduce VNC a WebSocket y sirve noVNC por HTTP
#   java        el Lector de archivos
#
# La aplicacion corre en primer plano: cuando termina, termina el contenedor.

set -euo pipefail

SCREEN_WIDTH="${SCREEN_WIDTH:-1440}"
SCREEN_HEIGHT="${SCREEN_HEIGHT:-900}"
SCREEN_DEPTH="${SCREEN_DEPTH:-24}"
DISPLAY="${DISPLAY:-:99}"
VNC_PORT="${VNC_PORT:-5900}"
WEB_PORT="${WEB_PORT:-6080}"
export DISPLAY

GEOMETRIA="${SCREEN_WIDTH}x${SCREEN_HEIGHT}x${SCREEN_DEPTH}"

log() { printf '[lector] %s\n' "$*"; }

# Al salir se llevan por delante todos los procesos auxiliares.
pids=()
limpiar() {
    log "Deteniendo servicios..."
    for pid in "${pids[@]:-}"; do
        kill "$pid" 2>/dev/null || true
    done
}
trap limpiar EXIT INT TERM

# --- Servidor X virtual ---------------------------------------------------
# Al reiniciar el contenedor (docker restart) la capa de escritura conserva el
# archivo de bloqueo de la sesion anterior, y Xvfb aborta con "Server is
# already active". Como en un contenedor recien arrancado no hay ningun X
# server vivo, el bloqueo siempre es residuo y se puede borrar.
NUMERO_PANTALLA="${DISPLAY#:}"
NUMERO_PANTALLA="${NUMERO_PANTALLA%%.*}"
if [ -e "/tmp/.X${NUMERO_PANTALLA}-lock" ]; then
    log "Eliminando el bloqueo residual /tmp/.X${NUMERO_PANTALLA}-lock"
    rm -f "/tmp/.X${NUMERO_PANTALLA}-lock" "/tmp/.X11-unix/X${NUMERO_PANTALLA}"
fi

log "Iniciando Xvfb en ${DISPLAY} (${GEOMETRIA})"
Xvfb "${DISPLAY}" -screen 0 "${GEOMETRIA}" -nolisten tcp -dpi 96 +extension RANDR &
pids+=($!)

# xdpyinfo confirma que el servidor ya acepta conexiones; sin esta espera,
# la aplicacion puede arrancar antes y morir con "Can't connect to X11".
log "Esperando a que el servidor X responda..."
for _ in $(seq 1 50); do
    if xdpyinfo -display "${DISPLAY}" >/dev/null 2>&1; then
        break
    fi
    sleep 0.2
done

if ! xdpyinfo -display "${DISPLAY}" >/dev/null 2>&1; then
    log "ERROR: el servidor X no respondio a tiempo."
    exit 1
fi

# --- Fondo del escritorio -------------------------------------------------
# Se pinta aqui y no desde fluxbox: su gestor de tapices (fbsetbg) no
# encuentra ningun programa de fondos en una imagen minima y abre un dialogo
# de error encima de la aplicacion.
xsetroot -solid "#1b1e24" 2>/dev/null || true

# --- Gestor de ventanas ---------------------------------------------------
log "Iniciando fluxbox"
fluxbox >/dev/null 2>&1 &
pids+=($!)

# --- Servidor VNC ---------------------------------------------------------
log "Publicando la pantalla por VNC en el puerto ${VNC_PORT}"
x11vnc -display "${DISPLAY}" \
       -rfbport "${VNC_PORT}" \
       -forever \
       -shared \
       -nopw \
       -noxdamage \
       -quiet \
       -bg \
       -o /tmp/x11vnc.log

# --- Puente web -----------------------------------------------------------
log "Sirviendo noVNC en http://localhost:${WEB_PORT}"
websockify --web=/usr/share/novnc "${WEB_PORT}" "localhost:${VNC_PORT}" >/tmp/websockify.log 2>&1 &
pids+=($!)

# --- Aplicacion -----------------------------------------------------------
# JavaFX va en el module-path; el jar de la aplicacion en el classpath.
# Mezclarlos haria que la JVM viera los modulos javafx.* dos veces.
log "Iniciando el Lector de archivos"
echo
echo "  ============================================================"
echo "    Abre esta direccion en tu navegador:"
echo "        http://localhost:${WEB_PORT}"
echo "    Tus archivos del equipo estan en la carpeta /data"
echo "  ============================================================"
echo

exec java \
    --module-path /app/lib \
    --add-modules javafx.controls,javafx.fxml \
    -Dfile.encoding=UTF-8 \
    -Dprism.order=sw \
    -Dprism.lcdtext=false \
    -Djava.awt.headless=true \
    ${JAVA_OPTS:-} \
    -cp /app/app.jar \
    com.unadm.lector.Launcher "$@"
