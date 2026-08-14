FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S xiaosu && adduser -S xiaosu -G xiaosu

WORKDIR /app
RUN mkdir -p uploads logs && chown -R xiaosu:xiaosu /app

COPY --chown=xiaosu:xiaosu target/*.jar app.jar

USER xiaosu
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
