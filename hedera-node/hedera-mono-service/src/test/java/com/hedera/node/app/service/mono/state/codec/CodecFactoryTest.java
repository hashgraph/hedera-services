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

import static com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactionsState.CURRENT_VERSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.state.merkle.MerkleNetworkContext;
import com.hedera.node.app.service.mono.state.merkle.MerkleScheduledTransactionsState;
import com.hedera.node.app.service.mono.state.submerkle.ExchangeRates;
import com.hedera.node.app.service.mono.state.submerkle.SequenceNumber;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CodecFactoryTest {
    @Mock
    private ReadableStreamingData input;

    @Mock
    private WritableStreamingData output;

    @Mock
    private PbjParser<String> parser;

    @Mock
    private PbjWriter<String> writer;

    private Codec<String> subject;

    @BeforeEach
    void setUp() {
        subject = CodecFactory.newInMemoryCodec(parser, writer);
    }

    @Test
    void unusedMethodsAreUnsupported() {
        assertThrows(UnsupportedOperationException.class, () -> subject.measure(input));
        assertThrows(UnsupportedOperationException.class, () -> subject.fastEquals("A", input));
    }

    @Test
    void delegatesWrite() throws IOException {
        subject.write("B", output);

        verify(writer).write(eq("B"), any());
    }

    @Test
    void delegatesParse() throws IOException {
        given(parser.parse(any())).willReturn("C");

        final var value = subject.parse(input);

        assertEquals("C", value);
    }

    @Test
    void codecWorksForSchedulingState() throws IOException {
        final Codec<MerkleScheduledTransactionsState> subject =
                MonoMapCodecAdapter.codecForSelfSerializable(
                        CURRENT_VERSION,
                        MerkleScheduledTransactionsState::new);

        assertThrows(UnsupportedOperationException.class, () -> subject.measureRecord(SOME_STATE));
        assertThrows(UnsupportedOperationException.class, () -> subject.measure(input));
        assertThrows(UnsupportedOperationException.class, () -> subject.fastEquals(SOME_STATE, input));


        final var baos = new ByteArrayOutputStream();
        final var actualOut = new SerializableDataOutputStream(baos);
        subject.write(SOME_STATE, new WritableStreamingData(actualOut));
        actualOut.flush();

        final var actualIn = new SerializableDataInputStream(new ByteArrayInputStream(baos.toByteArray()));
        final var parsed = subject.parse(new ReadableStreamingData(actualIn));
        assertEquals(SOME_STATE, parsed);
    }

    private static final MerkleScheduledTransactionsState SOME_STATE =
            new MerkleScheduledTransactionsState(1_234_567);
}
