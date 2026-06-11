# Этап 1: Сборка приложения (gradle)
FROM gradle:8.10-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle clean build -x test   # -x test отключает тесты для ускорения сборки

# Этап 2: Запуск (Amazon Corretto 17 на Alpine Linux)
FROM amazoncorretto:17-alpine
WORKDIR /app
#COPY --from=build /app/build/libs/*.jar app.jar
COPY --from=build /app/build/libs/*.jar /app/
RUN mv /app/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]