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

import com.swirlds.common.wiring.transformers.WireFilter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.BiFunction;

/**
 * A filter and the predicate to bind.
 *
 * @param filter           the filter we eventually want to bind
 * @param predicate        the predicate method
 * @param <COMPONENT_TYPE> the type of the component
 * @param <OUTPUT_TYPE>    the input type
 */
public record FilterToBind<COMPONENT_TYPE, OUTPUT_TYPE>(
        @NonNull WireFilter<OUTPUT_TYPE> filter, @NonNull BiFunction<COMPONENT_TYPE, OUTPUT_TYPE, Boolean> predicate) {}
