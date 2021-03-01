FROM ubuntu:20.10 AS base-runtime
RUN apt-get update && \
  apt-get upgrade -y && \
  apt-get install -y openjdk-15-jdk

RUN mkdir -p /launch /opt/bin

COPY assets/yahcli.jar /opt/bin

WORKDIR /launch

ENTRYPOINT ["java", "-jar", "/opt/bin/yahcli.jar"]
