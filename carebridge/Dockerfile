FROM node:22-alpine AS web
WORKDIR /app
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml ./
COPY backend/src ./src
COPY --from=web /app/dist/ ./src/main/resources/static/
RUN mvn -B clean verify

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=build /app/target/carebridge.jar ./carebridge.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/carebridge.jar"]
