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

package com.hedera.node.app.service.network.impl.test.serdes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.service.mono.stream.RecordsRunningHashLeaf;
import com.hedera.node.app.service.network.impl.serdes.MonoRunningHashesAdapterCodec;
import com.hedera.pbj.runtime.io.DataInput;
import com.hedera.pbj.runtime.io.DataOutput;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class MonoRunningHashesAdapterSerdesTest {
    private static final RecordsRunningHashLeaf SOME_HASHES = new RecordsRunningHashLeaf(
            new RunningHash(new Hash("abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdef".getBytes())));

    @Mock
    private DataInput input;

    @Mock
    private DataOutput output;

    final MonoRunningHashesAdapterCodec subject = new MonoRunningHashesAdapterCodec();

    @Test
    void doesntSupportUnnecessary() {
        assertThrows(UnsupportedOperationException.class, () -> subject.measureRecord(SOME_HASHES));
        assertThrows(UnsupportedOperationException.class, () -> subject.measure(input));
        assertThrows(UnsupportedOperationException.class, () -> subject.fastEquals(SOME_HASHES, input));
    }

    @Test
    void canSerializeAndDeserializeFromAppropriateStream() throws IOException, ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructable(new ClassConstructorPair(Hash.class, Hash::new));
        final var baos = new ByteArrayOutputStream();
        final var actualOut = new SerializableDataOutputStream(baos);
        subject.write(SOME_HASHES, output);
        actualOut.flush();

        final var actualIn = new SerializableDataInputStream(new ByteArrayInputStream(baos.toByteArray()));
        final var parsed = subject.parse(input);
        assertEquals(SOME_HASHES.getHash(), parsed.getHash());
    }

    @Test
    void doesntSupportOtherStreams() {
        assertThrows(IllegalArgumentException.class, () -> subject.parse(input));
        assertThrows(IllegalArgumentException.class, () -> subject.write(SOME_HASHES, output));
    }
}
