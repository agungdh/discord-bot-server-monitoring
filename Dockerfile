# Stage 1: Build JAR dengan Maven (bebas)
FROM maven:3-amazoncorretto-25 AS build
WORKDIR /app

# Cache dependencies biar build cepet
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

# Build
COPY . .
RUN mvn -q clean package -DskipTests

# Stage 2: Runtime Debian trixie + Java 25 JRE (non-headless)
FROM debian:trixie
ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      openjdk-25-jre \
      ca-certificates \
 && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/target/*.jar /app/app.jar

# (opsional) sedikit tuning JVM
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
