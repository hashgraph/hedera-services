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

package com.hedera.node.app.service.mono.state.codec;

import com.hedera.pbj.runtime.io.DataInputStream;
import com.hedera.pbj.runtime.io.DataOutputStream;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.MerkleLong;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonoMapCodecAdapterTest {
    private static final VirtualBlobKeySerializer SERIALIZER = new VirtualBlobKeySerializer();

    @Mock
    private DataInput input;

    @Mock
    private DataOutput output;

    @Test
    void canGetACodec() throws IOException {
        final var longCodec = MonoMapCodecAdapter.codecForSelfSerializable(1, MerkleLong::new);

        final var baos = new ByteArrayOutputStream();
        final var out = new DataOutputStream(new SerializableDataOutputStream(baos));
        final var longValue = new MerkleLong(1);

        longCodec.write(longValue, out);

        out.flush();
        out.close();

        final var bais = new ByteArrayInputStream(baos.toByteArray());
        final var in = new DataInputStream(new SerializableDataInputStream(bais));

        final var parsedLongValue = longCodec.parse(in);
        assertEquals(longValue, parsedLongValue);

        assertThrows(UnsupportedOperationException.class, () -> longCodec.measure(in));
        assertThrows(UnsupportedOperationException.class, () -> longCodec.fastEquals(longValue, in));
    }
}
