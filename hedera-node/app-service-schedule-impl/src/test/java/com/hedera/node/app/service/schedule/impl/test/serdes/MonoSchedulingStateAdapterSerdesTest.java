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
import com.hedera.node.app.service.schedule.impl.serdes.MonoSchedulingStateAdapterCodec;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class MonoSchedulingStateAdapterSerdesTest {
    private static final long SOME_NUMBER = 646L;
    private static final MerkleScheduledTransactionsState SOME_SCHEDULING_STATE =
            new MerkleScheduledTransactionsState(SOME_NUMBER);
    final MonoSchedulingStateAdapterCodec subject = new MonoSchedulingStateAdapterCodec();

    @Mock
    private ReadableSequentialData input;

    @Test
    void doesNotSupportUnnecessary() {
        assertThrows(UnsupportedOperationException.class, () -> subject.measureRecord(SOME_SCHEDULING_STATE));
        assertThrows(UnsupportedOperationException.class, () -> subject.measure(input));
        assertThrows(UnsupportedOperationException.class, () -> subject.fastEquals(SOME_SCHEDULING_STATE, input));
    }

    @Test
    void canSerializeAndDeserializeFromAppropriateStream() throws IOException {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        final WritableStreamingData actualOut = new WritableStreamingData(byteStream);
        subject.write(SOME_SCHEDULING_STATE, actualOut);

        final ReadableStreamingData actualIn =
                new ReadableStreamingData(new ByteArrayInputStream(byteStream.toByteArray()));
        final MerkleScheduledTransactionsState parsed = subject.parse(actualIn);
        assertEquals(SOME_SCHEDULING_STATE.getHash(), parsed.getHash());
    }
}
