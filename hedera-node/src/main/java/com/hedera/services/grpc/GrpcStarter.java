/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.grpc;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import java.io.PrintStream;
import java.util.Optional;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class GrpcStarter {
    private static final Logger log = LogManager.getLogger(GrpcStarter.class);

    private static final int PORT_MODULUS = 1000;

    private final NodeId nodeId;
    private final GrpcServerManager grpc;
    private final NodeLocalProperties nodeLocalProperties;
    private final Optional<PrintStream> console;
    private final Supplier<AddressBook> addressBook;

    @Inject
    public GrpcStarter(
            final NodeId nodeId,
            final GrpcServerManager grpc,
            final NodeLocalProperties nodeLocalProperties,
            final Supplier<AddressBook> addressBook,
            final Optional<PrintStream> console) {
        this.nodeId = nodeId;
        this.console = console;
        this.grpc = grpc;
        this.addressBook = addressBook;
        this.nodeLocalProperties = nodeLocalProperties;
    }

    public void startIfAppropriate() {
        final var port = nodeLocalProperties.port();
        final var tlsPort = nodeLocalProperties.tlsPort();
        final var activeProfile = nodeLocalProperties.activeProfile();

        log.info("TLS is turned on by default on node {}", nodeId);
        log.info("Active profile: {}", activeProfile);

        switch (activeProfile) {
            case DEV:
                if (nodeLocalProperties.devOnlyDefaultNodeListens()) {
                    if (thisNodeIsDefaultListener()) {
                        grpc.start(port, tlsPort, this::logInfoWithConsoleEcho);
                    }
                } else {
                    final var staticBook = addressBook.get();
                    final var nodeAddress = staticBook.getAddress(nodeId.getId());
                    int portOffset =
                            thisNodeIsDefaultListener()
                                    ? 0
                                    : nodeAddress.getPortExternalIpv4() % PORT_MODULUS;
                    grpc.start(
                            port + portOffset, tlsPort + portOffset, this::logInfoWithConsoleEcho);
                }
                break;
            case TEST:
                log.warn("No Netty config for profile {}, skipping gRPC startup", activeProfile);
                break;
            case PROD:
                grpc.start(port, tlsPort, this::logInfoWithConsoleEcho);
                break;
        }
    }

    void logInfoWithConsoleEcho(String s) {
        log.info(s);
        console.ifPresent(c -> c.println(s));
    }

    private boolean thisNodeIsDefaultListener() {
        final var blessedNodeAccount = nodeLocalProperties.devListeningAccount();
        final var staticBook = addressBook.get();
        return blessedNodeAccount.equals(staticBook.getAddress(nodeId.getId()).getMemo());
    }
}
