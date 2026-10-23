#!/data/data/com.termux/files/usr/bin/bash
# ============================================================================
# Instala y arranca el puente de WhatsApp EN EL TELÉFONO usando Termux.
# (Node.js corriendo en Android, sin PC).
#
# Uso:
#   1. Instala Termux desde F-Droid: https://f-droid.org/packages/com.termux/
#   2. Abre Termux y pega/ejecuta:
#        bash termux-install.sh
#      (o descarga y ejecuta este script)
#
# Variables opcionales:
#   WA_HTTP_PORT=3001
#   WA_TOKEN=mi-token-secreto
# ============================================================================
set -e

: "${WA_HTTP_PORT:=3001}"
: "${WA_TOKEN:=difusion}"

echo "== Instalando Node.js y Git en Termux =="
pkg update -y
pkg install -y nodejs git

echo "== Descargando el worker (Difusión) =="
rm -rf "$HOME/difusion-src"
git clone --depth 1 https://github.com/SaveFail/difusion.git "$HOME/difusion-src"

rm -rf "$HOME/difusion-wa"
cp -r "$HOME/difusion-src/wa-bridge" "$HOME/difusion-wa"
cd "$HOME/difusion-wa"

echo "== Instalando dependencias (Baileys) =="
npm install --omit=dev

echo "== Iniciando worker en segundo plano (puerto $WA_HTTP_PORT) =="
pkill -f "difusion-wa/main.js" 2>/dev/null || true
nohup env WA_HTTP_PORT="$WA_HTTP_PORT" WA_TOKEN="$WA_TOKEN" \
  node "$HOME/difusion-wa/main.js" > "$HOME/difusion-wa/wa.log" 2>&1 &
sleep 2

echo ""
echo "✅ Worker iniciado."
echo "   URL para la app:  http://127.0.0.1:$WA_HTTP_PORT"
echo "   Token:            $WA_TOKEN"
echo "   Log:              $HOME/difusion-wa/wa.log"
echo ""
echo "Siguiente: en la app ▸ Ajustes ▸ Bot:"
echo "   1) URL: http://127.0.0.1:$WA_HTTP_PORT"
echo "   2) Token: $WA_TOKEN"
echo "   3) Tu número y Vincular."
echo ""
echo "Para que siga tras cerrar Termux instala también 'Termux:Boot' (F-Droid)"
echo "y copia este arranque a ~/.termux/boot/."
