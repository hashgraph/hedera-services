## Installs OpenJDK12 and openssl (used by Swirlds Platform to 
## generate node keys for e.g. signing states), then copies 
## required libraries and startup assets for a node with:
##  * Configuration from /opt/hedera/services/config-mount; and, 
##  * Logs at /opt/hedera/services/output; and, 
##  * Saved states under /opt/hedera/services/output
FROM ubuntu:20.10 AS base-runtime
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y dos2unix openssl openjdk-15-jdk libsodium23 postgresql-client
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
RUN mkdir /opt/hedera/services/hapi-utils
COPY hapi-utils /opt/hedera/services/hapi-utils
RUN mkdir /opt/hedera/services/hapi-fees
COPY hapi-fees /opt/hedera/services/hapi-fees
RUN mkdir /opt/hedera/services/hedera-node
COPY hedera-node /opt/hedera/services/hedera-node
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
