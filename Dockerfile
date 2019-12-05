FROM openjdk:11-jre
COPY target/zeebe-http-worker.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]