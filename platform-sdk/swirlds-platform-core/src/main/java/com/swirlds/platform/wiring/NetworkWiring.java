/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.wiring;

import com.swirlds.base.state.Startable;
import com.swirlds.base.state.Stoppable;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.utility.Clearable;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.platform.network.connectivity.ConnectionServer;
import com.swirlds.platform.network.connectivity.InboundConnectionHandler;
import com.swirlds.platform.wiring.components.ConnectionServerWiring;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.ForkJoinPool;

/**
 * Wirings for network components
 */
public class NetworkWiring implements Startable, Stoppable, Clearable {
    private final WiringModel model;
    private final ConnectionServerWiring connectionServerWiring;

    private ConnectionServer connectionServer;

    public NetworkWiring(@NonNull final PlatformContext platformContext) {
        model = WiringModel.create(platformContext, platformContext.getTime(), new ForkJoinPool());
        connectionServerWiring = ConnectionServerWiring.create(model);
    }

    /**
     * Bind network components to their wiring.
     *
     * @param connectionServer the connection server to bind to
     */
    public void bind(@NonNull final ConnectionServer connectionServer) {
        connectionServerWiring.bind(connectionServer);
        this.connectionServer = connectionServer;
    }

    /**
     * Wire network components that adhere to the framework to components that don't
     * <p>
     *
     * @param inboundConnectionHandler the inbound connection handler to wire
     */
    public void bindExternalComponents(final InboundConnectionHandler inboundConnectionHandler) {
        connectionServerWiring
                .clientSocketOutputWire()
                .solderTo("inboundConnectionHandler", "handle inbound connections", inboundConnectionHandler::handle);
    }

    /**
     * Get the wiring model.
     *
     * @return the wiring model
     */
    @NonNull
    public WiringModel getModel() {
        return model;
    }

    public InputWire<NoInput> getConnectionServerInputWire() {
        return connectionServerWiring.connectionServerInputWire();
    }

    @Override
    public void start() {
        model.start();
    }

    @Override
    public void stop() {
        model.stop();
        if (connectionServer != null) {
            connectionServer.stop();
        }
    }

    @Override
    public void clear() {
        // connectionServerWiring doesn't need to clear anything just yet.
    }
}
