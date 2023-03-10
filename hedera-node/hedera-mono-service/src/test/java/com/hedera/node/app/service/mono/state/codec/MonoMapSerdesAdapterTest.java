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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.pbj.runtime.io.DataBuffer;
import com.hedera.pbj.runtime.io.DataInput;
import com.hedera.pbj.runtime.io.DataInputStream;
import com.hedera.pbj.runtime.io.DataOutput;
import com.hedera.pbj.runtime.io.DataOutputStream;
import com.swirlds.common.merkle.utility.MerkleLong;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MonoMapSerdesAdapterTest {
    private static final VirtualBlobKeySerializer SERIALIZER = new VirtualBlobKeySerializer();

    @Mock
    private DataInput input;

    @Mock
    private DataOutput output;

    @Test
    void canGetASerdes() throws IOException {
        final var longSerdes = MonoMapCodecAdapter.codecForSelfSerializable(1, MerkleLong::new);

        final var baos = new ByteArrayOutputStream();
        final var unusableOut = DataBuffer.allocate(1024);
        final var out = new DataOutputStream(baos);
        final var longValue = new MerkleLong(1);

        assertThrows(IllegalArgumentException.class, () -> longSerdes.write(longValue, unusableOut));
        longSerdes.write(longValue, out);

        out.flush();
        out.close();
        final var bais = new ByteArrayInputStream(baos.toByteArray());
        final var unusableIn = DataBuffer.wrap(ByteBuffer.wrap(baos.toByteArray()));
        final var in = new DataInputStream(bais);
        in.reset();  // without this line, the test fails

        assertThrows(IllegalArgumentException.class, () -> longSerdes.parse(unusableIn));
        final var parsedLongValue = longSerdes.parse(in);
        assertEquals(longValue, parsedLongValue);

        assertThrows(UnsupportedOperationException.class, () -> longSerdes.measure(in));
        assertThrows(UnsupportedOperationException.class, () -> longSerdes.fastEquals(longValue, in));
    }

    @Test
    void mustUseRecognizableDataInputAndOutputForVirtualKeys() {
        final var subject = MonoMapCodecAdapter.codecForVirtualKey(
                VirtualBlobKey.CURRENT_VERSION, VirtualBlobKey::new, SERIALIZER);

        assertThrows(IllegalArgumentException.class, () -> subject.parse(input));
        final var keyToFailWith = new VirtualBlobKey();
        assertThrows(IllegalArgumentException.class, () -> subject.write(keyToFailWith, output));
    }

    @Test
    void mustUseRecognizableDataInputAndOutputForVirtualValues() {
        final var subject =
                MonoMapCodecAdapter.codecForVirtualValue(VirtualBlobValue.CURRENT_VERSION, VirtualBlobValue::new);

        assertThrows(IllegalArgumentException.class, () -> subject.parse(input));
        final var valueToFailWith = new VirtualBlobValue();
        assertThrows(IllegalArgumentException.class, () -> subject.write(valueToFailWith, output));
    }
}
