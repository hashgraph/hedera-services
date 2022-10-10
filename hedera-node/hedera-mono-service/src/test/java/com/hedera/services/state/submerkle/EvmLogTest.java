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
package com.hedera.services.state.submerkle;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.hedera.services.utils.EntityNum;
import com.swirlds.common.utility.CommonUtils;
import java.util.Collections;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.log.LogsBloomFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvmLogTest {
    private static final byte[] data = "hgfedcba".getBytes();
    private static final byte[] otherData = "abcdefgh".getBytes();
    private static final byte[] bloom = "ijklmnopqrstuvwxyz".getBytes();
    private static final EntityNum aSourceNum = EntityNum.fromLong(3L);
    private static final EntityId aLoggerId = aSourceNum.toId().asEntityId();
    private static final List<byte[]> aTopics =
            List.of(
                    "first000000000000000000000000000".getBytes(),
                    "second00000000000000000000000000".getBytes(),
                    "third000000000000000000000000000".getBytes());

    private EvmLog subject;

    @BeforeEach
    void setup() {
        subject = new EvmLog(aLoggerId, bloom, aTopics, data);
    }

    @Test
    void convertsFromBesuAsExpected() {
        final var aSource =
                new Log(
                        aSourceNum.toEvmAddress(),
                        Bytes.wrap(data),
                        aTopics.stream().map(bytes -> LogTopic.of(Bytes.wrap(bytes))).toList());
        final var aBloom = bloomFor(aSource);
        subject.setBloom(aBloom);

        final var converted = EvmLog.fromBesu(aSource);

        assertEquals(subject, converted);
    }

    @Test
    void convertsFromTwoBesuAsExpected() {
        final var aSource =
                new Log(
                        aSourceNum.toEvmAddress(),
                        Bytes.wrap(data),
                        aTopics.stream().map(bytes -> LogTopic.of(Bytes.wrap(bytes))).toList());
        final var aBloom = bloomFor(aSource);
        final var bSourceNum = EntityNum.fromLong(666);
        final var bSource =
                new Log(bSourceNum.toEvmAddress(), Bytes.wrap(otherData), Collections.emptyList());
        final var bBloom = bloomFor(bSource);

        final var expected =
                List.of(
                        new EvmLog(aSourceNum.toId().asEntityId(), aBloom, aTopics, data),
                        new EvmLog(
                                bSourceNum.toId().asEntityId(),
                                bBloom,
                                Collections.emptyList(),
                                otherData));

        final var converted = EvmLog.fromBesu(List.of(aSource, bSource));

        assertEquals(expected, converted);
    }

    @Test
    void convertsEmptyLogs() {
        assertEquals(List.of(), EvmLog.fromBesu(Collections.emptyList()));
    }

    @Test
    void equalsSame() {
        final var sameButDifferent = subject;
        assertEquals(subject, sameButDifferent);
    }

    @Test
    void areSameTopicsBadScenarios() {
        List<byte[]> differentTopics = List.of("first".getBytes(), "second".getBytes());
        List<byte[]> sameButDifferentTopics =
                List.of("first".getBytes(), "second".getBytes(), "thirds".getBytes());

        EvmLog copy = new EvmLog(aLoggerId, bloom, differentTopics, data);
        EvmLog sameButDifferentCopy = new EvmLog(aLoggerId, bloom, sameButDifferentTopics, data);

        assertNotEquals(subject, copy);
        assertNotEquals(subject, sameButDifferentCopy);
    }

    @Test
    void toStringWorks() {
        assertEquals(
                "EvmLog{data="
                        + CommonUtils.hex(data)
                        + ", "
                        + "bloom="
                        + CommonUtils.hex(bloom)
                        + ", "
                        + "contractId="
                        + aLoggerId
                        + ", "
                        + "topics="
                        + aTopics.stream().map(CommonUtils::hex).collect(toList())
                        + "}",
                subject.toString());
    }

    @Test
    void objectContractWorks() {
        final var one = subject;
        final var two = new EvmLog(aLoggerId, bloom, aTopics, otherData);
        final var three = new EvmLog(aLoggerId, bloom, aTopics, data);

        assertNotEquals(null, one);
        assertNotEquals(new Object(), one);
        assertNotEquals(one, two);
        assertEquals(one, three);

        assertNotEquals(one.hashCode(), two.hashCode());
        assertEquals(one.hashCode(), three.hashCode());
    }

    @Test
    void beanWorks() {
        assertEquals(
                new EvmLog(
                        subject.getContractId(),
                        subject.getBloom(),
                        subject.getTopics(),
                        subject.getData()),
                subject);
    }

    @Test
    void serializableDetWorks() {
        assertEquals(EvmLog.MERKLE_VERSION, subject.getVersion());
        assertEquals(EvmLog.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
    }

    static byte[] bloomFor(final Log log) {
        return LogsBloomFilter.builder().insertLog(log).build().toArray();
    }
}
