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

package com.hedera.node.app.service.consensus.impl.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.service.consensus.impl.codecs.EntityNumCodec;
import com.hedera.node.app.service.mono.utils.EntityNum;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.hedera.pbj.runtime.io.stream.ReadableStreamingData;
import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityNumCodecTest {
    private static final EntityNum SOME_NUM = EntityNum.fromLong(666L);

    final EntityNumCodec subject = new EntityNumCodec();

    @Test
    void doesntSupportUnnecessary() {
        assertThrows(UnsupportedOperationException.class, () -> subject.measure(null));
        assertThrows(UnsupportedOperationException.class, () -> subject.fastEquals(SOME_NUM, null));
    }

    @Test
    void codecWorks() throws IOException {
        final var subject = new EntityNumCodec();

        final var baos = new ByteArrayOutputStream();
        final var out = new WritableStreamingData(new SerializableDataOutputStream(baos));
        final var item = EntityNum.fromLong(666L);

        subject.write(item, out);

        out.flush();
        out.close();

        final var bais = new ByteArrayInputStream(baos.toByteArray());
        final var in = new ReadableStreamingData(bais);

        final var parsedItem = subject.parse(in);
        assertEquals(item, parsedItem);

        assertThrows(UnsupportedOperationException.class, () -> subject.measure(in));
        assertThrows(UnsupportedOperationException.class, () -> subject.fastEquals(item, in));
    }
}
