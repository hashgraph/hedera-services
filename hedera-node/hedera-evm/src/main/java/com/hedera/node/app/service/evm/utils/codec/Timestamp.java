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

package com.hedera.node.app.service.evm.utils.codec;

import java.util.Objects;

public class Timestamp {

    private long seconds;
    private int nanos;

    public Timestamp(long seconds, int nanos) {
        this.seconds = seconds;
        this.nanos = nanos;
    }

    public long getSeconds() {
        return seconds;
    }

    public void setSeconds(long seconds) {
        this.seconds = seconds;
    }

    public int getNanos() {
        return nanos;
    }

    public void setNanos(int nanos) {
        this.nanos = nanos;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Timestamp timestamp = (Timestamp) o;
        return seconds == timestamp.seconds && nanos == timestamp.nanos;
    }

    @Override
    public int hashCode() {
        return Objects.hash(seconds, nanos);
    }

    @Override
    public String toString() {
        return "Timestamp{" + "seconds=" + seconds + ", nanos=" + nanos + '}';
    }
}
