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

public class HederaFileNumbersTest {
    private HederaFileNumbers subject;

    @BeforeEach
    void setUp(){
        subject = new HederaFileNumbers() {
            @Override
            public long addressBook() {
                return 101L;
            }

            @Override
            public long nodeDetails() {
                return 102L;
            }

            @Override
            public long feeSchedules() {
                return 111L;
            }

            @Override
            public long exchangeRates() {
                return 112L;
            }

            @Override
            public long applicationProperties() {
                return 121L;
            }

            @Override
            public long apiPermissions() {
                return 122L;
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
                return 123L;
            }
        };
    }

    @Test
    void checksIfNumIsSuperuser(){
        assertTrue(subject.isSoftwareUpdateFile(150L));
        assertTrue(subject.isSoftwareUpdateFile(159L));
        assertFalse(subject.isSoftwareUpdateFile(160L));
    }
}
