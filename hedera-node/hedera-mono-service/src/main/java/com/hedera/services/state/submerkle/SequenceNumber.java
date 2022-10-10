/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.submerkle;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

public class SequenceNumber {
    volatile long i;

    public SequenceNumber() {}

    public SequenceNumber(long i) {
        this.i = i;
    }

    public synchronized long getAndIncrement() {
        return i++;
    }

    public synchronized void decrement() {
        i--;
    }

    public long current() {
        return i;
    }

    public synchronized SequenceNumber copy() {
        return new SequenceNumber(this.i);
    }

    public void deserialize(SerializableDataInputStream in) throws IOException {
        this.i = in.readLong();
    }

    public void serialize(SerializableDataOutputStream out) throws IOException {
        out.writeLong(i);
    }
}
