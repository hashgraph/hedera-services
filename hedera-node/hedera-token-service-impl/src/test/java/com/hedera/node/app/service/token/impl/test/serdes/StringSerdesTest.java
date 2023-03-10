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

package com.hedera.node.app.service.token.impl.test.serdes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.token.impl.serdes.StringCodec;
import com.hedera.pbj.runtime.io.DataInput;
import com.hedera.pbj.runtime.io.DataOutput;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StringSerdesTest {
    private static final String SOME_STRING = "TestString";

    @Mock
    private DataInput in;

    @Mock
    private DataOutput out;

    final StringCodec subject = new StringCodec();

    @Test
    void providesFastEquals() throws IOException {
        given(in.readInt()).willReturn(SOME_STRING.getBytes().length);
        subject.fastEquals(SOME_STRING, in);
    }

    @Test
    void measuresInput() throws IOException {
        given(in.readInt()).willReturn(SOME_STRING.getBytes().length);
        assertEquals(SOME_STRING.getBytes().length, subject.measure(in));
    }

    @Test
    void providesFastEqualsWhenExceptionThrown() throws IOException {
        final var s = mock(StringCodec.class);
        given(s.parse(in)).willThrow(IOException.class);
        willCallRealMethod().given(s).fastEquals(SOME_STRING, in);

        final var isFastEquals = s.fastEquals(SOME_STRING, in);
        assertEquals(false, isFastEquals);
    }

    @Test
    void canDeserializeFromAppropriateStream() throws IOException {
        given(in.readInt()).willReturn(SOME_STRING.getBytes().length);
        subject.parse(in);

        verify(in).readInt();
        verify(in).readBytes(new byte[SOME_STRING.getBytes().length]);
    }

    @Test
    void canSerializeToAppropriateStream() throws IOException {
        subject.write(SOME_STRING, out);

        verify(out).writeInt(SOME_STRING.getBytes().length);
    }
}
