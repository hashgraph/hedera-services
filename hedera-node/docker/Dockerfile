## Installs OpenJDK17 and openssl (used by Swirlds Platform to
## generate node keys for e.g. signing states), then copies
## required libraries and startup assets for a node with:
##  * Configuration from /opt/hedera/services/config-mount; and,
##  * Logs at /opt/hedera/services/output; and,
##  * Saved states under /opt/hedera/services/output
## Ideally we'd use a much lighter (perhaps CoreOS) base, but this will do for now.
FROM ubuntu:22.04 AS base-runtime
RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get install -y dos2unix openssl libsodium23 bind9-host software-properties-common openjdk-17-jdk \
 && mkdir -p /opt/hedera/services/data/lib \
 && mkdir /opt/hedera/services/data/apps \
 && mkdir /opt/hedera/services/data/config \
 && mkdir /opt/hedera/services/data/saved \
 && mkdir /opt/hedera/services/data/onboard \
 && mkdir /opt/hedera/services/output \
 && mkdir /opt/hedera/services/config-mount

## Finishes by copying the Services JAR to the base runtime
FROM base-runtime AS final-image

WORKDIR /opt/hedera/services

COPY start-services.sh /opt/hedera/services/start-services.sh
COPY --from=services-data lib /opt/hedera/services/data/lib
COPY --from=services-data apps /opt/hedera/services/data/apps

RUN ls -al /opt/hedera/services/data/lib \
 && dos2unix start-services.sh

CMD ["/bin/sh", "-c", "./start-services.sh"]
