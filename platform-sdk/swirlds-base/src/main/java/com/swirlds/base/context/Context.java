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

package com.swirlds.base.context;

import com.swirlds.base.context.internal.GlobalContext;
import com.swirlds.base.context.internal.ThreadLocalContext;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A context is a key-value holder that can be used to store information of different parts of the code.
 * <p>
 * The context MUST NOT be used a collection to access data from different parts of the code. Based on that the
 * interface does not provide any methods to access the data. Instead, the context is used to add information to the
 * context that can be used by base systems like logging and metrics to enrich events of the base system "with a
 * context".
 */
public interface Context {

    /**
     * Adds a key-value pair to the context.
     *
     * @param key   the key
     * @param value the value
     */
    void add(@NonNull final String key, @NonNull final String value);

    /**
     * remove a key-value pair from the context if available.
     *
     * @param key the key to remove
     */
    void remove(@NonNull final String key);

    /**
     * Adds a key-value pair to the context. The returned {@link AutoCloseable} can be used to remove the value for the
     * given key.
     * <p>
     * Internally the context does not use a stack to store changes. If the value has been changed between this call and
     * any future call that sets the same key, the new value will be removed.
     *
     * @param key   the key
     * @param value the value
     * @return an {@link AutoCloseable} that can be used to remove the key-value pair from the context
     * @throws NullPointerException if the key or value is null
     */
    @NonNull
    default AutoCloseable addWithRemovalOnClose(@NonNull final String key, @NonNull final String value) {
        add(key, value);
        return () -> remove(key);
    }

    /**
     * Adds a key-value pair to the context.
     *
     * @param key   the key
     * @param value the value
     */
    @NonNull
    default void add(@NonNull final String key, final int value) {
        add(key, Integer.toString(value));
    }

    /**
     * Adds a key-value pair to the context. The returned {@link AutoCloseable} can be used to remove the value for the given key.
     *<p>
     *     Internally the context does not use a stack to store changes. If the value has been changed between this call and any future call that sets the same key, the new value will be removed.
     * @param key   the key
     * @param value the value
     * @return an {@link AutoCloseable} that can be used to remove the key-value pair from the context
     */
    @NonNull
    default AutoCloseable addWithRemovalOnClose(@NonNull final String key, final int value) {
        return addWithRemovalOnClose(key, Integer.toString(value));
    }

    /**
     * Adds a key-value pair to the context.
     *
     * @param key   the key
     * @param value the value
     */
    @NonNull
    default void add(@NonNull final String key, final long value) {
        add(key, Long.toString(value));
    }

    /**
     * Adds a key-value pair to the context. The returned {@link AutoCloseable} can be used to remove the value for the given key.
     *<p>
     *     Internally the context does not use a stack to store changes. If the value has been changed between this call and any future call that sets the same key, the new value will be removed.
     * @param key   the key
     * @param value the value
     * @return an {@link AutoCloseable} that can be used to remove the key-value pair from the context
     */
    @NonNull
    default AutoCloseable addWithRemovalOnClose(@NonNull final String key, final long value) {
        return addWithRemovalOnClose(key, Long.toString(value));
    }

    /**
     * Adds a key-value pair to the context.
     *
     * @param key   the key
     * @param value the value
     */
    @NonNull
    default void add(@NonNull final String key, final float value) {
        add(key, Float.toString(value));
    }

    /**
     * Adds a key-value pair to the context. The returned {@link AutoCloseable} can be used to remove the value for the
     * given key.
     * <p>
     * Internally the context does not use a stack to store changes. If the value has been changed between this call and
     * any future call that sets the same key, the new value will be removed.
     *
     * @param key   the key
     * @param value the value
     * @return an {@link AutoCloseable} that can be used to remove the key-value pair from the context
     */
    @NonNull
    default AutoCloseable addWithRemovalOnClose(@NonNull final String key, final float value) {
        return addWithRemovalOnClose(key, Float.toString(value));
    }

    /**
     * Adds a key-value pair to the context.
     *
     * @param key   the key
     * @param value the value
     */
    @NonNull
    default void add(@NonNull final String key, final double value) {
        add(key, Double.toString(value));
    }

    /**
     * Adds a key-value pair to the context. The returned {@link AutoCloseable} can be used to remove the value for the
     * given key.
     * <p>
     * Internally the context does not use a stack to store changes. If the value has been changed between this call and
     * any future call that sets the same key, the new value will be removed.
     *
     * @param key   the key
     * @param value the value
     * @return an {@link AutoCloseable} that can be used to remove the key-value pair from the context
     */
    @NonNull
    default AutoCloseable addWithRemovalOnClose(@NonNull final String key, final double value) {
        return addWithRemovalOnClose(key, Double.toString(value));
    }

    /**
     * Adds a key-value pair to the context.
     *
     * @param key   the key
     * @param value the value
     */
    @NonNull
    default void add(@NonNull final String key, final boolean value) {
        add(key, Boolean.toString(value));
    }

    /**
     * Adds a key-value pair to the context. The returned {@link AutoCloseable} can be used to remove the value for the
     * given key.
     * <p>
     * Internally the context does not use a stack to store changes. If the value has been changed between this call and
     * any future call that sets the same key, the new value will be removed.
     *
     * @param key   the key
     * @param value the value
     * @return an {@link AutoCloseable} that can be used to remove the key-value pair from the context
     */
    @NonNull
    default AutoCloseable addWithRemovalOnClose(@NonNull final String key, final boolean value) {
        return addWithRemovalOnClose(key, Boolean.toString(value));
    }

    /**
     * Returns the global context. The content of the global context is shared by all threads. The global context can be
     * used to store global values like the node id or the ip address of the node.
     *
     * @return the global context
     */
    @NonNull
    static Context getGlobalContext() {
        return GlobalContext.getInstance();
    }

    /**
     * Returns the thread local context. The content of the thread local context is only visible to the current thread.
     * The thread local context can be used to store thread local values like the thread id or the thread name.
     *
     * @return the thread local context
     */
    @NonNull
    static Context getThreadLocalContext() {
        return ThreadLocalContext.getInstance();
    }
}
