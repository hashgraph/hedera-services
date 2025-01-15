/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.component.framework.component.internal;

import com.swirlds.platform.component.framework.transformers.WireTransformer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.BiFunction;

/**
 * A transformer and the transformation to bind to a component.
 *
 * @param transformer      the transformer we eventually want to bind
 * @param transformation   the transformation method
 * @param <COMPONENT_TYPE> the type of the component
 * @param <INPUT_TYPE>     the input type of the transformer (equal to the output type of the base output wire)
 * @param <OUTPUT_TYPE>    the output type of the transformer
 */
public record TransformerToBind<COMPONENT_TYPE, INPUT_TYPE, OUTPUT_TYPE>(
        @NonNull WireTransformer<INPUT_TYPE, OUTPUT_TYPE> transformer,
        @NonNull BiFunction<COMPONENT_TYPE, INPUT_TYPE, OUTPUT_TYPE> transformation) {}
