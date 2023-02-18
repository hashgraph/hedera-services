/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.test.numbers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HederaAccountNumbersTest {
    private HederaAccountNumbers subject;

    @BeforeEach
    void setUp() {
        subject = new HederaAccountNumbers() {
            @Override
            public long treasury() {
                return 2L;
            }

            @Override
            public long freezeAdmin() {
                throw new IllegalCallerException("Should not be called here");
            }

            @Override
            public long systemAdmin() {
                return 50L;
            }

            @Override
            public long addressBookAdmin() {
                throw new IllegalCallerException("Should not be called here");
            }

            @Override
            public long feeSchedulesAdmin() {
                throw new IllegalCallerException("Should not be called here");
            }

            @Override
            public long exchangeRatesAdmin() {
                throw new IllegalCallerException("Should not be called here");
            }

            @Override
            public long systemDeleteAdmin() {
                throw new IllegalCallerException("Should not be called here");
            }

            @Override
            public long systemUndeleteAdmin() {
                throw new IllegalCallerException("Should not be called here");
            }

            @Override
            public long stakingRewardAccount() {
                throw new IllegalCallerException("Should not be called here");
            }

            @Override
            public long nodeRewardAccount() {
                throw new IllegalCallerException("Should not be called here");
            }
        };
    }

    @Test
    void checksIfNumIsSuperuser() {
        assertTrue(subject.isSuperuser(2L));
        assertTrue(subject.isSuperuser(50L));
        assertFalse(subject.isSuperuser(51L));
    }
}
