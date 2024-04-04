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

package com.swirlds.common.wiring.model.deterministic;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.wiring.model.internal.StandardWiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.internal.AbstractTaskSchedulerBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.ForkJoinPool;

public class DeterministicTaskSchedulerBuilder<OUT> extends AbstractTaskSchedulerBuilder<OUT> {

    /**
     * Constructor.
     *
     * @param platformContext the platform context
     * @param model           the wiring model
     * @param name            the name of the task scheduler. Used for metrics and debugging. Must be unique. Must only
     *                        contain alphanumeric characters and underscores.
     * @param defaultPool     the default fork join pool, if none is provided then this pool will be used
     */
    public DeterministicTaskSchedulerBuilder(
            @NonNull final PlatformContext platformContext,
            @NonNull final StandardWiringModel model,
            @NonNull final String name,
            @NonNull final ForkJoinPool defaultPool) {
        super(platformContext, model, name, defaultPool);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public TaskScheduler<OUT> build() {
        return null;
    }
}
