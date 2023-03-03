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
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.token.impl.serdes.StringSerdes;
import java.io.DataInput;
import java.io.DataOutput;
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

    final StringSerdes subject = new StringSerdes();

    @Test
    void givesTypicalSize() {
        assertEquals(255, subject.typicalSize());
    }

    @Test
    void providesFastEquals() throws IOException {
        given(in.readInt()).willReturn(SOME_STRING.getBytes().length);
        subject.fastEquals(SOME_STRING, in);
        assertEquals(255, subject.typicalSize());
    }

    @Test
    void canDeserializeFromAppropriateStream() throws IOException {
        given(in.readInt()).willReturn(SOME_STRING.getBytes().length);
        subject.parse(in);

        verify(in).readInt();
        verify(in).readFully(new byte[SOME_STRING.getBytes().length]);
    }

    @Test
    void canSerializeToAppropriateStream() throws IOException {
        subject.write(SOME_STRING, out);

        verify(out).writeInt(SOME_STRING.getBytes().length);
    }
}
