/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.bdd.junit.hedera.containers;

import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * A testcontainer for running a block node server instance.
 */
public class BlockNodeContainer extends GenericContainer<BlockNodeContainer> {
    private static final int INTERNAL_PORT = 8080;
    private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("block-node-server:0.4.0-SNAPSHOT");
    private static final String blockNodeVersion = "0.4.0-SNAPSHOT";

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
        withEnv("VERSION", blockNodeVersion);
        waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofMinutes(2)));
        waitingFor(Wait.forHealthcheck());
    }

    /**
     * Gets the mapped port for the block node gRPC server.
     *
     * @return the host port mapped to the container's internal port
     */
    public int getGrpcPort() {
        return getMappedPort(INTERNAL_PORT);
    }
}
