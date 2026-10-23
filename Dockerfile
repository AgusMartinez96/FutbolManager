# Stage 1: compile the SPA. Vite writes to ../src/main/resources/static, i.e. /app/src/main/resources/static.
FROM node:24-alpine AS frontend
WORKDIR /app/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# Stage 2: package the jar with the compiled SPA on the classpath.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
COPY src/ src/
COPY --from=frontend /app/src/main/resources/static/ src/main/resources/static/
RUN ./mvnw -q -DskipTests package

# Stage 3: runtime image, non-root.
FROM eclipse-temurin:21-jre
RUN groupadd --system app && useradd --system --gid app --no-create-home app
COPY --from=build /app/target/futbol-manager.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
