FROM node:20-alpine

WORKDIR /app

# Dependencias (Baileys + pino)
COPY package.json ./
RUN npm install --omit=dev

# Código del worker
COPY main.js ./

ENV WA_HTTP_PORT=3001
ENV WA_HTTP_HOST=0.0.0.0
# Carpeta persistente: sesión (wa-auth) y configuración del bot
ENV NODE_PATH=/data

EXPOSE 3001
VOLUME ["/data"]

CMD ["node", "main.js"]
