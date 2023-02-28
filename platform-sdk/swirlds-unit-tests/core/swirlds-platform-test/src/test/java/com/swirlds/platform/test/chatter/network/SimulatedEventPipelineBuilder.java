/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.chatter.network;

import java.util.LinkedList;
import java.util.List;

/**
 * A utility class that allows for fluent-style creation of event pipelines.
 *
 * @param <T> the type of event to process
 */
public class SimulatedEventPipelineBuilder<T extends SimulatedChatterEvent> {
    private final List<SimulatedEventPipeline<T>> pipelineList = new LinkedList<>();

    /**
     * Sets the next event processor in the pipeline.
     *
     * @param next the next event processor
     * @param <R>  the type of the next event processor
     * @return this
     */
    public <R extends SimulatedEventPipeline<T>> SimulatedEventPipelineBuilder<T> next(final R next) {
        pipelineList.add(next);
        return this;
    }

    /**
     * Builds the pipeline and returns the first processor
     *
     * @return the first event processor in the pipeline
     */
    public SimulatedEventPipeline<T> build() {
        if (pipelineList.isEmpty()) {
            return null;
        }
        SimulatedEventPipeline<T> curr = pipelineList.get(0);
        for (int i = 1; i < pipelineList.size(); i++) {
            final SimulatedEventPipeline<T> next = pipelineList.get(i);
            curr.setNext(next);
            curr = next;
        }
        return pipelineList.get(0);
    }
}
