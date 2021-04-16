FROM ubuntu:20.10 AS base-runtime
RUN apt-get update && \
  apt-get upgrade -y && \
  apt-get install -y openjdk-15-jdk

RUN mkdir -p /launch /opt/bin

COPY assets/yahcli.jar /opt/bin
COPY assets/screened-launch.sh /opt/bin

WORKDIR /launch

ENTRYPOINT ["/opt/bin/screened-launch.sh"]
