# Stage 1: Build
FROM maven:3.8.4-openjdk-17-slim AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/tracker-0.0.1-SNAPSHOT.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
# Use the port Render expects (default is 10000, but our app uses 8081; Render handles this via the PORT env var)
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
