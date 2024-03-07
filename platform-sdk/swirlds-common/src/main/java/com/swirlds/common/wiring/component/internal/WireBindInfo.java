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

import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Contains information necessary to bind an input wire when we eventually get the implementation of the component.
 *
 * @param handlerWithReturn    null if initially bound. If not initially bound, will be non-null if the method has a
 *                             non-void return type.
 * @param handlerWithoutReturn null if initially bound. If not initially bound, will be non-null if the method has a
 *                             void return type
 */
public record WireBindInfo(
        @Nullable BiFunction<Object, Object, Object> handlerWithReturn,
        @Nullable BiConsumer<Object, Object> handlerWithoutReturn) {}
