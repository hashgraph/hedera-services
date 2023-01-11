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

import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HederaFileNumbersTest {
    private HederaFileNumbers subject;

    @BeforeEach
    void setUp() {
        subject =
                new HederaFileNumbers() {
                    @Override
                    public long addressBook() {
                        throw new IllegalCallerException("Should not be called here");
                    }

                    @Override
                    public long nodeDetails() {
                        throw new IllegalCallerException("Should not be called here");
                    }

                    @Override
                    public long feeSchedules() {
                        throw new IllegalCallerException("Should not be called here");
                    }

                    @Override
                    public long exchangeRates() {
                        throw new IllegalCallerException("Should not be called here");
                    }

                    @Override
                    public long applicationProperties() {
                        throw new IllegalCallerException("Should not be called here");
                    }

                    @Override
                    public long apiPermissions() {
                        throw new IllegalCallerException("Should not be called here");
                    }

                    @Override
                    public long firstSoftwareUpdateFile() {
                        return 150L;
                    }

                    @Override
                    public long lastSoftwareUpdateFile() {
                        return 159L;
                    }

                    @Override
                    public long throttleDefinitions() {
                        throw new IllegalCallerException("Should not be called here");
                    }
                };
    }

    @Test
    void checksIfNumIsSuperuser() {
        assertTrue(subject.isSoftwareUpdateFile(150L));
        assertTrue(subject.isSoftwareUpdateFile(155L));
        assertTrue(subject.isSoftwareUpdateFile(159L));
        assertFalse(subject.isSoftwareUpdateFile(160L));
        assertFalse(subject.isSoftwareUpdateFile(149L));
    }
}
