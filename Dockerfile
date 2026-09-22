# Builds one image that serves both the API and the React app on a single origin.
#
#   docker build -t centsight .
#   docker run -p 8080:8080 --env-file .env centsight

# ---- 1. Build the React app ----
FROM node:22-alpine AS client
WORKDIR /client
COPY client/package.json client/package-lock.json ./
RUN npm ci
COPY client/ ./
RUN npm run build

# ---- 2. Build the Spring Boot jar, with the React build inside it ----
FROM maven:3.9-eclipse-temurin-21 AS server
WORKDIR /build
# Cache dependencies before touching source, so code changes don't re-download the world.
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=client /client/dist ./src/main/resources/static
RUN mvn -B -q clean package -DskipTests

# ---- 3. Runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=server /build/target/*.jar app.jar
USER app

EXPOSE 8080
ENV PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC"

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
