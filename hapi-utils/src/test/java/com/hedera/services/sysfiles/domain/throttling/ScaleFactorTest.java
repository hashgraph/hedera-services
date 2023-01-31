/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sysfiles.domain.throttling;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ScaleFactorTest {
    @CsvSource({"3:2,3,2", "1:5,1,5", "100:100,100,100"})
    @ParameterizedTest
    void parsesValidAsExpected(String literal, int numerator, int denominator) {
        final var subject = ScaleFactor.from(literal);

        assertEquals(numerator, subject.numerator());
        assertEquals(denominator, subject.denominator());
    }

    @CsvSource({"3:0", "15", "9223372036854775807:100", "1:-1", "-2:3"})
    @ParameterizedTest
    void throwsIaeOnInvalid(String invalidLiteral) {
        Assertions.assertThrows(
                IllegalArgumentException.class, () -> ScaleFactor.from(invalidLiteral));
    }

    @Test
    void scalesUpModestlyAsExpected() {
        final var subject = ScaleFactor.from("3:2");

        // expect:
        assertEquals(15, subject.scaling(10));
    }

    @Test
    void scalingUpHitsTheCeiling() {
        final var subject = ScaleFactor.from("2147483647:3");

        assertEquals(Integer.MAX_VALUE / 3, subject.scaling(2));
    }

    @Test
    void scalingDownHasFloor() {
        final var subject = ScaleFactor.from("1:3");

        assertEquals(1, subject.scaling(2));
    }

    @Test
    void toStringWorks() {
        final var subject = ScaleFactor.from("5:2");

        assertEquals("ScaleFactor{scale=5:2}", subject.toString());
    }

    @Test
    void comparabilityWorks() {
        final var a = ScaleFactor.from("7:3");
        final var b = ScaleFactor.from("9:4");
        final var c = ScaleFactor.from("21:9");

        assertTrue(a.compareTo(b) > 0);
        assertTrue(b.compareTo(c) < 0);
        assertEquals(0, a.compareTo(c));
    }

    @Test
    void objectContractWorks() {
        final var subject = ScaleFactor.from("3:2");
        final var equalSubject = ScaleFactor.from("3:2");
        final var unequalNumSubject = ScaleFactor.from("4:2");
        final var unequalDenomSubject = ScaleFactor.from("3:1");
        final var identicalSubject = subject;

        assertNotEquals(null, subject);
        assertNotEquals(new Object(), subject);
        assertNotEquals(subject, unequalNumSubject);
        assertNotEquals(subject, unequalDenomSubject);
        assertEquals(subject, equalSubject);
        assertEquals(subject, identicalSubject);

        assertEquals(subject.hashCode(), equalSubject.hashCode());
        assertNotEquals(subject.hashCode(), unequalNumSubject.hashCode());
    }
}
