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

package com.swirlds.platform.wiring.components;

import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.network.connectivity.ConnectionServer;
import com.swirlds.platform.wiring.NoInput;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.Socket;

/**
 * Wiring for Connection server.
 *
 * @param connectionServerInputWire the input wire for connection server
 * @param clientSocketOutputWire the output wire for connected client sockets
 */
public record ConnectionServerWiring(
        @NonNull InputWire<NoInput> connectionServerInputWire, @NonNull OutputWire<Socket> clientSocketOutputWire) {

    /**
     * Create a new instance of {@link ConnectionServerWiring}.
     *
     * @param model the wiring model
     * @return the new instance
     */
    @NonNull
    public static ConnectionServerWiring create(@NonNull final WiringModel model) {
        final TaskScheduler<Socket> scheduler = model.schedulerBuilder("connectionServer")
                .withType(TaskSchedulerType.CONCURRENT)
                .build()
                .cast();

        return new ConnectionServerWiring(scheduler.buildInputWire("connectionServerInput"), scheduler.getOutputWire());
    }

    /**
     * Bind a connection server to this wiring.
     *
     * @param connectionServer the connection server instance to bind
     */
    public void bind(@NonNull final ConnectionServer connectionServer) {
        ((BindableInputWire<NoInput, Socket>) connectionServerInputWire).bind(connectionServer::listen);
    }
}
