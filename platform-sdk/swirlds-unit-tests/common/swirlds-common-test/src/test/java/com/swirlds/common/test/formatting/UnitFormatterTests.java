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

package com.swirlds.common.test.formatting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.units.DataUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UnitFormatter Tests")
class UnitFormatterTests {

    @Test
    @DisplayName("Format Longs Test")
    void formatLongsTest() {

        assertEquals("0.0 b", DataUnit.UNIT_KILOBYTES.buildFormatter().render());
        assertEquals("1.0 KB", DataUnit.UNIT_KILOBYTES.buildFormatter(1).render());
        assertEquals(
                "1.0 kilobyte",
                DataUnit.UNIT_KILOBYTES.buildFormatter(1).setAbbreviate(false).render());
        assertEquals(
                "7.0 kilobytes",
                DataUnit.UNIT_KILOBYTES.buildFormatter(7).setAbbreviate(false).render());
        assertEquals(
                "0.0",
                DataUnit.UNIT_KILOBYTES.buildFormatter().setShowUnit(false).render());
        assertEquals(
                "1.0",
                DataUnit.UNIT_KILOBYTES.buildFormatter(1).setShowUnit(false).render());
        assertEquals(
                "1.0KB",
                DataUnit.UNIT_KILOBYTES
                        .buildFormatter(1)
                        .setShowSpaceInBetween(false)
                        .render());
        assertEquals(
                "7.0KB",
                DataUnit.UNIT_KILOBYTES
                        .buildFormatter(7)
                        .setShowSpaceInBetween(false)
                        .render());
        assertEquals(
                "1 KB",
                DataUnit.UNIT_KILOBYTES.buildFormatter(1).setDecimalPlaces(0).render());
        assertEquals(
                "7 KB",
                DataUnit.UNIT_KILOBYTES.buildFormatter(7).setDecimalPlaces(0).render());
        assertEquals(
                "1.00000 KB",
                DataUnit.UNIT_KILOBYTES.buildFormatter(1).setDecimalPlaces(5).render());
        assertEquals(
                "7.00000 KB",
                DataUnit.UNIT_KILOBYTES.buildFormatter(7).setDecimalPlaces(5).render());
        assertEquals("2.0 MB", DataUnit.UNIT_KILOBYTES.buildFormatter(1024 * 2).render());
        assertEquals(
                "2,048.0 KB",
                DataUnit.UNIT_KILOBYTES
                        .buildFormatter(1024 * 2)
                        .setSimplify(false)
                        .render());
    }

    @Test
    @DisplayName("Format Longs Test")
    void formatDoublesTest() {

        assertEquals(
                "0.0 b",
                DataUnit.UNIT_KILOBYTES.buildFormatter().setQuantity(0.0).render());
        assertEquals("1.0 KB", DataUnit.UNIT_KILOBYTES.buildFormatter(1.0).render());
        assertEquals(
                "1.0 kilobyte",
                DataUnit.UNIT_KILOBYTES.buildFormatter(1.0).setAbbreviate(false).render());
        assertEquals(
                "7.0 kilobytes",
                DataUnit.UNIT_KILOBYTES.buildFormatter(7.0).setAbbreviate(false).render());
        assertEquals(
                "0.0",
                DataUnit.UNIT_KILOBYTES.buildFormatter().setShowUnit(false).render());
        assertEquals(
                "1.0",
                DataUnit.UNIT_KILOBYTES.buildFormatter(1.0).setShowUnit(false).render());
        assertEquals(
                "1.0KB",
                DataUnit.UNIT_KILOBYTES
                        .buildFormatter(1.0)
                        .setShowSpaceInBetween(false)
                        .render());
        assertEquals(
                "7.0KB",
                DataUnit.UNIT_KILOBYTES
                        .buildFormatter(7.0)
                        .setShowSpaceInBetween(false)
                        .render());
        assertEquals(
                "1 KB",
                DataUnit.UNIT_KILOBYTES.buildFormatter(1.0).setDecimalPlaces(0).render());
        assertEquals(
                "7 KB",
                DataUnit.UNIT_KILOBYTES.buildFormatter(7.0).setDecimalPlaces(0).render());
        assertEquals(
                "1.00000 KB",
                DataUnit.UNIT_KILOBYTES.buildFormatter(1.0).setDecimalPlaces(5).render());
        assertEquals(
                "7.00000 KB",
                DataUnit.UNIT_KILOBYTES.buildFormatter(7.0).setDecimalPlaces(5).render());
        assertEquals(
                "2.0 MB", DataUnit.UNIT_KILOBYTES.buildFormatter(1024 * 2.0).render());
        assertEquals(
                "2,048.0 KB",
                DataUnit.UNIT_KILOBYTES
                        .buildFormatter(1024 * 2.0)
                        .setSimplify(false)
                        .render());
        assertEquals(
                "9.877 KB",
                DataUnit.UNIT_KILOBYTES
                        .buildFormatter(9.87654321)
                        .setDecimalPlaces(3)
                        .render());
        assertEquals(
                "10 KB",
                DataUnit.UNIT_KILOBYTES
                        .buildFormatter(9.87654321)
                        .setDecimalPlaces(0)
                        .render());
        assertEquals(
                "9.877 kilobytes",
                DataUnit.UNIT_KILOBYTES
                        .buildFormatter(9.87654321)
                        .setDecimalPlaces(3)
                        .setAbbreviate(false)
                        .render());
        // Edge case: number that isn't 1.0 but rounds to 1.0
        assertEquals(
                "1.000 kilobyte",
                DataUnit.UNIT_KILOBYTES
                        .buildFormatter(1.0000001)
                        .setDecimalPlaces(3)
                        .setAbbreviate(false)
                        .render());
        assertEquals(
                "1.0 kilobyte",
                DataUnit.UNIT_KILOBYTES
                        .buildFormatter(1.0000001)
                        .setAbbreviate(false)
                        .render());
        assertEquals(
                "1 kilobyte",
                DataUnit.UNIT_KILOBYTES
                        .buildFormatter(1.0000001)
                        .setDecimalPlaces(0)
                        .setAbbreviate(false)
                        .render());
    }
}
