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

package com.hedera.node.app.spi.state.codec;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LongCodecTest {
    private static final Bytes VALUE_AS_BYTES = Bytes.wrap(new byte[] {0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x0, 0x2A});
    private static final Long TEST_VALUE = Long.valueOf(42L);
    private static final int TEST_LENGTH = Long.BYTES;
    private ReadableSequentialData in;
    private WritableSequentialData out;
    private ByteBuffer outputBuffer;

    final LongCodec subject = LongCodec.SINGLETON;

    @BeforeEach
    void setUp() {
        in = BufferedData.wrap(VALUE_AS_BYTES.toByteArray());
        outputBuffer = ByteBuffer.allocate(32);
        out = BufferedData.wrap(outputBuffer);
    }

    @Test
    void providesFastEquals() {
        Assertions.assertTrue(subject.fastEquals(TEST_VALUE, in));
    }

    @Test
    void measuresInput() {
        Assertions.assertEquals(TEST_LENGTH, subject.measure(in));
    }

    @Test
    void canDeserializeFromAppropriateStream() throws IOException {
        ReadableSequentialData inSpy = Mockito.spy(in);
        subject.parse(inSpy);

        Mockito.verify(inSpy).readLong();
    }

    @Test
    void canSerializeToAppropriateStream() throws IOException {
        WritableSequentialData outSpy = Mockito.spy(out);
        subject.write(TEST_VALUE, outSpy);

        Mockito.verify(outSpy).writeLong(TEST_VALUE);
        Assertions.assertEquals(TEST_LENGTH, outputBuffer.position());
    }

    @Test
    void canMeasureThenParse() {
        Assertions.assertEquals(Long.BYTES, subject.measure(in));
        Assertions.assertEquals(TEST_VALUE, subject.parse(in));
    }

    @Test
    @Disabled("This cannot work until ReadableSequentialData.view is fixed to not consume the input.")
    void parseViewFastEqualThenParse() {
        ReadableSequentialData view = in.view(TEST_LENGTH);
        Long viewValue = subject.parse(view);
        Assertions.assertTrue(subject.fastEquals(viewValue, in));
        Assertions.assertEquals(viewValue, subject.parse(in));
    }
}
