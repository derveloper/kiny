FROM openjdk:8-slim

ADD ./build/libs/kiny-1.0-SNAPSHOT.jar /kiny.jar

EXPOSE 9090

CMD ["java","-jar","/kiny.jar"]