## Installs OpenJDK21 and openssl (used by Swirlds Platform to
## generate node keys for e.g. signing states), then copies
## required libraries and startup assets for a node with:
##  * Configuration from /opt/hedera/services/config-mount; and,
##  * Logs at /opt/hedera/services/output; and,
##  * Saved states under /opt/hedera/services/output
## Ideally we'd use a much lighter (perhaps CoreOS) base, but this will do for now.
FROM ubuntu:22.04@sha256:77906da86b60585ce12215807090eb327e7386c8fafb5402369e421f44eff17e AS openjdk-builder

# Define Standard Environment Variables
ENV LC_ALL=C.UTF-8
ENV DEBIAN_FRONTEND=noninteractive

# Install basic OS utilities for building
RUN apt-get update && \
	apt-get install --yes tar gzip gnupg2 curl && \
    apt-get autoclean && \
    apt-get clean all && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /var/cache/apt

##########################
####    Java Setup    ####
##########################
RUN set -eux; \
        ARCH="$(dpkg --print-architecture)"; \
        case "${ARCH}" in \
           aarch64|arm64) \
             ESUM='e184dc29a6712c1f78754ab36fb48866583665fa345324f1a79e569c064f95e9'; \
             BINARY_URL='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.1%2B12/OpenJDK21U-jdk_aarch64_linux_hotspot_21.0.1_12.tar.gz'; \
             ;; \
           amd64|i386:x86-64) \
             ESUM='1a6fa8abda4c5caed915cfbeeb176e7fbd12eb6b222f26e290ee45808b529aa1'; \
             BINARY_URL='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.1%2B12/OpenJDK21U-jdk_x64_linux_hotspot_21.0.1_12.tar.gz'; \
             ;; \
           ppc64el|powerpc:common64) \
             ESUM='9574828ef3d735a25404ced82e09bf20e1614f7d6403956002de9cfbfcb8638f'; \
             BINARY_URL='https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.1%2B12/OpenJDK21U-jdk_ppc64le_linux_hotspot_21.0.1_12.tar.gz'; \
             ;; \
           *) \
             echo "Unsupported arch: ${ARCH}"; \
             exit 1; \
             ;; \
        esac; \
    curl -LfsSo /tmp/openjdk.tar.gz ${BINARY_URL}; \
    echo "${ESUM} */tmp/openjdk.tar.gz" | sha256sum -c -; \
    mkdir -p /usr/local/java; \
    tar --extract \
    	      --file /tmp/openjdk.tar.gz \
    	      --directory "/usr/local/java" \
    	      --strip-components 1 \
    	      --no-same-owner \
    	  ; \
    rm -f /tmp/openjdk.tar.gz /usr/local/java/lib/src.zip;

FROM ubuntu:22.04@sha256:77906da86b60585ce12215807090eb327e7386c8fafb5402369e421f44eff17e AS base-runtime
RUN apt-get update && \
    apt-get install --yes --no-install-recommends dos2unix openssl libsodium23 bind9-host libnetty-tcnative-jni && \
    apt-get autoclean && \
    apt-get clean all && \
    rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /var/cache/apt && \
    mkdir -p /opt/hedera/services/data/lib && \
    mkdir /opt/hedera/services/data/apps && \
    mkdir /opt/hedera/services/data/config && \
    mkdir /opt/hedera/services/data/saved && \
    mkdir /opt/hedera/services/data/onboard && \
    mkdir /opt/hedera/services/output && \
    mkdir /opt/hedera/services/config-mount

## Finishes by copying the Services JAR to the base runtime
FROM base-runtime AS final-image

ENV JAVA_VERSION="jdk-21.0.1+12"
ENV JAVA_HOME="/usr/local/java/"
ENV MALLOC_ARENA_MAX=4

# Fetch Validated Java Binaries
COPY --from=openjdk-builder /usr/local/java/ /usr/local/java/

# Install Java Alternatives
RUN update-alternatives --install "/usr/bin/java" "java" "${JAVA_HOME}/bin/java" 1500 && \
    update-alternatives --install "/usr/bin/javac" "javac" "${JAVA_HOME}/bin/javac" 1500 && \
    update-alternatives --install "/usr/bin/javadoc" "javadoc" "${JAVA_HOME}/bin/javadoc" 1500 && \
    update-alternatives --install "/usr/bin/jshell" "jshell" "${JAVA_HOME}/bin/jshell" 1500 && \
    update-alternatives --install "/usr/bin/jstack" "jstack" "${JAVA_HOME}/bin/jstack" 1500 && \
    update-alternatives --install "/usr/bin/jps" "jps" "${JAVA_HOME}/bin/jps" 1500 && \
    update-alternatives --install "/usr/bin/jmap" "jmap" "${JAVA_HOME}/bin/jmap" 1500

WORKDIR /opt/hedera/services

COPY start-services.sh /opt/hedera/services/start-services.sh
COPY --from=services-data lib /opt/hedera/services/data/lib
COPY --from=services-data apps /opt/hedera/services/data/apps

RUN ls -al /opt/hedera/services/data/lib \
 && dos2unix start-services.sh

CMD ["/bin/sh", "-c", "./start-services.sh"]
