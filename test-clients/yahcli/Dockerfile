FROM ubuntu:21.10 AS base-runtime
# JDK
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y software-properties-common && \
    add-apt-repository -y ppa:openjdk-r/ppa && \
    apt-get install -y openjdk-17-jdk

RUN mkdir -p /launch /opt/bin

COPY assets/yahcli.jar /opt/bin
COPY assets/screened-launch.sh /opt/bin

WORKDIR /launch

ENTRYPOINT ["/opt/bin/screened-launch.sh"]
