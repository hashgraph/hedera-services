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

import com.swirlds.common.wiring.component.internal.ComponentInputWire;
import com.swirlds.common.wiring.component.internal.WiringComponentProxy;
import com.swirlds.common.wiring.model.WiringModel;
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
import java.util.function.Consumer;

/**
 * Builds and manages input/output wires for a component.
 *
 * @param <COMPONENT_TYPE> the type of the component
 * @param <OUTPUT_TYPE>    the output type of the component
 */
public class ComponentWiring<COMPONENT_TYPE, OUTPUT_TYPE> {

    private TaskScheduler<OUTPUT_TYPE> scheduler;

    private final WiringComponentProxy proxy = new WiringComponentProxy();
    private final COMPONENT_TYPE proxyComponent;

    private COMPONENT_TYPE component;

    private final Map<Method, ComponentInputWire> inputWires = new HashMap<>();

    /**
     * Create a new component wiring.
     *
     * @param model     the wiring model
     * @param clazz     the class of the component
     * @param scheduler the task scheduler that will run the component
     */
    @SuppressWarnings("unchecked")
    public ComponentWiring(
            @NonNull final WiringModel model,
            @NonNull final Class<COMPONENT_TYPE> clazz,
            @NonNull final TaskScheduler<OUTPUT_TYPE> scheduler) {

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
            return (InputWire<INPUT_TYPE>) inputWires.get(method).wire();
        }

        final BindableInputWire<INPUT_TYPE, OUTPUT_TYPE> inputWire = scheduler.buildInputWire(method.getName());

        if (component == null) {
            // we will bind this later
            inputWires.put(
                    method,
                    new ComponentInputWire(
                            (BindableInputWire<Object, Object>) inputWire,
                            (BiFunction<Object, Object, Object>) handlerWithReturn,
                            (BiConsumer<Object, Object>) handlerWithoutReturn,
                            false));
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

            // TODO can we do this cleaner?
            inputWires.put(
                    method, new ComponentInputWire((BindableInputWire<Object, Object>) inputWire, null, null, true));
        }

        return inputWire;
    }

    // TODO this is necessary to support primitives. Remove this method once we agree to not support primitives.

    /**
     * Try calling the proxy function with different input types until we find one that works.
     *
     * @param consumer the function to call
     */
    @SuppressWarnings("unchecked")
    private void tryApply(@NonNull final Consumer<?> consumer) {

        // We don't know what kind of data the function is expecting. Brute force it
        // by checking all primitive types. If not a primitive, null will work.

        try {
            consumer.accept(null);
            return;
        } catch (final ClassCastException | NullPointerException ignored) {

        }

        try {
            ((Consumer<Byte>) consumer).accept((byte) 0);
            return;
        } catch (final ClassCastException | NullPointerException ignored) {

        }

        try {
            ((Consumer<Short>) consumer).accept((short) 0);
            return;
        } catch (final ClassCastException | NullPointerException ignored) {

        }

        try {
            ((Consumer<Character>) consumer).accept((char) 0);
            return;
        } catch (final ClassCastException | NullPointerException ignored) {

        }

        try {
            ((Consumer<Integer>) consumer).accept(0);
            return;
        } catch (final ClassCastException | NullPointerException ignored) {

        }

        try {
            ((Consumer<Long>) consumer).accept(0L);
            return;
        } catch (final ClassCastException | NullPointerException ignored) {

        }

        try {
            ((Consumer<Float>) consumer).accept(0.0f);
            return;
        } catch (final ClassCastException | NullPointerException ignored) {

        }

        try {
            ((Consumer<Double>) consumer).accept(0.0d);
            return;
        } catch (final ClassCastException | NullPointerException ignored) {

        }

        try {
            ((Consumer<Boolean>) consumer).accept(false);
            return;
        } catch (final ClassCastException | NullPointerException ignored) {

        }

        throw new IllegalStateException("Unsupported type");
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

        for (final ComponentInputWire wireToBeBound : inputWires.values()) {
            if (wireToBeBound.initiallyBound()) {
                continue;
            }

            if (wireToBeBound.handlerWithReturn() != null) {
                final BiFunction<Object, Object, Object> handlerWithReturn = wireToBeBound.handlerWithReturn();
                wireToBeBound.wire().bind(x -> {
                    return handlerWithReturn.apply(component, x);
                });
            } else {
                final BiConsumer<Object, Object> handlerWithoutReturn =
                        Objects.requireNonNull(wireToBeBound.handlerWithoutReturn());
                wireToBeBound.wire().bind(x -> {
                    handlerWithoutReturn.accept(component, x);
                });
            }
        }
    }
}
