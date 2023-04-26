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

package com.hedera.node.app.spi.records;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import org.junit.jupiter.api.Test;

class UniversalRecordBuilderTest {
    private TestRecordBuilder subject = new TestRecordBuilder();

    @Test
    void throwsGettingStatusIfNotSet() {
        // expect:
        assertThrows(IllegalStateException.class, subject::getFinalStatus);
    }

    @Test
    void canGetSetStatus() {
        subject.setFinalStatus(ResponseCodeEnum.SUCCESS);

        assertEquals(ResponseCodeEnum.SUCCESS, subject.getFinalStatus());
    }

    private static class TestRecordBuilder extends UniversalRecordBuilder<TestRecordBuilder> {
        @Override
        protected TestRecordBuilder self() {
            return this;
        }
    }
}
