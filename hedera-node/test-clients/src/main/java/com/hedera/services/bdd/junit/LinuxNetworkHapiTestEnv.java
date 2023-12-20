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

import static java.lang.String.format;

import edu.umd.cs.findbugs.annotations.NonNull;

public class LinuxNetworkHapiTestEnv extends HapiTestEnvBase {
    private static final IPAllocator ADAPTER_IP_ALLOC = nodeId -> format("10.191.%d.1", nodeId);

    private static final PortAllocator ADAPTER_GRPC_PORT_ALLOC = nodeId -> FIRST_GRPC_PORT + (nodeId * 2);

    private static final PortAllocator ADAPTER_GOSSIP_PORT_ALLOC = nodeId -> 50111;

    public LinuxNetworkHapiTestEnv(
            @NonNull final String testName, final boolean cluster, final boolean useInProcessAlice) {
        super(ADAPTER_IP_ALLOC, ADAPTER_GOSSIP_PORT_ALLOC, ADAPTER_GRPC_PORT_ALLOC);
        initialize(testName, cluster, useInProcessAlice);
    }

    @Override
    protected void setupNetwork(int nodeId, String nodeAddress, int gossipPort, int grpcPort) {
        final String iface = format("vx10%d", nodeId);
        executeWithElevation("ip", "link", "add", iface, "type", "dummy");
        executeWithElevation("ifconfig", iface, "hw", "ether", format("C8:D7:4A:4E:47:0%d", nodeId));
        executeWithElevation("ip", "addr", "add", format("%s/24", nodeAddress), "brd", "+", "dev", iface);
        executeWithElevation("ip", "link", "set", "dev", iface, "up");
    }

    @Override
    protected void teardownNetwork(int nodeId, String nodeAddress) {
        final String iface = format("vx10%d", nodeId);
        executeWithElevation("ip", "addr", "del", format("%s/24", nodeAddress), "brd", "+", "dev", iface);
        executeWithElevation("ip", "link", "delete", iface, "type", "dummy");
    }
}
