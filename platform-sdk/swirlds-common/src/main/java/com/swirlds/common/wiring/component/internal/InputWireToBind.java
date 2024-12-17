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
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Contains information necessary to bind an input wire when we eventually get the implementation of the component.
 *
 * @param inputWire                               the input wire to bind
 * @param handlerWithReturn                       null if initially bound. If not initially bound, will be non-null
 *                                                if the method has a non-void return type
 * @param handlerWithoutReturn                    null if initially bound. If not initially bound, will be non-null
 *                                                if the method has a void return type
 * @param handlerWithoutParameter                 null if initially bound. If not initially bound, will be non-null
 *                                                if the method has no parameters
 * @param handlerWithoutReturnAndWithoutParameter null if initially bound. If not initially bound, will be non-null if
 *                                                the method has no parameters and a void return type
 * @param <COMPONENT_TYPE>                        the type of the component
 * @param <INPUT_TYPE>                            the input type of the input wire
 * @param <OUTPUT_TYPE>                           the output type of the component
 */
public record InputWireToBind<COMPONENT_TYPE, INPUT_TYPE, OUTPUT_TYPE>(
        @NonNull BindableInputWire<INPUT_TYPE, OUTPUT_TYPE> inputWire,
        @Nullable BiFunction<COMPONENT_TYPE, INPUT_TYPE, OUTPUT_TYPE> handlerWithReturn,
        @Nullable BiConsumer<COMPONENT_TYPE, INPUT_TYPE> handlerWithoutReturn,
        @Nullable Function<COMPONENT_TYPE, OUTPUT_TYPE> handlerWithoutParameter,
        @Nullable Consumer<COMPONENT_TYPE> handlerWithoutReturnAndWithoutParameter) {}
