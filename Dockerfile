## Installs OpenJDK12 and openssl (used by Swirlds Platform to 
## generate node keys for e.g. signing states), then copies 
## required libraries and startup assets for a node with:
##  * Configuration from /opt/hedera/services/config-mount; and, 
##  * Logs at /opt/hedera/services/output; and, 
##  * Saved states under /opt/hedera/services/output
FROM ubuntu:18.04 AS base-runtime
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y wget dos2unix openssl
RUN cd /tmp && \
    wget --quiet https://jvm-storage.s3.amazonaws.com/openjdk-12.0.2_linux-x64_bin.tar.gz && \
    mkdir -p /usr/local/java && \
    tar -zxf openjdk-12.0.2_linux-x64_bin.tar.gz -C /usr/local/java && \
    update-alternatives --install "/usr/bin/java" "java" "/usr/local/java/jdk-12.0.2/bin/java" 1500 && \
    update-alternatives --install "/usr/bin/javac" "javac" "/usr/local/java/jdk-12.0.2/bin/javac" 1500 && \
    update-alternatives --install "/usr/bin/javadoc" "javadoc" "/usr/local/java/jdk-12.0.2/bin/javadoc" 1500 && \
    rm -f /tmp/jdk-12.0.2_linux-x64_bin.tar.gz
# Services runtime
RUN mkdir -p /opt/hedera/services/data/lib
RUN mkdir -p /opt/hedera/services/data/backup
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
RUN apt-get update && apt-get install -y maven
WORKDIR /opt/hedera/services
# Install Services
COPY .env /opt/hedera/services
RUN for PIECE in $(cat .env | head -1 | tr '=' ' '); do \
  if [ "$IS_VERSION" = "true" ]; then echo $PIECE >> .VERSION ; else IS_VERSION=true; fi done
COPY pom.xml /opt/hedera/services
RUN mkdir /opt/hedera/services/hapi-proto
COPY hapi-proto /opt/hedera/services/hapi-proto
RUN mkdir /opt/hedera/services/hedera-node
COPY hedera-node /opt/hedera/services/hedera-node
RUN rm -rf /opt/hedera/services/hedera-node/data/lib/*
RUN mkdir /opt/hedera/services/test-clients
COPY test-clients /opt/hedera/services/test-clients
RUN mvn install -pl hedera-node -am -DskipTests -Dmaven.gitcommitid.skip=true

## Finishes by copying the Services JAR to the base runtime
FROM base-runtime AS final-image
COPY image-utils/ /opt/hedera/services 
COPY --from=services-builder /opt/hedera/services/.VERSION /opt/hedera/services
COPY --from=services-builder /opt/hedera/services/hedera-node/data/lib /opt/hedera/services/data/lib
COPY --from=services-builder /opt/hedera/services/hedera-node/data/backup /opt/hedera/services/data/backup
COPY --from=services-builder /opt/hedera/services/hedera-node/swirlds.jar /opt/hedera/services/data/lib
RUN ls -al /opt/hedera/services/data/lib
COPY --from=services-builder /opt/hedera/services/hedera-node/data/onboard/StartUpAccount.txt /opt/hedera/services/data/onboard
COPY --from=services-builder /opt/hedera/services/hedera-node/data/apps /opt/hedera/services/data/apps
WORKDIR /opt/hedera/services
RUN dos2unix start-services.sh wait-for-it /opt/hedera/services/data/backup/*.sh
CMD ["/bin/sh", "-c", "./start-services.sh"]
