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

package com.hedera.node.app.spi.state.serdes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class VirtualKeySerdesAdapterTest extends AbstractVirtualSerdesTest<VirtualBlobKey> {
    private static final int NUM_INSTANCES = 42;
    private static final SplittableRandom RANDOM = new SplittableRandom();
    private static final VirtualBlobKeySerializer SERIALIZER = new VirtualBlobKeySerializer();

    public VirtualKeySerdesAdapterTest() {
        super(MonoMapSerdesAdapter.serdesForVirtualKey(
                VirtualBlobKey.CURRENT_VERSION, VirtualBlobKey::new, SERIALIZER));
    }

    @Test
    void canMeasureKeySize() throws IOException {
        final var key = new VirtualBlobKey(VirtualBlobKey.Type.FILE_DATA, RANDOM.nextInt());
        final var bb = new ByteBufferDataInput(ByteBuffer.wrap(writeUsingBuffer(key)));
        final var expected = SERIALIZER.getSerializedSize();
        final var actual = subject.measure(bb);
        assertEquals(expected, actual);
    }

    @Test
    void canGetTypicalSize() {
        final var expected = SERIALIZER.getSerializedSize();
        final var actual = subject.typicalSize();
        assertEquals(expected, actual);
    }

    @Test
    void doesNotSupportFastEquals() {
        final var key = new VirtualBlobKey(VirtualBlobKey.Type.FILE_DATA, RANDOM.nextInt());
        final var bb = new ByteBufferDataInput(ByteBuffer.wrap(writeUsingBuffer(key)));
        assertThrows(UnsupportedOperationException.class, () -> subject.fastEquals(key, bb));
    }
}
