/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import com.swirlds.common.Reservable;
import java.util.Objects;

/**
 * A thread safe object that holds a reference count for an object, and then calls a lambda when it is time to destroy
 * the object.
 */
public class ReferenceCounter extends AbstractReservable {

    private final Runnable onDestroy;
    private final Runnable onReferenceCountException;

    /**
     * Create a new reference counter.
     *
     * @param onDestroy a method that is called when the object is destroyed
     */
    public ReferenceCounter(final Runnable onDestroy) {
        this(onDestroy, null);
    }

    /**
     * Create a new reference counter.
     *
     * @param onDestroy                 a method that is called when the object is destroyed
     * @param onReferenceCountException a method that is called when the object is destroyed
     */
    public ReferenceCounter(final Runnable onDestroy, final Runnable onReferenceCountException) {
        this.onDestroy = Objects.requireNonNull(onDestroy, "onDestroy");
        this.onReferenceCountException = onReferenceCountException;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDestroy() {
        onDestroy.run();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onReferenceCountException() {
        if (onReferenceCountException != null) {
            onReferenceCountException.run();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final int reservationCount = getReservationCount();
        if (reservationCount == Reservable.DESTROYED_REFERENCE_COUNT) {
            return "destroyed(" + Reservable.DESTROYED_REFERENCE_COUNT + ")";
        } else if (reservationCount == Reservable.IMPLICIT_REFERENCE_COUNT) {
            return "implicit(" + Reservable.IMPLICIT_REFERENCE_COUNT + ")";
        } else {
            return "explicit(" + reservationCount + ")";
        }
    }
}
