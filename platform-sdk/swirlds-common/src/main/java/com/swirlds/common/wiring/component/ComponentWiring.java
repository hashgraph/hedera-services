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

package com.swirlds.common.wiring.component;

import com.swirlds.common.wiring.component.internal.FilterToBind;
import com.swirlds.common.wiring.component.internal.InputWireToBind;
import com.swirlds.common.wiring.component.internal.TransformerToBind;
import com.swirlds.common.wiring.component.internal.WiringComponentProxy;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.transformers.WireFilter;
import com.swirlds.common.wiring.transformers.WireTransformer;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Builds and manages input/output wires for a component.
 *
 * @param <COMPONENT_TYPE> the type of the component
 * @param <OUTPUT_TYPE>    the output type of the component
 */
@SuppressWarnings("unchecked")
public class ComponentWiring<COMPONENT_TYPE, OUTPUT_TYPE> {

    private final WiringModel model;
    private final TaskScheduler<OUTPUT_TYPE> scheduler;

    private final WiringComponentProxy proxy = new WiringComponentProxy();
    private final COMPONENT_TYPE proxyComponent;

    /**
     * The component that implements the business logic. Will be null until {@link #bind(Object)} is called.
     */
    private COMPONENT_TYPE component;

    /**
     * Input wires that have been created for this component.
     */
    private final Map<Method, BindableInputWire<Object, Object>> inputWires = new HashMap<>();

    /**
     * Input wires that need to be bound.
     */
    private final List<InputWireToBind<COMPONENT_TYPE, Object, OUTPUT_TYPE>> inputsToBind = new ArrayList<>();

    /**
     * Previously created transformers/splitters/filters.
     */
    private final Map<Method, OutputWire<?>> alternateOutputs = new HashMap<>();

    /**
     * Transformers that need to be bound.
     */
    private final List<TransformerToBind<COMPONENT_TYPE, Object, Object>> transformersToBind = new ArrayList<>();

    /**
     * Filters that need to be bound.
     */
    private final List<FilterToBind<COMPONENT_TYPE, Object>> filtersToBind = new ArrayList<>();

    /**
     * A splitter (if one has been constructed).
     */
    private OutputWire<Object> splitterOutput;

    /**
     * Create a new component wiring.
     *
     * @param model     the wiring model that will contain the component
     * @param clazz     the interface class of the component
     * @param scheduler the task scheduler that will run the component
     */
    public ComponentWiring(
            @NonNull final WiringModel model,
            @NonNull final Class<COMPONENT_TYPE> clazz,
            @NonNull final TaskScheduler<OUTPUT_TYPE> scheduler) {

        this.model = Objects.requireNonNull(model);
        this.scheduler = Objects.requireNonNull(scheduler);

        if (!clazz.isInterface()) {
            throw new IllegalArgumentException("Component class " + clazz.getName() + " is not an interface.");
        }

        proxyComponent = (COMPONENT_TYPE) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[] {clazz}, proxy);
    }

    /**
     * Get the output wire of this component.
     *
     * @return the output wire
     */
    @NonNull
    public OutputWire<OUTPUT_TYPE> getOutputWire() {
        return scheduler.getOutputWire();
    }

    /**
     * Get an input wire for this component.
     *
     * @param handler      the component method that will handle the input, e.g. "MyComponent::handleInput". Should be a
     *                     method on the class, not a method on a specific instance.
     * @param <INPUT_TYPE> the type of the input
     * @return the input wire
     */
    public <INPUT_TYPE> InputWire<INPUT_TYPE> getInputWire(
            @NonNull final BiFunction<COMPONENT_TYPE, INPUT_TYPE, OUTPUT_TYPE> handler) {

        Objects.requireNonNull(handler);

        try {
            handler.apply(proxyComponent, null);
        } catch (final NullPointerException e) {
            throw new IllegalStateException(
                    "Component wiring does not support primitive input types or return types. Use a boxed primitive "
                            + "instead.",
                    e);
        }

        return getOrBuildInputWire(proxy.getMostRecentlyInvokedMethod(), handler, null, null, null);
    }

    /**
     * Get an input wire for this component.
     *
     * @param handler      the component method that will handle the input, e.g. "MyComponent::handleInput". Should be a
     *                     method on the class, not a method on a specific instance.
     * @param <INPUT_TYPE> the input type
     * @return the input wire
     */
    public <INPUT_TYPE> InputWire<INPUT_TYPE> getInputWire(
            @NonNull final BiConsumer<COMPONENT_TYPE, INPUT_TYPE> handler) {

        Objects.requireNonNull(handler);

        try {
            handler.accept(proxyComponent, null);
        } catch (final NullPointerException e) {
            throw new IllegalStateException(
                    "Component wiring does not support primitive input types. Use a boxed primitive instead.", e);
        }

        return getOrBuildInputWire(proxy.getMostRecentlyInvokedMethod(), null, handler, null, null);
    }

    /**
     * Get an input wire for this component.
     *
     * @param handler      the component method that will handle the input, e.g. "MyComponent::handleInput". Should be a
     *                     method on the class, not a method on a specific instance.
     * @param <INPUT_TYPE> the input type
     * @return the input wire
     */
    @NonNull
    public <INPUT_TYPE> InputWire<INPUT_TYPE> getInputWire(
            @NonNull final Function<COMPONENT_TYPE, OUTPUT_TYPE> handler) {
        Objects.requireNonNull(handler);

        try {
            handler.apply(proxyComponent);
        } catch (final NullPointerException e) {
            throw new IllegalStateException(
                    "Component wiring does not support primitive input types. Use a boxed primitive instead.", e);
        }

        return getOrBuildInputWire(proxy.getMostRecentlyInvokedMethod(), null, null, handler, null);
    }

    /**
     * Get an input wire for this component.
     *
     * @param handler      the component method that will handle the input, e.g. "MyComponent::handleInput". Should be a
     *                     method on the class, not a method on a specific instance.
     * @param <INPUT_TYPE> the input type
     * @return the input wire
     */
    @NonNull
    public <INPUT_TYPE> InputWire<INPUT_TYPE> getInputWire(@NonNull final Consumer<COMPONENT_TYPE> handler) {
        Objects.requireNonNull(handler);

        try {
            handler.accept(proxyComponent);
        } catch (final NullPointerException e) {
            throw new IllegalStateException(
                    "Component wiring does not support primitive input types. Use a boxed primitive instead.", e);
        }

        return getOrBuildInputWire(proxy.getMostRecentlyInvokedMethod(), null, null, null, handler);
    }

    /**
     * Get the output wire of this component, transformed by a function.
     *
     * @param transformation     the function that will transform the output, must be a static method on the component
     * @param <TRANSFORMED_TYPE> the type of the transformed output
     * @return the transformed output wire
     */
    @NonNull
    public <TRANSFORMED_TYPE> OutputWire<TRANSFORMED_TYPE> getTransformedOutput(
            @NonNull final BiFunction<COMPONENT_TYPE, OUTPUT_TYPE, TRANSFORMED_TYPE> transformation) {

        return getOrBuildTransformer(transformation, getOutputWire());
    }

    /**
     * Get the output wire of a splitter of this component, transformed by a function. Automatically constructs the
     * splitter if it does not already exist. Intended for use only with components that produce lists of items.
     *
     * @param transformation the function that will transform the output, must be a static method on the component
     * @param <ELEMENT>      the type of the elements in the list, the base type of this component's output is expected
     *                       to be a list of this type
     */
    public <ELEMENT, TRANSFORMED_TYPE> OutputWire<TRANSFORMED_TYPE> getSplitAndTransformedOutput(
            @NonNull final BiFunction<COMPONENT_TYPE, ELEMENT, TRANSFORMED_TYPE> transformation) {
        return getOrBuildTransformer(transformation, getSplitOutput());
    }

    /**
     * Create a filter for the output of this component.
     *
     * @param predicate the filter predicate
     * @return the output wire of the filter
     */
    @NonNull
    public OutputWire<OUTPUT_TYPE> getFilteredOutput(
            @NonNull final BiFunction<COMPONENT_TYPE, OUTPUT_TYPE, Boolean> predicate) {
        return getOrBuildFilter(predicate, getOutputWire());
    }

    /**
     * Create a filter for the output of a splitter of this component. Automatically constructs the splitter if it does
     * not already exist. Intended for use only with components that produce lists of items.
     *
     * @param predicate the filter predicate
     * @param <ELEMENT> the type of the elements in the list, the base type of this component's output is expected to be
     *                  a list of this type
     * @return the output wire of the filter
     */
    @NonNull
    public <ELEMENT> OutputWire<ELEMENT> getSplitAndFilteredOutput(
            @NonNull final BiFunction<COMPONENT_TYPE, ELEMENT, Boolean> predicate) {

        return getOrBuildFilter(predicate, getSplitOutput());
    }

    /**
     * Create a splitter for the output of this component. A splitter converts an output wire that produces lists of
     * items into an output wire that produces individual items. Note that calling this method on a component that does
     * not produce lists will result in a runtime exception.
     *
     * @param <ELEMENT> the type of the elements in the list, the base type of this component's output is expected to be
     *                  a list of this type
     * @return the output wire
     */
    @NonNull
    public <ELEMENT> OutputWire<ELEMENT> getSplitOutput() {
        if (splitterOutput == null) {

            // Future work: there is not a clean way to specify the "splitterInputName" label, so as a short
            // term work around we can just call it "data". This is ugly but ok as a temporary place holder.
            // The proper way to fix this is to change the way we assign labels to wires in the diagram.
            // Instead of defining names for input wires, we should instead define names for output wires,
            // and require that any scheduler that has output define the label for its output data.

            splitterOutput = getOutputWire().buildSplitter(scheduler.getName() + "Splitter", "data");
        }
        return (OutputWire<ELEMENT>) splitterOutput;
    }

    /**
     * Create a transformed output wire or return the existing one if it has already been created.
     *
     * @param transformation     the function that will transform the output, must be a static method on the component
     * @param transformerSource  the source of the data to transform (i.e. the base output wire or the output wire of
     *                           the splitter)
     * @param <ELEMENT>          the type of the elements passed to the transformer
     * @param <TRANSFORMED_TYPE> the type of the transformed output
     * @return the transformed output wire
     */
    @NonNull
    private <ELEMENT, TRANSFORMED_TYPE> OutputWire<TRANSFORMED_TYPE> getOrBuildTransformer(
            @NonNull final BiFunction<COMPONENT_TYPE, ELEMENT, TRANSFORMED_TYPE> transformation,
            @NonNull final OutputWire<ELEMENT> transformerSource) {

        Objects.requireNonNull(transformation);
        try {
            transformation.apply(proxyComponent, null);
        } catch (final NullPointerException e) {
            throw new IllegalStateException(
                    "Component wiring does not support primitive input types or return types. "
                            + "Use a boxed primitive instead.",
                    e);
        }

        final Method method = proxy.getMostRecentlyInvokedMethod();
        if (!method.isDefault()) {
            throw new IllegalArgumentException("Method " + method.getName() + " does not have a default.");
        }

        if (alternateOutputs.containsKey(method)) {
            // We've already created this transformer.
            return (OutputWire<TRANSFORMED_TYPE>) alternateOutputs.get(method);
        }

        final String wireLabel;
        final InputWireLabel inputWireLabel = method.getAnnotation(InputWireLabel.class);
        if (inputWireLabel == null) {
            wireLabel = "data to transform";
        } else {
            wireLabel = inputWireLabel.value();
        }

        final String schedulerLabel;
        final SchedulerLabel schedulerLabelAnnotation = method.getAnnotation(SchedulerLabel.class);
        if (schedulerLabelAnnotation == null) {
            schedulerLabel = method.getName();
        } else {
            schedulerLabel = schedulerLabelAnnotation.value();
        }

        final WireTransformer<ELEMENT, TRANSFORMED_TYPE> transformer =
                new WireTransformer<>(model, schedulerLabel, wireLabel);
        transformerSource.solderTo(transformer.getInputWire());
        alternateOutputs.put(method, transformer.getOutputWire());

        if (component == null) {
            // we will bind this later
            transformersToBind.add((TransformerToBind<COMPONENT_TYPE, Object, Object>)
                    new TransformerToBind<>(transformer, transformation));
        } else {
            // bind this now
            transformer.bind(x -> transformation.apply(component, x));
        }

        return transformer.getOutputWire();
    }

    /**
     * Create a filtered output wire or return the existing one if it has already been created.
     *
     * @param predicate    the filter predicate
     * @param filterSource the source of the data to filter (i.e. the base output wire or the output wire of the
     *                     splitter)
     * @param <ELEMENT>    the type of the elements passed to the filter
     * @return the output wire of the filter
     */
    private <ELEMENT> OutputWire<ELEMENT> getOrBuildFilter(
            @NonNull final BiFunction<COMPONENT_TYPE, ELEMENT, Boolean> predicate,
            @NonNull final OutputWire<ELEMENT> filterSource) {

        Objects.requireNonNull(predicate);
        try {
            predicate.apply(proxyComponent, null);
        } catch (final NullPointerException e) {
            throw new IllegalStateException(
                    "Component wiring does not support primitive input types or return types. "
                            + "Use a boxed primitive instead.",
                    e);
        }

        final Method method = proxy.getMostRecentlyInvokedMethod();
        if (!method.isDefault()) {
            throw new IllegalArgumentException("Method " + method.getName() + " does not have a default.");
        }

        if (alternateOutputs.containsKey(method)) {
            // We've already created this filter.
            return (OutputWire<ELEMENT>) alternateOutputs.get(method);
        }

        final String wireLabel;
        final InputWireLabel inputWireLabel = method.getAnnotation(InputWireLabel.class);
        if (inputWireLabel == null) {
            wireLabel = "data to filter";
        } else {
            wireLabel = inputWireLabel.value();
        }

        final String schedulerLabel;
        final SchedulerLabel schedulerLabelAnnotation = method.getAnnotation(SchedulerLabel.class);
        if (schedulerLabelAnnotation == null) {
            schedulerLabel = method.getName();
        } else {
            schedulerLabel = schedulerLabelAnnotation.value();
        }

        final WireFilter<ELEMENT> filter = new WireFilter<>(model, schedulerLabel, wireLabel);
        filterSource.solderTo(filter.getInputWire());
        alternateOutputs.put(method, filter.getOutputWire());

        if (component == null) {
            // we will bind this later
            filtersToBind.add((FilterToBind<COMPONENT_TYPE, Object>) new FilterToBind<>(filter, predicate));
        } else {
            // bind this now
            filter.bind(x -> predicate.apply(component, x));
        }

        return filter.getOutputWire();
    }

    /**
     * Get the input wire for a specified method.
     *
     * @param method                                  the method that will handle data on the input wire
     * @param handlerWithReturn                       the handler for the method if it has a return type
     * @param handlerWithoutReturn                    the handler for the method if it does not have a return type
     * @param handlerWithoutParameter                 the handler for the method if it does not have a parameter
     * @param handlerWithoutReturnAndWithoutParameter the handler for the method if it does not have a return type and
     *                                                does not have a parameter
     * @param <INPUT_TYPE>                            the input type
     * @return the input wire
     */
    private <INPUT_TYPE> InputWire<INPUT_TYPE> getOrBuildInputWire(
            @NonNull final Method method,
            @Nullable final BiFunction<COMPONENT_TYPE, INPUT_TYPE, OUTPUT_TYPE> handlerWithReturn,
            @Nullable final BiConsumer<COMPONENT_TYPE, INPUT_TYPE> handlerWithoutReturn,
            @Nullable final Function<COMPONENT_TYPE, OUTPUT_TYPE> handlerWithoutParameter,
            @Nullable final Consumer<COMPONENT_TYPE> handlerWithoutReturnAndWithoutParameter) {

        if (inputWires.containsKey(method)) {
            // We've already created this wire
            return (InputWire<INPUT_TYPE>) inputWires.get(method);
        }

        final String label;
        final InputWireLabel inputWireLabel = method.getAnnotation(InputWireLabel.class);
        if (inputWireLabel == null) {
            label = method.getName();
        } else {
            label = inputWireLabel.value();
        }

        final BindableInputWire<INPUT_TYPE, OUTPUT_TYPE> inputWire = scheduler.buildInputWire(label);
        inputWires.put(method, (BindableInputWire<Object, Object>) inputWire);

        if (component == null) {
            // we will bind this later
            inputsToBind.add((InputWireToBind<COMPONENT_TYPE, Object, OUTPUT_TYPE>) new InputWireToBind<>(
                    inputWire,
                    handlerWithReturn,
                    handlerWithoutReturn,
                    handlerWithoutParameter,
                    handlerWithoutReturnAndWithoutParameter));
        } else {
            // bind this now
            if (handlerWithReturn != null) {
                inputWire.bind(x -> handlerWithReturn.apply(component, x));
            } else if (handlerWithoutReturn != null) {
                inputWire.bindConsumer(x -> {
                    handlerWithoutReturn.accept(component, x);
                });
            } else if (handlerWithoutParameter != null) {
                inputWire.bind(x -> handlerWithoutParameter.apply(component));
            } else {
                assert handlerWithoutReturnAndWithoutParameter != null;
                inputWire.bindConsumer(x -> {
                    handlerWithoutReturnAndWithoutParameter.accept(component);
                });
            }
        }

        return inputWire;
    }

    /**
     * Flush all data in the task scheduler. Blocks until all data currently in flight has been processed.
     *
     * @throws UnsupportedOperationException if the scheduler does not support flushing
     */
    public void flush() {
        scheduler.flush();
    }

    /**
     * Start squelching the output of this component.
     *
     * @throws UnsupportedOperationException if the scheduler does not support squelching
     * @throws IllegalStateException         if the scheduler is already squelching
     */
    public void startSquelching() {
        scheduler.startSquelching();
    }

    /**
     * Stop squelching the output of this component.
     *
     * @throws UnsupportedOperationException if the scheduler does not support squelching
     * @throws IllegalStateException         if the scheduler is not squelching
     */
    public void stopSquelching() {
        scheduler.stopSquelching();
    }

    /**
     * Bind the component to the input wires.
     *
     * @param component the component to bind
     */
    public void bind(@NonNull final COMPONENT_TYPE component) {
        Objects.requireNonNull(component);

        this.component = component;

        // Bind input wires
        for (final InputWireToBind<COMPONENT_TYPE, ?, OUTPUT_TYPE> wireToBind : inputsToBind) {
            if (wireToBind.handlerWithReturn() != null) {
                final BiFunction<COMPONENT_TYPE, Object, OUTPUT_TYPE> handlerWithReturn =
                        (BiFunction<COMPONENT_TYPE, Object, OUTPUT_TYPE>) wireToBind.handlerWithReturn();
                wireToBind.inputWire().bind(x -> handlerWithReturn.apply(component, x));
            } else if (wireToBind.handlerWithoutReturn() != null) {
                final BiConsumer<COMPONENT_TYPE, Object> handlerWithoutReturn =
                        (BiConsumer<COMPONENT_TYPE, Object>) wireToBind.handlerWithoutReturn();
                wireToBind.inputWire().bindConsumer(x -> {
                    handlerWithoutReturn.accept(component, x);
                });
            } else if (wireToBind.handlerWithoutParameter() != null) {
                wireToBind
                        .inputWire()
                        .bind(x -> wireToBind.handlerWithoutParameter().apply(component));
            } else {
                assert wireToBind.handlerWithoutReturnAndWithoutParameter() != null;
                wireToBind.inputWire().bindConsumer(x -> {
                    wireToBind.handlerWithoutReturnAndWithoutParameter().accept(component);
                });
            }
        }

        // Bind transformers
        for (final TransformerToBind<COMPONENT_TYPE, Object, Object> transformerToBind : transformersToBind) {
            final WireTransformer<Object, Object> transformer = transformerToBind.transformer();
            final BiFunction<COMPONENT_TYPE, Object, Object> transformation = transformerToBind.transformation();
            transformer.bind(x -> transformation.apply(component, x));
        }

        // Bind filters
        for (final FilterToBind<COMPONENT_TYPE, Object> filterToBind : filtersToBind) {
            filterToBind.filter().bind(x -> filterToBind.predicate().apply(component, x));
        }
    }
}
