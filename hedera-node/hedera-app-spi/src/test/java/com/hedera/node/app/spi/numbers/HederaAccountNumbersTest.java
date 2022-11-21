/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.numbers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

class HederaAccountNumbersTest {
    private HederaAccountNumbers subject;

    @BeforeEach
    void setUp(){
        subject = new HederaAccountNumbers() {
            @Override
            public long treasury() {
                return 2L;
            }

            @Override
            public long freezeAdmin() {
                return 58L;
            }

            @Override
            public long systemAdmin() {
                return 50L;
            }

            @Override
            public long addressBookAdmin() {
                return 55L;
            }

            @Override
            public long feeSchedulesAdmin() {
                return 56L;
            }

            @Override
            public long exchangeRatesAdmin() {
                return 57L;
            }

            @Override
            public long systemDeleteAdmin() {
                return 59L;
            }

            @Override
            public long systemUndeleteAdmin() {
                return 60L;
            }

            @Override
            public long stakingRewardAccount() {
                return 801L;
            }

            @Override
            public long nodeRewardAccount() {
                return 800L;
            }
        };
    }

    @Test
    void checksIfNumIsSuperuser(){
        assertTrue(subject.isSuperuser(2L));
        assertTrue(subject.isSuperuser(50L));
        assertFalse(subject.isSuperuser(51L));
    }
}
