/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.files;

import static com.hedera.services.files.DataMapFactory.dataMapFrom;
import static com.hedera.services.files.DataMapFactory.toFid;
import static com.hedera.services.files.DataMapFactory.toKeyString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.fees.calculation.FeeCalcUtilsTest;
import com.hedera.test.utils.IdUtils;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class DataMapFactoryTest {
    @Test
    void toFidConversionWorks() {
        // given:
        var key = "/666/f888";
        // and:
        var expected = IdUtils.asFile("0.666.888");

        // when:
        var actual = toFid(key);

        // then:
        assertEquals(expected, actual);
    }

    @Test
    void toKeyConversionWorks() {
        // given:
        var fid = IdUtils.asFile("0.2.3");
        // and:
        var expected = FeeCalcUtilsTest.pathOf(fid);

        // when:
        var actual = toKeyString(fid);

        // then:
        assertEquals(expected, actual);
    }

    private String asLegacyPath(String fid) {
        return FeeCalcUtilsTest.pathOf(IdUtils.asFile(fid));
    }

    @Test
    void productHasMapSemantics() {
        // setup:
        Map<String, byte[]> delegate = new HashMap<>();
        delegate.put(asLegacyPath("0.2.7"), "APRIORI".getBytes());
        // and:
        var fid1 = IdUtils.asFile("0.2.3");
        var fid2 = IdUtils.asFile("0.3333.4");
        var fid3 = IdUtils.asFile("0.4.555555");
        // and:
        var theData = "THE".getBytes();
        var someData = "SOME".getBytes();
        var moreData = "MORE".getBytes();

        // given:
        var dataMap = dataMapFrom(delegate);

        // when:
        dataMap.put(fid1, someData);
        dataMap.put(fid2, moreData);
        dataMap.put(fid3, theData);

        assertFalse(dataMap.isEmpty());
        assertEquals(4, dataMap.size());
        dataMap.remove(fid2);
        assertEquals(3, dataMap.size());
        assertEquals(
                "/2/f3->SOME, /4/f555555->THE, /2/f7->APRIORI",
                delegate.entrySet().stream()
                        .sorted(
                                Comparator.comparingLong(
                                        entry ->
                                                Long.parseLong(
                                                        entry.getKey()
                                                                .substring(
                                                                        entry.getKey().indexOf('f')
                                                                                + 1,
                                                                        entry.getKey().indexOf('f')
                                                                                + 2))))
                        .map(
                                entry ->
                                        String.format(
                                                "%s->%s",
                                                entry.getKey(), new String(entry.getValue())))
                        .collect(Collectors.joining(", ")));

        assertTrue(dataMap.containsKey(fid1));
        assertFalse(dataMap.containsKey(fid2));

        dataMap.clear();
        assertTrue(dataMap.isEmpty());
    }

    @Test
    void cannotBeConstructed() {
        // expect:
        assertThrows(IllegalStateException.class, DataMapFactory::new);
    }
}
