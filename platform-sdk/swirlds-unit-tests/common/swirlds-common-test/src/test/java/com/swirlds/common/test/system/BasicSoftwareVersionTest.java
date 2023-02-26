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

package com.swirlds.common.test.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.test.framework.TestTypeTags;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("Basic SoftwareVersion Tests")
class BasicSoftwareVersionTest {

    public final SoftwareVersion NO_VERSION = SoftwareVersion.NO_VERSION;
    public final SoftwareVersion VERSION_ONE = new BasicSoftwareVersion(1);
    public final SoftwareVersion VERSION_TWO = new BasicSoftwareVersion(2);

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Verify compareTo functionality")
    @SuppressWarnings("EqualsWithItself")
    void testCompareTo() {
        final int comparedToNoVersion = 1;
        final int comparedToSelf = 0;

        assertEquals(
                comparedToNoVersion,
                VERSION_ONE.compareTo(NO_VERSION),
                "Should always get back 1 when comparing to NO_VERSION.");
        assertEquals(
                comparedToNoVersion,
                VERSION_TWO.compareTo(NO_VERSION),
                "Should always get back 1 when comparing to NO_VERSION.");
        assertEquals(
                comparedToSelf, VERSION_ONE.compareTo(VERSION_ONE), "Should always get back 0 when comparing to self.");
        assertEquals(
                comparedToSelf, VERSION_TWO.compareTo(VERSION_TWO), "Should always get back 0 when comparing to self.");
        assertTrue(VERSION_ONE.compareTo(VERSION_TWO) < 0, "VERSION_ONE should be older than VERSION_TWO.");
        assertTrue(VERSION_TWO.compareTo(VERSION_ONE) > 0, "VERSION_TWO should be newer than VERSION_ONE.");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Verify toString functionality")
    void testToString() {
        assertEquals("1", VERSION_ONE.toString(), "VERSION_ONE not reporting its version as 1.");
        assertEquals("2", VERSION_TWO.toString(), "VERSION_TWO not reporting its version as 2.");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("A BasicSoftwareVersion is equal to itself")
    void selfEquals() {
        final var ver = new BasicSoftwareVersion(1);
        assertThat(ver).isEqualTo(ver);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Two BasicSoftwareVersion are equal if their versions are equal")
    void equals() {
        final var ver1 = new BasicSoftwareVersion(1);
        final var ver2 = new BasicSoftwareVersion(1);
        assertThat(ver1).isEqualTo(ver2);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("A BasicSoftwareVersion is not equal to null")
    void notEqualToNull() {
        final var ver = new BasicSoftwareVersion(1);
        assertThat(ver).isNotEqualTo(null);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Two BasicSoftwareVersion of different versions are not equal")
    void unequal() {
        final var ver1 = new BasicSoftwareVersion(1);
        final var ver2 = new BasicSoftwareVersion(2);
        assertThat(ver1).isNotEqualTo(ver2);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Two equal BasicSoftwareVersion produce the same hashcode")
    void hashCodeConsistentWithEquals() {
        final var ver1 = new BasicSoftwareVersion(1);
        final var ver2 = new BasicSoftwareVersion(1);
        assertThat(ver1).hasSameHashCodeAs(ver2);
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Two different BasicSoftwareVersion probably produce different hash codes")
    void differentHashCodesConsistentWithNotEquals() {
        final var ver1 = new BasicSoftwareVersion(1);
        final var ver2 = new BasicSoftwareVersion(2);
        assertThat(ver1.hashCode()).isNotEqualTo(ver2.hashCode());
    }
}
