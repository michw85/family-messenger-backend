# Этап 1: Сборка приложения (gradle)
FROM gradle:8.10-jdk17 AS build
WORKDIR /app
COPY . .
RUN gradle clean build -x test   # -x test отключает тесты для ускорения сборки

# Этап 2: Запуск (Amazon Corretto 17 на Alpine Linux)
FROM amazoncorretto:17-alpine
WORKDIR /app
#COPY --from=build /app/build/libs/*.jar app.jar
#COPY --from=build /app/build/libs/*.jar /app/
#RUN mv /app/*.jar app.jar
#COPY --from=build /app/build/libs/backend-0.0.1-SNAPSHOT.jar app.jar
# Копируем все jar-файлы из сборки
COPY --from=build /app/build/libs/*.jar /app/
# Перемещаем основной jar (не содержащий "-plain") в app.jar
RUN for jar in /app/*.jar; do if [[ ! "$jar" =~ -plain ]]; then mv "$jar" /app/app.jar; break; fi; done
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]