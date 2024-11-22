/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.test.fixtures;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.nio.ByteBuffer;
import java.util.Random;

public final class ExampleFixedValue {

    public static final int RANDOM_BYTES = 32;

    static final byte[] RANDOM_DATA = new byte[RANDOM_BYTES];

    static {
        new Random(12234).nextBytes(RANDOM_DATA);
    }

    public static Bytes intToValue(final int id) {
        return intToValue(id, RANDOM_DATA);
    }

    public static Bytes intToValue(final int id, final byte[] data) {
        final byte[] bytes = new byte[Integer.BYTES + data.length];
        ByteBuffer.wrap(bytes).putInt(id).put(data);
        return Bytes.wrap(bytes);
    }

    public static int valueToId(final Bytes value) {
        return value.getInt(0);
    }

    public static byte[] valueToData(final Bytes value) {
        return value.toByteArray(Integer.BYTES, Math.toIntExact(value.length() - Integer.BYTES));
    }
}
