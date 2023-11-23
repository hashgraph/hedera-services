########################################################################################################################
#
# Define Global Build Arguments
#
########################################################################################################################
ARG UBUNTU_SHA="sha256:ceed028aae0eac7db9dd33bd89c14d5a9991d73443b0de24ba0db250f47491d2"

ARG HAVEGED_VERSION="1.9.1-6"

########################################################################################################################
#
# Setup Builder Image
#
########################################################################################################################
FROM ubuntu@${UBUNTU_SHA} AS haveged-builder

# Define Global Argument Refs
ARG HAVEGED_VERSION

# Import Checksum Files from the Build Context
ADD checksums/* /tmp/checksums/

# Install basic OS utilities for building
RUN apt-get update && \
	apt-get install -y tar gzip

# Download Haveged deb archive
WORKDIR "/tmp/deb"
RUN	apt-get update && \
	apt-get download -y haveged=${HAVEGED_VERSION} libhavege1=${HAVEGED_VERSION} && \
	apt-get clean all

# Validate Haveged deb archive
RUN sha256sum -c /tmp/checksums/haveged_${HAVEGED_VERSION}_amd64.deb.sha256 && \
    sha256sum -c /tmp/checksums/libhavege1_${HAVEGED_VERSION}_amd64.deb.sha256

########################################################################################################################
#
# Build Final Image
#
########################################################################################################################
FROM ubuntu@${UBUNTU_SHA} AS network-node-haveged

# Define Standard Environment Variables
ENV LC_ALL=C.UTF-8
ENV DEBIAN_FRONTEND=noninteractive

# Fetch Validated Debian Packages
COPY --from=haveged-builder /tmp/deb/*.deb /tmp/deb/

# Install Validated Local Debian Packages
RUN apt-get --allow-unauthenticated install -y /tmp/deb/*.deb && \
    apt-get autoremove && \
    apt-get autoclean && \
    apt-get clean all && \
    rm -rf /tmp/deb

# Set Final Working Directory and Command/Entrypoint
WORKDIR "/"
ENTRYPOINT ["/usr/bin/env", "haveged", "-v", "3", "-F"]
