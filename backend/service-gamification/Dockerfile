FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY . .

COPY service-gamification/ service-gamification/

RUN mvn clean package -pl service-gamification -am -DskipTests

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /app/service-gamification/target/*.jar app.jar
EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]