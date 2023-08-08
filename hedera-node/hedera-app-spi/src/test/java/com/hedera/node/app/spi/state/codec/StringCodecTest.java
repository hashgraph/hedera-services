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
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StringCodecTest {
    private static final String SOME_STRING = "TestString";
    private static final byte[] SOME_BYTES = SOME_STRING.getBytes(StandardCharsets.UTF_8);
    private static final int length = SOME_BYTES.length;
    private ReadableSequentialData in;
    private BufferedData outData;
    private WritableSequentialData out;

    final StringCodec subject = StringCodec.SINGLETON;

    @BeforeEach
    void setUp() {
        ByteBuffer inputData = ByteBuffer.allocate(SOME_BYTES.length + Integer.BYTES);
        inputData.mark();
        inputData.putInt(length);
        inputData.put(SOME_BYTES);
        inputData.reset();
        in = BufferedData.wrap(inputData.duplicate());
        outData = BufferedData.allocate(inputData.limit());
        out = outData;
    }

    @Test
    @Disabled("This cannot work until ReadableSequentialData.view is fixed to not consume the input.")
    void providesFastEquals() {
        Assertions.assertTrue(subject.fastEquals(SOME_STRING, in));
    }

    @Test
    void measuresInput() {
        Assertions.assertEquals(length + Integer.BYTES, subject.measure(in));
    }

    @Test
    void canDeserializeFromAppropriateStream() {
        Assertions.assertEquals(SOME_STRING, subject.parse(in));
    }

    @Test
    void canParseWrittenData() {
        subject.write(SOME_STRING, out);
        outData.resetPosition();
        Assertions.assertEquals(SOME_STRING, subject.parse(outData));
    }

    @Test
    @Disabled("This cannot work until ReadableSequentialData.view is fixed to not consume the input.")
    void canMeasureThenParse() {
        Assertions.assertEquals(length + Integer.BYTES, subject.measure(in));
        Assertions.assertEquals(SOME_STRING, subject.parse(in));
    }
}
