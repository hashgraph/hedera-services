/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.hapi.utils.throttles;

/** A bucket of discrete capacity. */
class DiscreteLeakyBucket {
    private long used;
    private final long capacity;

    DiscreteLeakyBucket(long capacity) {
        used = 0L;
        assertValidState(used, capacity);
        this.capacity = capacity;
    }

    /* Used only for test setup */
    DiscreteLeakyBucket(long used, long capacity) {
        assertValidState(used, capacity);
        this.used = used;
        this.capacity = capacity;
    }

    long capacityFree() {
        return capacity - used;
    }

    long capacityUsed() {
        return used;
    }

    long totalCapacity() {
        return capacity;
    }

    void useCapacity(long units) {
        long newUsed = used + units;
        assertValidUsage(units, newUsed);
        used = newUsed;
    }

    void leak(long units) {
        assertValidUnitsToFree(units);
        used -= Math.min(used, units);
    }

    void resetUsed(long amount) {
        assertValidState(amount, capacity);
        this.used = amount;
    }

    private void assertValidState(long candidateUsed, long candidateCapacity) {
        assertValidUnitsToFree(candidateCapacity);
        if (candidateUsed < 0 || candidateUsed > candidateCapacity || candidateCapacity == 0) {
            throw new IllegalArgumentException(
                    "Cannot use "
                            + candidateUsed
                            + " units in a bucket of capacity "
                            + candidateCapacity
                            + "!");
        }
    }

    private void assertValidUnitsToFree(long units) {
        if (units < 0) {
            throw new IllegalArgumentException("Cannot free " + units + " units of capacity!");
        }
    }

    private void assertValidUsage(long newUnits, long newUsage) {
        if (newUnits < 0) {
            throw new IllegalArgumentException("Cannot use " + newUnits + " units of capacity!");
        }
        if (newUsage < 0 || newUsage > capacity) {
            throw new IllegalArgumentException(
                    "Adding "
                            + newUnits
                            + " units to "
                            + used
                            + " already used would exceed capacity "
                            + capacity
                            + "!");
        }
    }
}
