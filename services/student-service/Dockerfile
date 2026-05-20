FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw .
COPY pom.xml .
COPY services/api-gateway/pom.xml services/api-gateway/pom.xml
COPY services/login-service/pom.xml services/login-service/pom.xml
COPY services/student-service/pom.xml services/student-service/pom.xml
COPY services/teacher-service/pom.xml services/teacher-service/pom.xml
RUN ./mvnw -B -q -pl services/student-service -am dependency:go-offline
COPY services/student-service/src services/student-service/src
RUN ./mvnw -B -q -pl services/student-service -am -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/student-service/target/student-service.jar app.jar
EXPOSE 8082
ENV JAVA_OPTS="-Xms64m -Xmx256m -XX:MaxRAMPercentage=50.0 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
HEALTHCHECK --interval=15s --timeout=3s --retries=5 \
  CMD wget -qO- http://localhost:8082/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
