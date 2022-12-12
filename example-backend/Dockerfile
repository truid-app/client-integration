FROM maven:3.8.6-eclipse-temurin-11

WORKDIR /build
COPY . .
RUN mvn package


FROM eclipse-temurin:11.0.17_8-jre

WORKDIR /app
COPY --from=0 ./build/target/example-backend-*.jar ./example-backend.jar
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "example-backend.jar"]
