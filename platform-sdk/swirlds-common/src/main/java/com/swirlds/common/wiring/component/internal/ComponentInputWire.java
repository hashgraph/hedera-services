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

package com.swirlds.common.wiring.component.internal;

import com.swirlds.common.wiring.wires.input.BindableInputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Information that describes a component's input wire
 *
 * @param wire                 the wire, may need to be bound
 * @param handlerWithReturn    null if initially bound. If not initially bound, will be non-null if the method has a
 *                             non-void return type.
 * @param handlerWithoutReturn null if initially bound. If not initially bound, will be non-null if the method has a
 *                             void return type
 * @param initiallyBound       true if the wire was bound when it was first created, false if it was initially created
 *                             without being bound
 */
public record ComponentInputWire(
        @NonNull BindableInputWire<Object, Object> wire,
        @Nullable BiFunction<Object, Object, Object> handlerWithReturn,
        @Nullable BiConsumer<Object, Object> handlerWithoutReturn,
        boolean initiallyBound) {}
