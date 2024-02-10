/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.networkadmin.impl.test.serdes;

import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.networkadmin.impl.serdes.EntityNumCodec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityNumSerdesTest {
    private static final EntityNum SOME_NUM = EntityNum.fromLong(666L);

    @Mock
    private ReadableSequentialData input;

    @Mock
    private WritableSequentialData output;

    final EntityNumCodec subject = new EntityNumCodec();

    @Test
    void doesntSupportUnnecessary() {
        Assertions.assertThrows(UnsupportedOperationException.class, () -> subject.measureRecord(SOME_NUM));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> subject.measure(input));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> subject.fastEquals(SOME_NUM, input));
    }

    @Test
    void canDeserializeFromAppropriateStream() throws ParseException {
        BDDMockito.given(input.readInt()).willReturn(SOME_NUM.intValue());

        final var parsed = subject.parse(input);

        Assertions.assertEquals(SOME_NUM, parsed);
    }

    @Test
    void canSerializeToAppropriateStream() throws IOException {
        subject.write(SOME_NUM, output);

        Mockito.verify(output).writeInt(SOME_NUM.intValue());
    }
}
