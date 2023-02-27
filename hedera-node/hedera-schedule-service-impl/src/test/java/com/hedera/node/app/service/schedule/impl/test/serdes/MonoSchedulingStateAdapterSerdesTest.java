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

package com.hedera.node.app.service.schedule.impl.test.serdes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactionsState;
import com.hedera.node.app.service.schedule.impl.serdes.MonoSchedulingStateAdapterSerdes;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class MonoSchedulingStateAdapterSerdesTest {
    private static final long SOME_NUMBER = 666L;
    private static final MerkleScheduledTransactionsState SOME_SCHEDULING_STATE =
            new MerkleScheduledTransactionsState(SOME_NUMBER);

    @Mock
    private DataInput input;

    @Mock
    private DataOutput output;

    final MonoSchedulingStateAdapterSerdes subject = new MonoSchedulingStateAdapterSerdes();

    @Test
    void doesntSupportUnnecessary() {
        assertThrows(UnsupportedOperationException.class, subject::typicalSize);
        assertThrows(UnsupportedOperationException.class, () -> subject.measure(input));
        assertThrows(UnsupportedOperationException.class, () -> subject.fastEquals(SOME_SCHEDULING_STATE, input));
    }

    @Test
    void canSerializeAndDeserializeFromAppropriateStream() throws IOException {
        final var baos = new ByteArrayOutputStream();
        final var actualOut = new SerializableDataOutputStream(baos);
        subject.write(SOME_SCHEDULING_STATE, actualOut);
        actualOut.flush();

        final var actualIn = new SerializableDataInputStream(new ByteArrayInputStream(baos.toByteArray()));
        final var parsed = subject.parse(actualIn);
        assertEquals(SOME_SCHEDULING_STATE.getHash(), parsed.getHash());
    }

    @Test
    void doesntSupportOtherStreams() {
        assertThrows(IllegalArgumentException.class, () -> subject.parse(input));
        assertThrows(IllegalArgumentException.class, () -> subject.write(SOME_SCHEDULING_STATE, output));
    }
}
