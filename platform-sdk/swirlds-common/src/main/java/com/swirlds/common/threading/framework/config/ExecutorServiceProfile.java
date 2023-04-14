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

package com.swirlds.common.threading.framework.config;

import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * Various preconfigured executor service profiles.
 */
public enum ExecutorServiceProfile {
    /**
     * A thread pool with this profile uses whatever configuration is provided without providing a pre-made profile. A
     * thread pool with a custom profile but no configuration will resemble a {@link #SINGLE_THREAD_EXECUTOR}.
     */
    CUSTOM,
    /**
     * A thread pool with this profile mimics a thread pool constructed via {@link Executors#newCachedThreadPool()}. If
     * this profile is enabled, the following configuration options are ignored:
     * <ul>
     *     <li>{@link ExecutorServiceConfiguration#setCorePoolSize(int)}</li>
     *     <li>{@link ExecutorServiceConfiguration#setMaximumPoolSize(int)}</li>
     *     <li>{@link ExecutorServiceConfiguration#setKeepAliveTime(Duration)}</li>
     * </ul>
     */
    CACHED_THREAD_POOL,
    /**
     * A thread pool with this profile mimics a thread pool constructed via {@link Executors#newSingleThreadExecutor()}.
     * If this profile is enabled, the following configuration options are ignored:
     * <ul>
     *     <li>{@link ExecutorServiceConfiguration#setCorePoolSize(int)}</li>
     *     <li>{@link ExecutorServiceConfiguration#setMaximumPoolSize(int)}</li>
     *     <li>{@link ExecutorServiceConfiguration#setKeepAliveTime(Duration)}</li>
     * </ul>
     */
    SINGLE_THREAD_EXECUTOR,
    /**
     * A thread pool with this profile mimics a thread pool constructed via {@link Executors#newFixedThreadPool(int)}
     * with 8 threads. If this profile is enabled, the following configuration options are ignored:
     * <ul>
     *     <li>{@link ExecutorServiceConfiguration#setMaximumPoolSize(int)}</li>
     *     <li>{@link ExecutorServiceConfiguration#setKeepAliveTime(Duration)}</li>
     * </ul>
     */
    FIXED_THREAD_POOL
}
