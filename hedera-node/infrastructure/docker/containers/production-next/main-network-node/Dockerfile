########################################################################################################################
#
# Define Global Build Arguments
#
########################################################################################################################

ARG UBUNTU_TAG="lunar-20231128"
ARG SDK_DATA_PATH="sdk/data"

########################################################################################################################
#
# Setup Ephemeral Java Downloader Layer
#
########################################################################################################################
FROM ubuntu:${UBUNTU_TAG} AS java-builder

# Define Standard Environment Variables
ENV LC_ALL C.UTF-8
ENV DEBIAN_FRONTEND noninteractive
ENV LANG C.UTF-8
ENV LC_ALL C.UTF-8

# Install basic OS utilities for building
RUN apt-get update && \
	apt-get install --yes --no-install-recommends tar gzip gnupg2 curl ca-certificates && \
    apt-get autoclean --yes && \
    apt-get clean all --yes && \
    rm -rf /var/lib/{apt,dpkg,cache,log}/ && \
    rm -rf /var/cache/apt/

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

########################################################################################################################
#
# Setup OS Base Layer
#
########################################################################################################################
FROM ubuntu:${UBUNTU_TAG} AS operating-system-base

# Define Standard Environment Variables
ENV LANG C.UTF-8
ENV LC_ALL C.UTF-8
ENV DEBIAN_FRONTEND noninteractive

# Install basic OS utilities
RUN apt-get update && \
    apt-get install -y tar gzip openssl zlib1g libsodium23 libreadline7 sudo netcat net-tools && \
    apt-get autoclean --yes && \
    apt-get clean all --yes && \
    rm -rf /var/lib/{apt,dpkg,cache,log}/ && \
    rm -rf /var/cache/apt/

# Create Application Folders
RUN mkdir -p "/opt/hgcapp" && \
    mkdir -p "/opt/hgcapp/accountBalances" && \
    mkdir -p "/opt/hgcapp/eventsStreams" && \
    mkdir -p "/opt/hgcapp/recordStreams" && \
    mkdir -p "/opt/hgcapp/services-hedera" && \
    mkdir -p "/opt/hgcapp/services-hedera/HapiApp2.0" && \
    mkdir -p "/opt/hgcapp/services-hedera/HapiApp2.0/data" && \
    mkdir -p "/opt/hgcapp/services-hedera/HapiApp2.0/data/apps" && \
    mkdir -p "/opt/hgcapp/services-hedera/HapiApp2.0/data/config" && \
    mkdir -p "/opt/hgcapp/services-hedera/HapiApp2.0/data/diskFs" && \
    mkdir -p "/opt/hgcapp/services-hedera/HapiApp2.0/data/keys" && \
    mkdir -p "/opt/hgcapp/services-hedera/HapiApp2.0/data/lib" && \
    mkdir -p "/opt/hgcapp/services-hedera/HapiApp2.0/data/onboard" && \
    mkdir -p "/opt/hgcapp/services-hedera/HapiApp2.0/data/stats" && \
    mkdir -p "/opt/hgcapp/services-hedera/HapiApp2.0/data/saved" && \
    mkdir -p "/opt/hgcapp/services-hedera/HapiApp2.0/data/upgrade"

# Configure the standard user account
RUN groupadd --gid 2000 hedera && \
    useradd --no-user-group --create-home --uid 2000 --gid 2000 --shell /bin/bash hedera && \
    chown -R hedera:hedera /opt/hgcapp

# Configure SUDO support
RUN echo >> /etc/sudoers && \
    echo "%hedera ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

# Define Volume Bindpoints
VOLUME "/opt/hgcapp/accountBalances"
VOLUME "/opt/hgcapp/eventsStreams"
VOLUME "/opt/hgcapp/recordStreams"
VOLUME "/opt/hgcapp/services-hedera/HapiApp2.0/data/config"
VOLUME "/opt/hgcapp/services-hedera/HapiApp2.0/data/diskFs"
VOLUME "/opt/hgcapp/services-hedera/HapiApp2.0/data/keys"
VOLUME "/opt/hgcapp/services-hedera/HapiApp2.0/data/onboard"
VOLUME "/opt/hgcapp/services-hedera/HapiApp2.0/data/stats"
VOLUME "/opt/hgcapp/services-hedera/HapiApp2.0/data/saved"
VOLUME "/opt/hgcapp/services-hedera/HapiApp2.0/data/upgrade"

########################################################################################################################
#
# Setup Java Base Layer
#
########################################################################################################################
FROM operating-system-base AS java-base

# Define Standard Environment Variables
ENV JAVA_VERSION "jdk-21.0.1+12"
ENV JAVA_HOME /usr/local/java
ENV PATH ${JAVA_HOME}/bin:${PATH}

COPY --from=java-builder ${JAVA_HOME}/ ${JAVA_HOME}/

########################################################################################################################
#
# Setup Final Container Image
#
########################################################################################################################
FROM java-base AS final-image

# Import Build Arguments
ARG SDK_DATA_PATH

# Define Environment Variables
ENV JAVA_HEAP_MIN ""
ENV JAVA_HEAP_MAX ""
ENV JAVA_OPTS ""
ENV JAVA_MAIN_CLASS ""
ENV JAVA_CLASS_PATH ""

# Performance Tuning for Malloc
ENV MALLOC_ARENA_MAX 4

# Log Folder Name Override
ENV LOG_DIR_NAME ""

# Add SDK components
ADD ${SDK_DATA_PATH}/apps/* /opt/hgcapp/services-hedera/HapiApp2.0/data/apps/
ADD ${SDK_DATA_PATH}/lib/* /opt/hgcapp/services-hedera/HapiApp2.0/data/lib/

# Add the entrypoint script
ADD entrypoint.sh /opt/hgcapp/services-hedera/HapiApp2.0/

# Ensure proper file permissions
RUN chmod -R +x /opt/hgcapp/services-hedera/HapiApp2.0/entrypoint.sh && \
    chown -R 2000:2000 /opt/hgcapp/services-hedera/HapiApp2.0

# Expose TCP/UDP Port Definitions
EXPOSE 50111/tcp 50211/tcp 50212/tcp

# Set Final Working Directory, User, and Entrypoint
USER 2000
WORKDIR "/opt/hgcapp"
ENTRYPOINT ["/opt/hgcapp/services-hedera/HapiApp2.0/entrypoint.sh"]



