FROM ubuntu:20.10 AS base-runtime
RUN apt-get update && \
  apt-get upgrade -y && \
  apt-get install -y openjdk-15-jdk

RUN mkdir -p /launch

COPY assets/yahcli.jar /launch/

WORKDIR /launch

ENTRYPOINT ["java", "-jar", "yahcli.jar"]
