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

import com.swirlds.common.wiring.component.internal.WireBindInfo;
import com.swirlds.common.wiring.component.internal.WiringComponentProxy;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Builds and manages input/output wires for a component.
 *
 * @param <COMPONENT_TYPE> the type of the component
 * @param <OUTPUT_TYPE>    the output type of the component
 */
public class ComponentWiring<COMPONENT_TYPE, OUTPUT_TYPE> {

    private final TaskScheduler<OUTPUT_TYPE> scheduler;

    private final WiringComponentProxy proxy = new WiringComponentProxy();
    private final COMPONENT_TYPE proxyComponent;

    private COMPONENT_TYPE component;

    private final Map<Method, WireBindInfo> bindInfo = new HashMap<>();
    private final Map<Method, BindableInputWire<Object, Object>> inputWires = new HashMap<>();

    /**
     * Create a new component wiring.
     *
     * @param clazz     the class of the component
     * @param scheduler the task scheduler that will run the component
     */
    @SuppressWarnings("unchecked")
    public ComponentWiring(
            @NonNull final Class<COMPONENT_TYPE> clazz, @NonNull final TaskScheduler<OUTPUT_TYPE> scheduler) {

        this.scheduler = Objects.requireNonNull(scheduler);

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
                    "Component wiring does not support primitive input types or return types. Use a boxed primitive instead.");
        }

        return getOrBuildInputWire(proxy.getMostRecentlyInvokedMethod(), handler, null);
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
                    "Component wiring does not support primitive input types. Use a boxed primitive instead.");
        }

        return getOrBuildInputWire(proxy.getMostRecentlyInvokedMethod(), null, handler);
    }

    /**
     * Get the input wire for a specified method.
     *
     * @param method               the method that will handle data on the input wire
     * @param handlerWithReturn    the handler for the method if it has a return type
     * @param handlerWithoutReturn the handler for the method if it does not have a return type
     * @param <INPUT_TYPE>         the input type
     * @return the input wire
     */
    @SuppressWarnings("unchecked")
    private <INPUT_TYPE> InputWire<INPUT_TYPE> getOrBuildInputWire(
            @NonNull final Method method,
            @Nullable final BiFunction<COMPONENT_TYPE, INPUT_TYPE, OUTPUT_TYPE> handlerWithReturn,
            @Nullable final BiConsumer<COMPONENT_TYPE, INPUT_TYPE> handlerWithoutReturn) {

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
            bindInfo.put(method, new WireBindInfo((BiFunction<Object, Object, Object>) handlerWithReturn, (BiConsumer<
                            Object, Object>)
                    handlerWithoutReturn));
        } else {
            // bind this now
            if (handlerWithReturn != null) {
                inputWire.bind(x -> {
                    return handlerWithReturn.apply(component, x);
                });
            } else {
                inputWire.bind(x -> {
                    assert handlerWithoutReturn != null;
                    handlerWithoutReturn.accept(component, x);
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

        for (final Map.Entry<Method, WireBindInfo> entry : bindInfo.entrySet()) {

            final Method method = entry.getKey();
            final WireBindInfo wireBindInfo = entry.getValue();
            final BindableInputWire<Object, Object> wire = inputWires.get(method);

            if (wireBindInfo.handlerWithReturn() != null) {
                final BiFunction<Object, Object, Object> handlerWithReturn = wireBindInfo.handlerWithReturn();
                wire.bind(x -> {
                    return handlerWithReturn.apply(component, x);
                });
            } else {
                final BiConsumer<Object, Object> handlerWithoutReturn =
                        Objects.requireNonNull(wireBindInfo.handlerWithoutReturn());
                wire.bind(x -> {
                    handlerWithoutReturn.accept(component, x);
                });
            }
        }
    }
}
