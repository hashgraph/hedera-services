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

package com.swirlds.base.concurrent;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ForkJoinPool;

public class ForkJoinPoolFactory {

    private final ForkJoinWorkerThreadFactoryImpl factory;

    private final UncaughtExceptionHandler handler;

    public ForkJoinPoolFactory(String platformId, UncaughtExceptionHandler handler) {
        this.handler = handler;
        ThreadGroup threadGroup = new ThreadGroup("ForkJoinPool-" + platformId);
        this.factory = new ForkJoinWorkerThreadFactoryImpl(threadGroup, platformId);
    }

    public ForkJoinPool create(int parallelism) {
        return new ForkJoinPool(parallelism, factory, handler, false);
    }
}
