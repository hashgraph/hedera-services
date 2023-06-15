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

package com.swirlds.platform.componentframework;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.concurrent.BlockingQueue;

/**
 * Configuration for a {@link TaskProcessor}.
 *
 * @param definition
 * 		an interface that extends {@link TaskProcessor} and describes which tasks the processor can handle
 * @param name
 * 		a name for the processor
 * @param customQueue
 * 		a custom queue to use for the processor, or null to use the default queue
 */
public record TaskProcessorConfig(
        @NonNull Class<? extends TaskProcessor> definition,
        @NonNull String name,
        @Nullable BlockingQueue<Object> customQueue) {}
