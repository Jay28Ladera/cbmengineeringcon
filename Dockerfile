# Stage 1: build with Maven + JDK 21 (matches <java.version>21</java.version> in pom.xml)
FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy pom.xml first so dependencies are cached between builds
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Copy the source and build
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# Stage 2: lightweight Java 21 runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=builder /app/target/admin-system-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
# Use up to 75% of the container's memory (Render's small plans have 512 MB)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
