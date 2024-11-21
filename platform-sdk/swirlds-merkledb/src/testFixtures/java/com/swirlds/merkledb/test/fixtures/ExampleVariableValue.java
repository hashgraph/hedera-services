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

public final class ExampleVariableValue {

    private static final Random RANDOM = new Random(12234);

    private static final int RANDOM_BYTES = 1024;

    private static final byte[] RANDOM_DATA = new byte[RANDOM_BYTES];

    static {
        RANDOM.nextBytes(RANDOM_DATA);
    }

    public static Bytes intToValue(final int v) {
        return intToValue(v, RANDOM_DATA, 0, 256 + (v % 768));
    }

    public static Bytes intToValue(final int v, final byte[] data, final int off, final int len) {
        final byte[] bytes = new byte[Integer.BYTES + len];
        ByteBuffer.wrap(bytes).putInt(v).put(data, off, len);
        return Bytes.wrap(bytes);
    }

    private ExampleVariableValue() {}
}
