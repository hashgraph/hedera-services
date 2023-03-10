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

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.submerkle.ExchangeRates;
import com.hedera.node.app.service.mono.state.submerkle.SequenceNumber;
import com.hedera.node.app.service.network.impl.serdes.MonoContextAdapterCodec;

import com.hedera.pbj.runtime.io.DataInputStream;
import com.hedera.pbj.runtime.io.DataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class MonoContextAdapterSerdesTest {
    private static final MerkleNetworkContext SOME_CONTEXT = new MerkleNetworkContext(
            Instant.ofEpochSecond(1_234_567L, 890),
            new SequenceNumber(666L),
            777L,
            new ExchangeRates(1, 2, 3L, 4, 5, 6L));

    final MonoContextAdapterCodec subject = new MonoContextAdapterCodec();

    @Test
    void doesntSupportUnnecessary() {
        assertThrows(UnsupportedOperationException.class, () -> subject.measureRecord(SOME_CONTEXT));
        assertThrows(UnsupportedOperationException.class, () -> subject.measure(null));
        assertThrows(UnsupportedOperationException.class, () -> subject.fastEquals(SOME_CONTEXT, null));
    }

    @Test
    void canSerializeAndDeserializeFromAppropriateStream() throws IOException {
        final var baos = new ByteArrayOutputStream();
        final var actualOut = new DataOutputStream(baos);
        subject.write(SOME_CONTEXT, actualOut);
        actualOut.flush();
        //System.out.println(Arrays.toString(baos.toByteArray()));

        final var actualIn = new DataInputStream(new ByteArrayInputStream(baos.toByteArray()));
        final var parsed = subject.parse(actualIn);
        assertSomeFields(SOME_CONTEXT, parsed);
    }


    private void assertSomeFields(final MerkleNetworkContext a, final MerkleNetworkContext b) {
        assertEquals(a.seqNo().current(), b.seqNo().current());
        assertEquals(a.lastScannedEntity(), b.lastScannedEntity());
        assertEquals(a.midnightRates(), b.getMidnightRates());
        assertEquals(a.getStateVersion(), b.getStateVersion());
        assertEquals(a.getPreExistingEntityScanStatus(), b.getPreExistingEntityScanStatus());
    }
}
