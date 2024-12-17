/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.wiring.wires.output.internal;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;

import com.swirlds.common.wiring.model.TraceableWiringModel;
import com.swirlds.common.wiring.wires.SolderType;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * An output wire that transforms data that flows across it. For advanced use cases where
 * {@link OutputWire#buildTransformer(String, String, Function)} semantics are insufficient.
 *
 * @param <IN>  the type of data passed to the forwarding method
 * @param <OUT> the type of data forwarded to things soldered to this wire
 */
public class TransformingOutputWire<IN, OUT> extends ForwardingOutputWire<IN, OUT> {

    private static final Logger logger = LogManager.getLogger(TransformingOutputWire.class);
    private final List<Consumer<OUT>> forwardingDestinations = new ArrayList<>();

    private final Function<IN, OUT> transform;
    private final Consumer<IN> inputCleanup;
    private final Consumer<OUT> outputCleanup;

    /**
     * Constructor.
     *
     * @param model         the wiring model containing this output wire
     * @param name          the name of the output wire
     * @param transformer   the function to transform the data from the input type to the output type. Is called once
     *                      per output per data item. If this method returns null then the data is not forwarded.
     * @param inputCleanup  an optional method that is called on input data after the data is forwarded to all
     *                      destinations. The original data is passed to this method. Ignored if null.
     * @param outputCleanup an optional method that is called on output data if it is rejected by a destination. This is
     *                      possible if offer soldering is used and the destination declines to take the data.
     */
    public TransformingOutputWire(
            @NonNull final TraceableWiringModel model,
            @NonNull final String name,
            @NonNull final Function<IN, OUT> transformer,
            @Nullable final Consumer<IN> inputCleanup,
            @Nullable final Consumer<OUT> outputCleanup) {
        super(model, name);

        this.transform = Objects.requireNonNull(transformer);
        this.inputCleanup = inputCleanup == null ? (data) -> {} : inputCleanup;
        this.outputCleanup = outputCleanup == null ? (data) -> {} : outputCleanup;
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
    public void forward(@NonNull final IN data) {
        for (final Consumer<OUT> destination : forwardingDestinations) {
            try {
                final OUT transformed = transform.apply(data);
                if (transformed == null) {
                    // Do not forward null values.
                    return;
                }
                destination.accept(transformed);
            } catch (final Exception e) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "Exception thrown on output wire {} while forwarding data {}",
                        getName(),
                        data,
                        e);
            }
        }
        inputCleanup.accept(data);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void solderTo(@NonNull final InputWire<OUT> inputWire, @NonNull final SolderType solderType) {
        getModel().registerEdge(getName(), inputWire.getTaskSchedulerName(), inputWire.getName(), solderType);

        switch (solderType) {
            case PUT -> addForwardingDestination(inputWire::put);
            case INJECT -> addForwardingDestination(inputWire::inject);
            case OFFER -> addForwardingDestination(x -> {
                if (!inputWire.offer(x)) {
                    outputCleanup.accept(x);
                }
            });
            default -> throw new IllegalArgumentException("Unknown solder type: " + solderType);
        }
    }
}
