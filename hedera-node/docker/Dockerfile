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

## Finishes by copying the Services JAR to the base runtime
FROM base-runtime AS final-image

WORKDIR /opt/hedera/services

COPY start-services.sh /opt/hedera/services/start-services.sh

COPY .env /opt/hedera/services
RUN for PIECE in $(cat .env | head -1 | tr '=' ' '); do \
  if [ "$IS_VERSION" = "true" ]; then echo $PIECE >> .VERSION ; else IS_VERSION=true; fi done

COPY --from=services-data lib /opt/hedera/services/data/lib
RUN ls -al /opt/hedera/services/data/lib
COPY --from=services-data onboard/StartUpAccount.txt /opt/hedera/services/data/onboard
COPY --from=services-data apps /opt/hedera/services/data/apps
RUN dos2unix start-services.sh
CMD ["/bin/sh", "-c", "./start-services.sh"]
