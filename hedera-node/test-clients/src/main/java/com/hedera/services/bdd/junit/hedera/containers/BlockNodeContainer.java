package com.hedera.services.bdd.junit.hedera.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * A testcontainer for running a block node server instance.
 */
public class BlockNodeContainer extends GenericContainer<BlockNodeContainer> {
    private static final int INTERNAL_PORT = 8080;
    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("block-node-server:0.4.0-SNAPSHOT");

    /**
     * Creates a new block node container with the default image.
     */
    public BlockNodeContainer() {
        this(DEFAULT_IMAGE_NAME);
    }

    /**
     * Creates a new block node container with the specified image.
     *
     * @param dockerImageName the docker image to use
     */
    public BlockNodeContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
        withExposedPorts(INTERNAL_PORT);
        waitingFor(Wait.forLogMessage(".*Block Node Server started.*\\n", 1));
    }

    /**
     * Gets the mapped port for the block node gRPC server.
     *
     * @return the host port mapped to the container's internal port
     */
    public int getGrpcPort() {
        return getMappedPort(INTERNAL_PORT);
    }

    /**
     * Gets the host address for connecting to this block node.
     *
     * @return the host address (usually localhost)
     */
    public String getHost() {
        return getHost();
    }
} 