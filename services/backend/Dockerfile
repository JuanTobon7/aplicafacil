# Imagen genérica para cualquier módulo del backend.
#   docker build --build-arg MODULE=platform/config-server -t aplicafacil/config-server .
#   docker build --build-arg MODULE=jobs-service           -t aplicafacil/jobs-service .

FROM eclipse-temurin:25-jdk AS build
ARG MODULE
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY libs/ libs/
COPY platform/ platform/
COPY service-parent/ service-parent/
COPY auth-service/ auth-service/
COPY people-service/ people-service/
COPY profile-service/ profile-service/
COPY jobs-service/ jobs-service/
COPY automation-service/ automation-service/
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -q -B -DskipTests -pl ${MODULE} -am package \
    && cp ${MODULE}/target/*.jar /workspace/app.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 spring
USER spring
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
