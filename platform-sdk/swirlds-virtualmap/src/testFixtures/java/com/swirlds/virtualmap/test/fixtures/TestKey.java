/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.test.fixtures;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.nio.ByteBuffer;

public final class TestKey {

    public static Bytes longToKey(final long k) {
        final byte[] bytes = new byte[Long.BYTES];
        // sub-optimal, but easy
        ByteBuffer.wrap(bytes).putLong(k);
        return Bytes.wrap(bytes);
    }

    public static Bytes charToKey(final char c) {
        return longToKey(c);
    }
}
