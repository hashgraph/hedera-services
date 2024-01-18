/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.grpc;

import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
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
    private final InitTrigger initTrigger;
    private final GrpcServerManager grpc;
    private final NodeLocalProperties nodeLocalProperties;
    private final Optional<PrintStream> console;
    private final Supplier<AddressBook> addressBook;

    @Inject
    public GrpcStarter(
            final NodeId nodeId,
            @NonNull final InitTrigger initTrigger,
            final GrpcServerManager grpc,
            final NodeLocalProperties nodeLocalProperties,
            final Supplier<AddressBook> addressBook,
            final Optional<PrintStream> console) {
        this.nodeId = nodeId;
        this.initTrigger = requireNonNull(initTrigger);
        this.console = console;
        this.grpc = grpc;
        this.addressBook = addressBook;
        this.nodeLocalProperties = nodeLocalProperties;
    }

    public void startIfAppropriate() {
        // Never any reason to start Netty during event stream recovery
        if (initTrigger == EVENT_STREAM_RECOVERY) {
            return;
        }
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
                    final var nodeAddress = staticBook.getAddress(nodeId);
                    int portOffset = thisNodeIsDefaultListener() ? 0 : nodeAddress.getPortExternal() % PORT_MODULUS;
                    grpc.start(port + portOffset, tlsPort + portOffset, this::logInfoWithConsoleEcho);
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
        return blessedNodeAccount.equals(staticBook.getAddress(nodeId).getMemo());
    }
}
