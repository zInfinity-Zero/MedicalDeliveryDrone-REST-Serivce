FROM --platform=linux/amd64 eclipse-temurin:21-jre
LABEL authors="charl"


EXPOSE 8080

WORKDIR /app

COPY ./target/ilpcoursework1-0.0.1-SNAPSHOT.jar app.jar

#what preocess to run when container started
ENTRYPOINT ["java","-jar","app.jar"]