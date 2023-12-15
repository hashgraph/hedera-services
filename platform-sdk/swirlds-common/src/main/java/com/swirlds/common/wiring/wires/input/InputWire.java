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

package com.swirlds.common.wiring.wires.input;

import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.transformers.WireFilter;
import com.swirlds.common.wiring.transformers.WireListSplitter;
import com.swirlds.common.wiring.transformers.WireTransformer;
import com.swirlds.common.wiring.wires.SolderType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * An object that can insert work to be handled by a {@link TaskScheduler}.
 *
 * @param <IN> the type of data that passes into the wire
 */
public abstract class InputWire<IN> {

    private final WiringModel model;
    private final TaskSchedulerInput<?> taskSchedulerInput;
    private Consumer<Object> handler;
    private final String name;
    private final String taskSchedulerName;

    /**
     * Constructor.
     *
     * @param taskScheduler the scheduler to insert data into
     * @param name          the name of the input wire
     */
    protected InputWire(
            @NonNull final WiringModel model,
            @NonNull final TaskScheduler<?> taskScheduler,
            @NonNull final String name) {

        this.model = Objects.requireNonNull(model);
        this.taskSchedulerInput = Objects.requireNonNull(taskScheduler);
        this.name = Objects.requireNonNull(name);
        this.taskSchedulerName = taskScheduler.getName();
    }

    /**
     * Get the name of this input wire.
     *
     * @return the name of this input wire
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * Get the name of the task scheduler this input channel is bound to.
     *
     * @return the name of the wire this input channel is bound to
     */
    @NonNull
    public String getTaskSchedulerName() {
        return taskSchedulerName;
    }

    /**
     * Add a task to the task scheduler. May block if back pressure is enabled.
     *
     * @param data the data to be processed by the task scheduler
     */
    public void put(@NonNull final IN data) {
        taskSchedulerInput.put(handler, data);
    }

    /**
     * Add a task to the task scheduler. If backpressure is enabled and there is not immediately capacity available,
     * this method will not accept the data.
     *
     * @param data the data to be processed by the task scheduler
     * @return true if the data was accepted, false otherwise
     */
    public boolean offer(@NonNull final IN data) {
        return taskSchedulerInput.offer(handler, data);
    }

    /**
     * Inject data into the task scheduler, doing so even if it causes the number of unprocessed tasks to exceed the
     * capacity specified by configured back pressure. If backpressure is disabled, this operation is logically
     * equivalent to {@link #put(Object)}.
     *
     * @param data the data to be processed by the task scheduler
     */
    public void inject(@NonNull final IN data) {
        taskSchedulerInput.inject(handler, data);
    }

    /**
     * Build a filter for data being inserted into this input wire. Data that passes the filter will be forwarded to
     * this input wire. Solder type {@link SolderType#PUT} is used.
     *
     * @param filterName      the name of the filter
     * @param filterInputName the label for the input wire going into the filter
     * @param predicate       the predicate that determines whether data is forwarded, if this method returns true the
     *                        data is forwarded, if this method returns false the data is discarded.
     * @return the input wire for the filter
     */
    @NonNull
    public InputWire<IN> buildFilter(
            @NonNull final String filterName,
            @NonNull final String filterInputName,
            @NonNull final Predicate<IN> predicate) {

        return buildFilter(filterName, filterInputName, predicate, SolderType.PUT);
    }

    /**
     * Build a filter for data being inserted into this input wire. Data that passes the filter will be forwarded to
     * this input wire.
     *
     * @param filterName      the name of the filter
     * @param predicate       the predicate that determines whether data is forwarded, if this method returns true the
     *                        data is forwarded, if this method returns false the data is discarded.
     * @param filterInputName the label for the input wire going into the filter
     * @param solderType      the type of soldering to use when connecting the filter to this input wire
     * @return the input wire for the filter
     */
    @NonNull
    public InputWire<IN> buildFilter(
            @NonNull final String filterName,
            @NonNull final String filterInputName,
            @NonNull final Predicate<IN> predicate,
            @NonNull final SolderType solderType) {

        Objects.requireNonNull(filterName);
        Objects.requireNonNull(filterInputName);
        Objects.requireNonNull(predicate);
        Objects.requireNonNull(solderType);

        final WireFilter<IN> filter = new WireFilter<>(model, filterName, filterInputName, predicate);
        filter.getOutputWire().solderTo(this, solderType);
        return filter.getInputWire();
    }

    /**
     * Build a splitter for data being inserted into this input wire. Data passed to the input returned by this method
     * will be split and then passed to this input wire. Solder type {@link SolderType#PUT} is used.
     *
     * @param splitterName      the name of the splitter
     * @param splitterInputName the label for the input wire going into the splitter
     * @return the input wire for the splitter
     */
    @NonNull
    public InputWire<List<IN>> buildSplitter(
            @NonNull final String splitterName, @NonNull final String splitterInputName) {
        return buildSplitter(splitterName, splitterInputName, SolderType.PUT);
    }

    /**
     * Build a splitter for data being inserted into this input wire. Data passed to the input returned by this method
     * will be split and then passed to this input wire.
     *
     * @param splitterName      the name of the splitter
     * @param splitterInputName the label for the input wire going into the splitter
     * @param solderType        the type of soldering to use when connecting the splitter to this input wire
     * @return the input wire for the splitter
     */
    @NonNull
    public InputWire<List<IN>> buildSplitter(
            @NonNull final String splitterName,
            @NonNull final String splitterInputName,
            @NonNull final SolderType solderType) {

        Objects.requireNonNull(splitterName);
        Objects.requireNonNull(splitterInputName);
        Objects.requireNonNull(solderType);

        final WireListSplitter<IN> splitter = new WireListSplitter<>(model, splitterInputName, splitterName);
        splitter.getOutputWire().solderTo(this, solderType);
        return splitter.getInputWire();
    }

    /**
     * Build a transformer for data being inserted into this input wire. Data passed to the input returned by this
     * method will be transformed and then passed to this input wire. Solder type {@link SolderType#PUT} is used.
     *
     * @param transformerName      the name of the transformer
     * @param transformerInputName the label for the input wire going into the transformer
     * @param transformer          the transformer that will transform data
     * @param <NEW_IN>             the type of data that passes into the transformer
     * @return the input wire for the transformer
     */
    public <NEW_IN> InputWire<NEW_IN> buildTransformer(
            @NonNull final String transformerName,
            @NonNull final String transformerInputName,
            @NonNull final Function<NEW_IN, IN> transformer) {
        return buildTransformer(transformerName, transformerInputName, transformer, SolderType.PUT);
    }

    /**
     * Build a transformer for data being inserted into this input wire. Data passed to the input returned by this
     * method will be transformed and then passed to this input wire. Solder type {@link SolderType#PUT} is used.
     *
     * @param transformerName      the name of the transformer
     * @param transformerInputName the label for the input wire going into the transformer
     * @param transformer          the transformer that will transform data
     * @param solderType           the type of soldering to use when connecting the transformer to this input wire
     * @param <NEW_IN>             the type of data that passes into the transformer
     * @return the input wire for the transformer
     */
    @NonNull
    public <NEW_IN> InputWire<NEW_IN> buildTransformer(
            @NonNull final String transformerName,
            @NonNull final String transformerInputName,
            @NonNull final Function<NEW_IN, IN> transformer,
            @NonNull final SolderType solderType) {

        Objects.requireNonNull(transformerName);
        Objects.requireNonNull(transformerInputName);
        Objects.requireNonNull(transformer);
        Objects.requireNonNull(solderType);

        final WireTransformer<NEW_IN, IN> wireTransformer =
                new WireTransformer<>(model, transformerName, transformerInputName, transformer);
        wireTransformer.getOutputWire().solderTo(this, solderType);
        return wireTransformer.getInputWire();
    }

    /**
     * Set the method that will handle data traveling over this wire.
     *
     * @param handler the method that will handle data traveling over this wire
     */
    protected void setHandler(@NonNull final Consumer<Object> handler) {
        if (this.handler != null) {
            throw new IllegalStateException("Handler already bound");
        }
        this.handler = Objects.requireNonNull(handler);
    }
}
