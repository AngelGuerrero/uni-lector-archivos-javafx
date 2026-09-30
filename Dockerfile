# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Etapa 1: compilacion
#
# Maven y el JDK solo existen aqui. La imagen final no los lleva, por eso no
# hace falta tener Java instalado ni en el equipo ni en el contenedor final.
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

# El pom se copia solo para resolver dependencias: mientras no cambie, esta
# capa se reutiliza y la compilacion no vuelve a descargar nada.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean package

# Los artefactos de OpenJFX se publican por duplicado: un jar vacio sin
# clasificador y el real con el clasificador de la plataforma. Si los dos
# quedan en el module-path, la JVM ve el modulo javafx.* definido dos veces
# y aborta. Aqui se descartan los vacios.
RUN set -eu; \
    cd /build/target/lib; \
    for real in *-linux.jar; do \
        rm -f "${real%-linux.jar}.jar"; \
    done; \
    echo "Modulos de JavaFX que van a la imagen final:"; ls -1

# ---------------------------------------------------------------------------
# Etapa 2: ejecucion
#
# Un JRE, un servidor X virtual y un puente a noVNC. La aplicacion dibuja en
# Xvfb, x11vnc publica esa pantalla por VNC y websockify la sirve por HTTP.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-jammy AS runtime

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install --no-install-recommends -y \
        # Servidor X virtual y gestor de ventanas
        xvfb \
        fluxbox \
        x11vnc \
        # Puente VNC -> navegador
        novnc \
        websockify \
        # Bibliotecas nativas que JavaFX necesita en Linux
        libgtk-3-0 \
        libglib2.0-0 \
        libgl1 \
        libgdk-pixbuf-2.0-0 \
        libxtst6 \
        libxrender1 \
        libxi6 \
        libfreetype6 \
        fontconfig \
        # Tipografias: sin ellas el texto sale en cuadros vacios.
        # DejaVu y Liberation cubren de sobra el español y el monoespaciado.
        fonts-dejavu-core \
        fonts-liberation2 \
        # xsetroot pinta el fondo del escritorio. Sin el, fluxbox lanza
        # fbsetbg, que abre un xmessage de error encima de la aplicacion.
        x11-xserver-utils \
        # xdpyinfo: el entrypoint lo usa para saber cuando el servidor X
        # ya acepta conexiones antes de arrancar la aplicacion.
        x11-utils \
        ca-certificates \
        tini \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/* /var/cache/apt/archives/*.deb \
    && fc-cache -f

# noVNC se sirve desde su propia carpeta; index.html redirige al visor ya
# configurado para conectarse solo y escalar a la ventana del navegador.
RUN printf '%s\n' \
    '<!doctype html>' \
    '<meta charset="utf-8">' \
    '<title>Lector de archivos</title>' \
    '<meta http-equiv="refresh" content="0; url=vnc.html?autoconnect=1&resize=scale&reconnect=1&reconnect_delay=1000&show_dot=1">' \
    > /usr/share/novnc/index.html

# Usuario sin privilegios: la aplicacion no necesita root.
RUN useradd --create-home --shell /bin/bash lector

WORKDIR /app

COPY --from=build /build/target/lector-archivos.jar /app/app.jar
COPY --from=build /build/target/lib /app/lib
COPY docker/entrypoint.sh /usr/local/bin/entrypoint.sh
COPY docker/fluxbox-init /home/lector/.fluxbox/init
COPY docker/fluxbox-overlay /home/lector/.fluxbox/overlay
COPY docker/ejemplos /app/ejemplos

RUN chmod +x /usr/local/bin/entrypoint.sh \
    && mkdir -p /data /home/lector/.config/lector-archivos \
    && chown -R lector:lector /app /data /home/lector \
    # Xvfb crea aqui su socket. El usuario sin privilegios no puede crear el
    # directorio por su cuenta, asi que se deja listo con los permisos de /tmp.
    && mkdir -p /tmp/.X11-unix \
    && chmod 1777 /tmp/.X11-unix

# Geometria de la pantalla virtual y puertos publicados.
ENV SCREEN_WIDTH=1440 \
    SCREEN_HEIGHT=900 \
    SCREEN_DEPTH=24 \
    DISPLAY=:99 \
    VNC_PORT=5900 \
    WEB_PORT=6080 \
    JAVA_OPTS="" \
    HOME=/home/lector

USER lector

VOLUME ["/data"]
EXPOSE 6080 5900

# tini reparte las senales y entierra los procesos huerfanos que deja Xvfb.
ENTRYPOINT ["/usr/bin/tini", "--", "/usr/local/bin/entrypoint.sh"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=25s --retries=4 \
    CMD bash -c 'echo > /dev/tcp/127.0.0.1/${WEB_PORT}' || exit 1
