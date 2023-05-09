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

package com.hedera.node.app.service.util.impl.test.records;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.hedera.node.app.service.util.impl.records.UtilPrngRecordBuilder;
import com.hedera.node.app.spi.fixtures.TestBase;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UtilPrngRecordBuilderTest {
    private UtilPrngRecordBuilder subject;
    private static final Random random = new Random(92399921);

    @BeforeEach
    void setUp() {
        subject = new UtilPrngRecordBuilder();
    }

    @Test
    void emptyConstructor() {
        assertThat(subject.getPrngBytes()).isNull();
        assertThat(subject.getPrngNumber()).isNull();
        assertThat(subject.hasPrngNumber()).isFalse();
    }

    @Test
    void gettersAndSettersForBytesWork() {
        final var randomByteArray = TestBase.randomBytes(random, 48);
        final var randomBytes = Bytes.wrap(randomByteArray);

        subject.setPrngBytes(randomBytes);

        assertThat(subject.getPrngBytes()).isEqualTo(randomBytes);
        assertThat(subject.hasPrngNumber()).isFalse();
        assertThat(subject.hasPrngBytes()).isTrue();
    }

    @Test
    void gettersAndSettersForNumberWork() {
        subject.setPrngNumber(123456789);

        assertThat(subject.getPrngNumber()).isEqualTo(123456789);
        assertThat(subject.hasPrngNumber()).isTrue();
        assertThat(subject.hasPrngBytes()).isFalse();
    }
}
