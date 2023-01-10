## Installs OpenJDK17 and openssl (used by Swirlds Platform to
## generate node keys for e.g. signing states), then copies
## required libraries and startup assets for a node with:
##  * Configuration from /opt/hedera/services/config-mount; and,
##  * Logs at /opt/hedera/services/output; and,
##  * Saved states under /opt/hedera/services/output
FROM ubuntu:20.04 AS base-runtime
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y dos2unix openssl libsodium23 bind9-host

# JDK
RUN apt-get install -y software-properties-common && \
    add-apt-repository -y ppa:openjdk-r/ppa && \
    apt-get install -y openjdk-17-jdk

# Services runtime
RUN mkdir -p /opt/hedera/services/data/lib
RUN mkdir /opt/hedera/services/data/apps
RUN mkdir /opt/hedera/services/data/config
RUN mkdir /opt/hedera/services/data/saved
RUN mkdir /opt/hedera/services/data/onboard
RUN mkdir /opt/hedera/services/output
RUN mkdir /opt/hedera/services/config-mount

## Builds the HederaNode.jar from the current source tree and creates
## the /opt/hedera/services/.VERSION file
FROM base-runtime AS services-builder

# Maven
# Note: Java 17 requires Maven 3.8+ so the distro provided one just won't do
RUN apt-get update && \
    apt-get install -y wget unzip
ENV PATH=/opt/apache-maven-3.8.6/bin:$PATH

WORKDIR /opt/hedera/services
# Install Services
COPY .env /opt/hedera/services
RUN for PIECE in $(cat .env | head -1 | tr '=' ' '); do \
  if [ "$IS_VERSION" = "true" ]; then echo $PIECE >> .VERSION ; else IS_VERSION=true; fi done
RUN mkdir /opt/hedera/services/hedera-node
COPY hedera-node /opt/hedera/services/hedera-node
RUN mkdir /opt/hedera/services/test-clients
COPY test-clients /opt/hedera/services/test-clients
RUN mkdir /opt/hedera/services/buildSrc
COPY buildSrc /opt/hedera/services/buildSrc
RUN mkdir /opt/hedera/services/gradle
COPY gradle /opt/hedera/services/gradle
COPY gradlew /opt/hedera/services/gradlew
COPY gradle.properties /opt/hedera/services/gradle.properties
COPY settings.gradle.kts /opt/hedera/services/settings.gradle.kts
COPY build.gradle.kts /opt/hedera/services/build.gradle.kts

RUN ./gradlew assemble copyLib copyApp

## Finishes by copying the Services JAR to the base runtime
FROM base-runtime AS final-image
COPY docker/start-services.sh /opt/hedera/services/start-services.sh
COPY --from=services-builder /opt/hedera/services/.VERSION /opt/hedera/services
COPY --from=services-builder /opt/hedera/services/hedera-node/data/lib /opt/hedera/services/data/lib
RUN ls -al /opt/hedera/services/data/lib
COPY --from=services-builder /opt/hedera/services/hedera-node/data/onboard/StartUpAccount.txt /opt/hedera/services/data/onboard
COPY --from=services-builder /opt/hedera/services/hedera-node/data/apps /opt/hedera/services/data/apps
WORKDIR /opt/hedera/services
RUN dos2unix start-services.sh
CMD ["/bin/sh", "-c", "./start-services.sh"]
