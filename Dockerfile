# Multi-stage: Vite UI build + Spring Boot gyanwire-server fat jar.
FROM node:20-alpine AS ui-build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY client ./client
COPY vite.config.js ./
ARG VITE_GOOGLE_OAUTH_CLIENT_ID=
ARG VITE_BACKEND_URL=https://gyanwire-r5eanyyt6a-el.a.run.app
ENV VITE_GOOGLE_OAUTH_CLIENT_ID=$VITE_GOOGLE_OAUTH_CLIENT_ID
ENV VITE_BACKEND_URL=$VITE_BACKEND_URL
RUN npm run build

FROM eclipse-temurin:21-jdk-alpine AS api-build
WORKDIR /app
COPY gyanwire-server ./gyanwire-server
WORKDIR /app/gyanwire-server
RUN chmod +x ./gradlew && ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
ENV PORT=8080
ENV HOST=0.0.0.0
COPY --from=api-build /app/gyanwire-server/build/libs/*.jar /app/app.jar
COPY --from=ui-build /app/dist /app/dist
EXPOSE 8080
USER nobody
CMD ["java", "-jar", "/app/app.jar"]
