/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hedera.services.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.context.properties.PropertySource;
import com.hedera.test.utils.IdUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FileNumbersTest {
    PropertySource properties;
    HederaNumbers hederaNumbers;

    FileNumbers subject;

    @BeforeEach
    private void setup() {
        properties = mock(PropertySource.class);
        hederaNumbers = mock(HederaNumbers.class);

        given(hederaNumbers.realm()).willReturn(24L);
        given(hederaNumbers.shard()).willReturn(42L);

        given(properties.getLongProperty("files.addressBook")).willReturn(101L);
        given(properties.getLongProperty("files.nodeDetails")).willReturn(102L);
        given(properties.getLongProperty("files.networkProperties")).willReturn(121L);
        given(properties.getLongProperty("files.hapiPermissions")).willReturn(122L);
        given(properties.getLongProperty("files.feeSchedules")).willReturn(111L);
        given(properties.getLongProperty("files.exchangeRates")).willReturn(112L);
        given(properties.getLongProperty("files.softwareUpdateZip")).willReturn(150L);
        given(properties.getLongProperty("files.throttleDefinitions")).willReturn(123L);
        given(properties.getEntityNumRange("files.softwareUpdateRange"))
                .willReturn(Pair.of(150L, 159L));

        subject = new FileNumbers(hederaNumbers, properties);
    }

    @Test
    void hasExpectedNumbers() {
        // expect:
        assertEquals(101, subject.addressBook());
        assertEquals(102, subject.nodeDetails());
        assertEquals(111, subject.feeSchedules());
        assertEquals(112, subject.exchangeRates());
        assertEquals(121, subject.applicationProperties());
        assertEquals(122, subject.apiPermissions());
        assertEquals(123, subject.throttleDefinitions());
        assertEquals(150L, subject.firstSoftwareUpdateFile());
        assertEquals(159L, subject.lastSoftwareUpdateFile());
    }

    @Test
    void knowsSpecialFiles() {
        assertFalse(subject.isSoftwareUpdateFile(149L));
        for (long sp = 150L; sp <= 159L; sp++) {
            assertTrue(subject.isSoftwareUpdateFile(sp));
        }
        assertFalse(subject.isSoftwareUpdateFile(160L));
    }

    @Test
    void getsExpectedFid() {
        // when:
        var fid = subject.toFid(3L);

        // then:
        assertEquals(IdUtils.asFile("42.24.3"), fid);
    }
}
