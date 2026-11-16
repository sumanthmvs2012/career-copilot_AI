# Build: docker build -t career-copilot-backend backend
# Run:   docker run --env-file backend/.env -p 8080:8080 career-copilot-backend
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home appuser
COPY --from=build /app/target/*.jar app.jar
USER appuser
EXPOSE 8080
# All secrets and settings come from environment variables (see backend/.env.example).
ENTRYPOINT ["java", "-jar", "app.jar"]
