/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.wires.output;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.component.framework.model.TraceableWiringModel;
import com.swirlds.component.framework.wires.output.internal.ForwardingOutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An output wire that will take data and forward it to its outputs. Output type is the same as the input type.
 *
 * @param <OUT> the type of data passed to the forwarding method
 */
public class StandardOutputWire<OUT> extends ForwardingOutputWire<OUT, OUT> {

    private static final Logger logger = LogManager.getLogger(StandardOutputWire.class);

    private final List<Consumer<OUT>> forwardingDestinations = new ArrayList<>();

    /**
     * Constructor.
     *
     * @param model the wiring model containing this output wire
     * @param name  the name of the output wire
     */
    public StandardOutputWire(@NonNull final TraceableWiringModel model, @NonNull final String name) {
        super(model, name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void addForwardingDestination(@NonNull final Consumer<OUT> destination) {
        Objects.requireNonNull(destination);
        forwardingDestinations.add(destination);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void forward(@NonNull final OUT data) {
        for (final Consumer<OUT> destination : forwardingDestinations) {
            try {
                destination.accept(data);
            } catch (final Exception e) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Exception thrown on output wire {} while forwarding data {}",
                        getName(),
                        data,
                        e);
            }
        }
    }
}
