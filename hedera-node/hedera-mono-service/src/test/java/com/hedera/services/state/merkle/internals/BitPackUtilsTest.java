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
package com.hedera.services.state.merkle.internals;

import static com.hedera.services.state.merkle.internals.BitPackUtils.MAX_NUM_ALLOWED;
import static com.hedera.services.state.merkle.internals.BitPackUtils.buildAutomaticAssociationMetaData;
import static com.hedera.services.state.merkle.internals.BitPackUtils.getAlreadyUsedAutomaticAssociationsFrom;
import static com.hedera.services.state.merkle.internals.BitPackUtils.getMaxAutomaticAssociationsFrom;
import static com.hedera.services.state.merkle.internals.BitPackUtils.isValidNum;
import static com.hedera.services.state.merkle.internals.BitPackUtils.packedTime;
import static com.hedera.services.state.merkle.internals.BitPackUtils.setAlreadyUsedAutomaticAssociationsTo;
import static com.hedera.services.state.merkle.internals.BitPackUtils.setMaxAutomaticAssociationsTo;
import static com.hedera.services.state.merkle.internals.BitPackUtils.signedLowOrder32From;
import static com.hedera.services.state.merkle.internals.BitPackUtils.unsignedHighOrder32From;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class BitPackUtilsTest {
    private final int maxAutoAssociations = 123;
    private final int alreadyUsedAutomaticAssociations = 12;
    private int metadata =
            buildAutomaticAssociationMetaData(
                    maxAutoAssociations, alreadyUsedAutomaticAssociations);

    @Test
    void numFromCodeWorks() {
        // expect:
        assertEquals(MAX_NUM_ALLOWED, BitPackUtils.numFromCode((int) MAX_NUM_ALLOWED));
    }

    @Test
    void codeFromNumWorks() {
        // expect:
        assertEquals((int) MAX_NUM_ALLOWED, BitPackUtils.codeFromNum(MAX_NUM_ALLOWED));
    }

    @Test
    void codeFromNumThrowsWhenOutOfRange() {
        // expect:
        assertThrows(IllegalArgumentException.class, () -> BitPackUtils.codeFromNum(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> BitPackUtils.codeFromNum(MAX_NUM_ALLOWED + 1));
    }

    @Test
    void throwsWhenArgOutOfRange() {
        // expect:
        assertDoesNotThrow(() -> BitPackUtils.assertValid(MAX_NUM_ALLOWED));
        assertThrows(IllegalArgumentException.class, () -> BitPackUtils.assertValid(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> BitPackUtils.assertValid(MAX_NUM_ALLOWED + 1));
    }

    @Test
    void timePackingWorks() {
        // given:
        final var distantFuture = Instant.ofEpochSecond(MAX_NUM_ALLOWED, 999_999_999);

        // when:
        final var packed = packedTime(distantFuture.getEpochSecond(), distantFuture.getNano());
        // and:
        final var unpacked =
                Instant.ofEpochSecond(
                        unsignedHighOrder32From(packed), signedLowOrder32From(packed));

        // then:
        assertEquals(distantFuture, unpacked);
    }

    @Test
    void longPackingWorks() {
        // setup:
        final long bigNumA = MAX_NUM_ALLOWED;
        final long bigNumB = MAX_NUM_ALLOWED - 1;

        // given:
        final var packed = BitPackUtils.packedNums(bigNumA, bigNumB);

        // when:
        final var unpackedA = BitPackUtils.unsignedHighOrder32From(packed);
        final var unpackedB = BitPackUtils.unsignedLowOrder32From(packed);

        // then:
        assertEquals(bigNumA, unpackedA);
        assertEquals(bigNumB, unpackedB);
    }

    @Test
    void longPackingValidatesArgs() {
        // setup:
        final long overlyBigNum = MAX_NUM_ALLOWED + 1;
        final long bigNum = MAX_NUM_ALLOWED;

        // given:
        assertThrows(
                IllegalArgumentException.class,
                () -> BitPackUtils.packedNums(overlyBigNum, bigNum));
        assertThrows(
                IllegalArgumentException.class,
                () -> BitPackUtils.packedNums(bigNum, overlyBigNum));
    }

    @Test
    void cantPackTooFarFuture() {
        // expect:
        assertThrows(IllegalArgumentException.class, () -> packedTime(MAX_NUM_ALLOWED + 1, 0));
    }

    @Test
    void automaticAssociationsMetaWorks() {
        assertEquals(maxAutoAssociations, getMaxAutomaticAssociationsFrom(metadata));
        assertEquals(
                alreadyUsedAutomaticAssociations,
                getAlreadyUsedAutomaticAssociationsFrom(metadata));
    }

    @Test
    void automaticAssociationSettersWork() {
        int newMax = maxAutoAssociations + alreadyUsedAutomaticAssociations;

        metadata = setMaxAutomaticAssociationsTo(metadata, newMax);

        assertEquals(newMax, getMaxAutomaticAssociationsFrom(metadata));
        assertEquals(
                alreadyUsedAutomaticAssociations,
                getAlreadyUsedAutomaticAssociationsFrom(metadata));

        int newAlreadyAutoAssociations =
                alreadyUsedAutomaticAssociations + alreadyUsedAutomaticAssociations;

        metadata = setAlreadyUsedAutomaticAssociationsTo(metadata, newAlreadyAutoAssociations);

        assertEquals(newMax, getMaxAutomaticAssociationsFrom(metadata));
        assertEquals(newAlreadyAutoAssociations, getAlreadyUsedAutomaticAssociationsFrom(metadata));
    }

    @Test
    void validateLong() {
        assertFalse(isValidNum(MAX_NUM_ALLOWED + 10));
        assertTrue(isValidNum(MAX_NUM_ALLOWED));
        assertTrue(isValidNum(0L));
        assertFalse(isValidNum(-1L));
    }
}
