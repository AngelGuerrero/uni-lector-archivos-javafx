#!/usr/bin/env bash
#
# Construye la imagen si hace falta, levanta el contenedor y abre el navegador.
# Uso:  ./run.sh            arranca
#       ./run.sh --build    fuerza reconstruir la imagen
#       ./run.sh --stop     detiene y limpia

set -euo pipefail
cd "$(dirname "$0")"

URL="http://localhost:6080"

abrir_navegador() {
    if   command -v xdg-open >/dev/null 2>&1; then xdg-open "$URL" >/dev/null 2>&1 &
    elif command -v open     >/dev/null 2>&1; then open "$URL" >/dev/null 2>&1 &
    fi
}

case "${1:-}" in
    --stop)
        docker compose down
        exit 0
        ;;
    --build)
        docker compose build --no-cache
        ;;
esac

docker compose up -d --build

printf 'Esperando a que la aplicacion responda'
for _ in $(seq 1 60); do
    if curl -sf -o /dev/null "$URL"; then
        printf '\n'
        echo "Listo: $URL"
        abrir_navegador
        exit 0
    fi
    printf '.'
    sleep 1
done

printf '\n'
echo "La aplicacion no respondio a tiempo. Revisa los registros con:"
echo "    docker compose logs"
exit 1
