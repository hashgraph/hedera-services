########################################################################################################################
#
# Define Global Build Arguments
#
########################################################################################################################
ARG IMAGE_PREFIX="gcr.io/hedera-registry/"
ARG IMAGE_TAG="latest"

FROM ${IMAGE_PREFIX}network-node-base:${IMAGE_TAG}

# Define Environment Variables
ENV JAVA_HEAP_MIN=""
ENV JAVA_HEAP_MAX=""
ENV JAVA_OPTS=""

# Add SDK components
ADD sdk/data/apps/* /opt/hgcapp/services-hedera/HapiApp2.0/data/apps/
ADD sdk/data/lib/* /opt/hgcapp/services-hedera/HapiApp2.0/data/lib/

# Add Diagnostic Utilities
RUN apt-get update && \
    apt-get install --yes net-tools && \
    apt-get autoremove --yes && \
    apt-get autoclean --yes && \
    apt-get clean all --yes && \
    rm -rf /var/lib/{apt,dpkg,cache,log}/

# Add the entrypoint script
ADD entrypoint.sh /opt/hgcapp/services-hedera/HapiApp2.0/

# Ensure MainNet specific folder & files exist
RUN mkdir -p "/opt/hgcapp/services-hedera/HapiApp2.0/output" && \
    touch "/opt/hgcapp/services-hedera/HapiApp2.0/stdout.log"

# Ensure proper file permissions
RUN chmod -R +x /opt/hgcapp/services-hedera/HapiApp2.0/entrypoint.sh && \
    chown -R 2000:2000 /opt/hgcapp/services-hedera/HapiApp2.0

# Expose TCP/UDP Port Definitions
EXPOSE 50111/tcp 50211/tcp 50212/tcp

# Set Final Working Directory, User, and Entrypoint
USER 2000
WORKDIR "/opt/hgcapp"
ENTRYPOINT ["/opt/hgcapp/services-hedera/HapiApp2.0/entrypoint.sh"]
