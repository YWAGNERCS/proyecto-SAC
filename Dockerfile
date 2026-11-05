# Imagen de producción del backend (S13). Multi-stage: se compila con Maven en una imagen
# y la final solo lleva el JRE y el JAR (sin código fuente, sin Maven, sin secretos).
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Dependencias en su propia capa: si solo cambia el código, no se vuelven a descargar
RUN --mount=type=cache,target=/root/.m2 mvn -q -B dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# Las tareas programadas (resumen diario, alertas de mantenimiento) usan la hora local:
# sin esto el contenedor corre en UTC, 5 horas adelantado a Lima.
ENV TZ=America/Lima
# Usuario sin privilegios: el proceso no corre como root dentro del contenedor
RUN useradd --system --uid 10001 sgejd
COPY --from=build /app/target/*.jar app.jar
USER sgejd
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
