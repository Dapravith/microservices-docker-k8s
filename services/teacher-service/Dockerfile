FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/login-service/pom.xml services/login-service/pom.xml
COPY services/student-service/pom.xml services/student-service/pom.xml
COPY services/teacher-service/pom.xml services/teacher-service/pom.xml
RUN mvn -B -q -pl services/teacher-service -am dependency:go-offline
COPY services/teacher-service/src services/teacher-service/src
RUN mvn -B -q -pl services/teacher-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/teacher-service/target/teacher-service.jar app.jar
EXPOSE 8083
ENV JAVA_OPTS="-Xms64m -Xmx256m -XX:MaxRAMPercentage=50.0 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD wget -qO- http://localhost:8083/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
