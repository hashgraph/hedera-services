/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit;

/**
 * Defines a node in the network for running Hapi tests. There are implementations for running locally in this
 * JVM started by JUnit, and for running out of process.
 */
public interface HapiTestNode {
    /**
     * Starts the node software.
     */
    void start();

    /**
     * Waits for the node to become active.
     *
     * @param seconds the number of seconds to wait for the server to start before throwing an exception.
     */
    void waitForActive(long seconds);

    /**
     * Stops the node software gracefully
     */
    void stop();

    /**
     * Attempts to hard-terminate the node software
     */
    void terminate();
}
