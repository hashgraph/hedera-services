/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.benchmark;

public final class WiringBenchmarkEvent {
    private long number = -1; // We'll let the orphan buffer assign this, although I think consensus actually does
    private final byte[] data = new byte[1024 * 32]; // Just gotta have some bytes. Whatever.

    public WiringBenchmarkEvent() {}

    void reset(long number) {
        this.number = number;
    }

    @Override
    public String toString() {
        return "Event {number=" + number + "}";
    }

    public long number() {
        return number;
    }
}
