/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.threading.framework.ThreadSeed;
import com.swirlds.common.threading.framework.internal.AbstractThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadBuilder;
import java.util.concurrent.ThreadFactory;

/**
 * This object is used to configure and build {@link Thread} instances.
 */
public class ThreadConfiguration extends AbstractThreadConfiguration<ThreadConfiguration> {

    /**
     * Build a new thread configuration with default values.
     *
     * @param threadBuilder
     * 		capable of building raw thread objects
     */
    public ThreadConfiguration(final ThreadBuilder threadBuilder) {
        super(threadBuilder);
    }

    /**
     * Copy constructor.
     *
     * @param that
     * 		the configuration to copy.
     */
    private ThreadConfiguration(final ThreadConfiguration that) {
        super(that);
    }

    /**
     * Get a copy of this configuration. New copy is always mutable,
     * and the mutability status of the original is unchanged.
     *
     * @return a copy of this configuration
     */
    @Override
    public ThreadConfiguration copy() {
        return new ThreadConfiguration(this);
    }

    /**
     * Extracts the thread configuration from the caller's thread.
     *
     * @param threadBuilder
     * 		capable of building raw thread objects
     * @return a thread configuration with properties matching the caller's thread
     */
    public static ThreadConfiguration captureThreadConfiguration(final ThreadBuilder threadBuilder) {
        return captureThreadConfiguration(threadBuilder, Thread.currentThread());
    }

    /**
     * Extracts the thread configuration from a given thread.
     *
     * @param threadBuilder
     * 		capable of building raw thread objects
     * @param thread
     * 		the thread to copy configuration from
     * @return a thread configuration that matches the provided thread
     */
    public static ThreadConfiguration captureThreadConfiguration(
            final ThreadBuilder threadBuilder, final Thread thread) {
        final ThreadConfiguration configuration = new ThreadConfiguration(threadBuilder);
        configuration.copyThreadConfiguration(thread);
        return configuration;
    }

    /**
     * <p>
     * Build a new thread. Do not start it automatically.
     * </p>
     *
     * <p>
     * After calling this method, this configuration object should not be modified or used to construct other
     * threads, factories, or seeds.
     * </p>
     *
     * @return a thread built using this configuration
     */
    public Thread build() {
        return build(false);
    }

    /**
     * <p>
     * Build a new thread.
     * </p>
     *
     * <p>
     * After calling this method, this configuration object should not be modified or used to construct other
     * threads, factories, or seeds.
     * </p>
     *
     * @param start
     * 		if true then start the thread before returning it
     * @return a thread built using this configuration
     */
    public Thread build(final boolean start) {
        becomeImmutable();
        return buildThread(start);
    }

    /**
     * <p>
     * Get a {@link ThreadFactory} that contains the configuration specified by this object.
     * </p>
     *
     * <p>
     * After calling this method, this configuration object should not be modified or used to construct other
     * threads, factories, or seeds.
     * </p>
     */
    public ThreadFactory buildFactory() {
        enableThreadNumbering();

        final ThreadFactory factory = (final Runnable r) -> {
            final Thread thread = getThreadBuilder().buildThread(r);
            configureThread(thread);
            return thread;
        };

        becomeImmutable();

        return factory;
    }

    /**
     * <p>
     * Build a "seed" that can be planted in a thread. When the runnable is executed, it takes over the calling thread
     * and configures that thread the way it would configure a newly created thread via {@link #build()}. When work
     * is finished, the calling thread is restored back to its original configuration.
     * </p>
     *
     * <p>
     * Note that this seed will be unable to change daemon status of the calling thread,
     * regardless the values set in this configuration.
     * </p>
     *
     * <p>
     * After calling this method, this configuration object should not be modified or used to construct other
     * threads, factories, or seeds.
     * </p>
     *
     * @return a seed that can be used to inject this thread configuration onto an existing thread.
     */
    public ThreadSeed buildSeed() {
        becomeImmutable();
        return buildThreadSeed();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Runnable getRunnable() {
        return super.getRunnable();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ThreadConfiguration setRunnable(final Runnable runnable) {
        return super.setRunnable(runnable);
    }
}
