FROM openjdk:15-jdk
COPY target/zeebe-http-worker.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-Djava.security.egd=file:/dev/./urandom","-jar","/app.jar"]