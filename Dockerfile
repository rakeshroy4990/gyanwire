# Multi-stage image: Vite client build + Express API (serves dist in production).
FROM node:20-alpine AS build

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY client ./client
COPY vite.config.js ./
COPY server ./server

# Public GIS client id — baked into the Vite bundle at build time.
ARG VITE_GOOGLE_OAUTH_CLIENT_ID=
ENV VITE_GOOGLE_OAUTH_CLIENT_ID=$VITE_GOOGLE_OAUTH_CLIENT_ID

RUN npm run build

FROM node:20-alpine AS runtime

WORKDIR /app

ENV NODE_ENV=production
ENV HOST=0.0.0.0
ENV PORT=8080

COPY package.json package-lock.json ./
RUN npm ci --omit=dev && npm cache clean --force

COPY server ./server
COPY --from=build /app/dist ./dist

EXPOSE 8080

USER node

CMD ["node", "server/index.js"]
