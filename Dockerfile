# Stage 1: Build JAR menggunakan Maven
FROM maven:3-amazoncorretto-25 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Jalankan aplikasi dengan JDK 21 (lebih kecil)
FROM amazoncorretto:25-headful
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]