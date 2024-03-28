FROM eclipse-temurin:21-jdk@sha256:1f64d3e6c88850f2d88982c561954683803e1dec8894e85cda1d1df89de93454

RUN mkdir -p /launch /opt/bin

COPY assets/yahcli.jar /opt/bin
COPY assets/screened-launch.sh /opt/bin

WORKDIR /launch

ENTRYPOINT ["/opt/bin/screened-launch.sh"]
