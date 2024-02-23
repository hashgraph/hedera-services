/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.utility;

import com.swirlds.common.Releasable;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Keeps track of a {@link com.swirlds.common.constructable.RuntimeConstructable} object. When the object is released,
 * this record will also be released.
 */
public class RuntimeObjectRecord implements Releasable {

    /**
     * Has this object been released?
     */
    private boolean released = false;

    /**
     * An action to run when this record becomes released.
     */
    private final Consumer<RuntimeObjectRecord> cleanupAction;

    /**
     * The time when this object was created.
     */
    private final Instant creationTime;

    private final Object metadata;

    /**
     * Create a new record of an object being tracked.
     *
     * @param creationTime
     * 		the creation time of the object
     * @param cleanupAction
     * 		an operation that will be performed when {@link #release()} is called
     */
    public RuntimeObjectRecord(final Instant creationTime, final Consumer<RuntimeObjectRecord> cleanupAction) {
        this(creationTime, cleanupAction, null);
    }

    /**
     * Create a new record of an object being tracked.
     *
     * @param creationTime
     * 		the creation time of the object
     * @param cleanupAction
     * 		an operation that will be performed when {@link #release()} is called
     * @param metadata
     * 		optional arbitrary metadata for debugging purposes, can be null
     */
    public RuntimeObjectRecord(
            final Instant creationTime, final Consumer<RuntimeObjectRecord> cleanupAction, final Object metadata) {

        this.creationTime = creationTime;
        this.cleanupAction = cleanupAction;
        this.metadata = metadata;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean release() {
        throwIfDestroyed();
        released = true;
        if (cleanupAction != null) {
            cleanupAction.accept(this);
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized boolean isDestroyed() {
        return released;
    }

    /**
     * Get the time when this object was created.
     */
    public Instant getCreationTime() {
        return creationTime;
    }

    /**
     * Get the age of this record.
     *
     * @param now
     * 		the current time
     * @return the time between the creation of this record and now
     */
    public Duration getAge(final Instant now) {
        return Duration.between(creationTime, now);
    }

    /**
     * Get the metadata associated with this record.
     *
     * @param <T>
     * 		the type of the metadata
     * @return the associated metadata
     * @throws ClassCastException
     * 		if the metadata is not of the expected type
     */
    @SuppressWarnings("unchecked")
    public <T> T getMetadata() {
        return (T) metadata;
    }
}
