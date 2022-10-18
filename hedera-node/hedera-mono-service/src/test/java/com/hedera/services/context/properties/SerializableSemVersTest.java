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
package com.hedera.services.context.properties;

import static com.hedera.services.context.properties.SerializableSemVers.SEM_VER_COMPARATOR;
import static com.hedera.services.context.properties.SerializableSemVersSerdeTest.assertEqualVersions;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.swirlds.common.system.SoftwareVersion;
import javax.annotation.Nullable;
import org.junit.jupiter.api.Test;

class SerializableSemVersTest {
    @Test
    void metaAsExpected() {
        final var subject = new SerializableSemVers();
        assertEquals(SerializableSemVers.CLASS_ID, subject.getClassId());
        assertEquals(SerializableSemVers.RELEASE_027_VERSION, subject.getVersion());
        assertEquals(SerializableSemVers.RELEASE_027_VERSION, subject.getMinimumSupportedVersion());
    }

    @Test
    void factoryAsExpected() {
        final var proto = semVerWith(1, 2, 3, null, null);
        final var services = semVerWith(3, 2, 1, "pre", "build");
        final var expected = new SerializableSemVers(proto, services);
        final var actual = SerializableSemVers.forHapiAndHedera("1.2.3", "3.2.1-pre+build");
        assertEqualVersions(expected, actual);
    }

    @Test
    void ordersWithRespectToAlphaNumber() {
        final var alpha1 = semVerWith(1, 9, 9, "alpha.1", null);
        final var alpha0 = semVerWith(1, 9, 9, "alpha.0", null);
        assertTrue(SEM_VER_COMPARATOR.compare(alpha0, alpha1) < 0);
    }

    @Test
    void comparatorPrioritizesOrderAsExpected() {
        assertTrue(
                SEM_VER_COMPARATOR.compare(
                                semVerWith(1, 9, 9, "pre", "build"),
                                semVerWith(2, 0, 0, null, null))
                        < 0);
        assertTrue(
                SEM_VER_COMPARATOR.compare(
                                semVerWith(1, 0, 9, "pre", "build"),
                                semVerWith(1, 9, 0, null, null))
                        < 0);
        assertTrue(
                SEM_VER_COMPARATOR.compare(
                                semVerWith(1, 0, 0, "pre", "build"),
                                semVerWith(1, 0, 1, null, null))
                        < 0);
        assertTrue(
                SEM_VER_COMPARATOR.compare(
                                semVerWith(1, 0, 1, "alpha.12345", null),
                                semVerWith(1, 0, 1, null, "build"))
                        < 0);
        assertTrue(
                SEM_VER_COMPARATOR.compare(
                                semVerWith(1, 0, 1, null, "build"), semVerWith(1, 0, 1, null, null))
                        < 0);
    }

    @Test
    void toStringAsExpected() {
        final var noPreNoBuild = semVerWith(1, 2, 3, null, null);
        final var justPreNoBuild = semVerWith(1, 2, 3, "pre", null);
        final var justBuildNoPre = semVerWith(1, 2, 3, null, "build");
        final var buildAndPre = semVerWith(1, 2, 3, "pre", "build");

        final var subject = new SerializableSemVers(noPreNoBuild, noPreNoBuild);

        assertEquals("Services @ 1.2.3 | HAPI @ 1.2.3", subject.toString());

        subject.setProto(justPreNoBuild);
        subject.setServices(justPreNoBuild);
        assertEquals("Services @ 1.2.3-pre | HAPI @ 1.2.3-pre", subject.toString());

        subject.setProto(justBuildNoPre);
        subject.setServices(justBuildNoPre);
        assertEquals("Services @ 1.2.3+build | HAPI @ 1.2.3+build", subject.toString());

        subject.setProto(buildAndPre);
        subject.setServices(buildAndPre);
        assertEquals("Services @ 1.2.3-pre+build | HAPI @ 1.2.3-pre+build", subject.toString());
    }

    @Test
    void prioritizesServicesVersionForComparison() {
        final var earlierServices = semVerWith(1, 0, 1, null, null);
        final var laterServices = semVerWith(1, 1, 0, null, null);
        final var earlierProto = semVerWith(1, 0, 1, null, null);
        final var laterProto = semVerWith(1, 1, 0, null, null);

        final var a = new SerializableSemVers(earlierProto, laterServices);
        final var b = new SerializableSemVers(laterProto, earlierServices);
        final var c = new SerializableSemVers(laterProto, laterServices);
        final var d = new SerializableSemVers(earlierProto, laterServices);

        assertTrue(b.compareTo(a) < 0);
        assertTrue(c.compareTo(a) > 0);
        assertEquals(0, a.compareTo(d));
        assertTrue(a.isAfter(null));
        assertFalse(a.isBefore(null));
    }

    @Test
    void answersTruthfullyOnPendingMigrationRecords() {
        final var someProto = semVerWith(1, 1, 1, null, null);
        final var saved027Version = semVerWith(0, 27, 7, null, null);
        final var saved028Version = semVerWith(0, 28, 5, null, null);
        final var patch0286 = semVerWith(0, 28, 6, null, null);

        final var subject = new SerializableSemVers(someProto, patch0286);
        final var v0277 = new SerializableSemVers(someProto, saved027Version);
        final var v0285 = new SerializableSemVers(someProto, saved028Version);

        assertTrue(subject.hasMigrationRecordsFrom(v0277));
        SerializableSemVers.setCurrentVersionHasPatchMigrationRecords(true);
        assertTrue(subject.hasMigrationRecordsFrom(v0285));
        assertFalse(subject.hasMigrationRecordsFrom(subject));
    }

    @Test
    void detectsNonPatchServicesUpgrades() {
        final var services = semVerWith(1, 2, 3, null, null);
        final var servicesPatch = semVerWith(1, 2, 4, null, null);
        final var servicesMinorUpgrade = semVerWith(1, 3, 3, null, null);
        final var servicesMajorUpgrade = semVerWith(2, 2, 3, null, null);
        final var someProto = semVerWith(1, 1, 1, null, null);

        final var base = new SerializableSemVers(someProto, services);
        final var patch = new SerializableSemVers(someProto, servicesPatch);
        final var minor = new SerializableSemVers(someProto, servicesMinorUpgrade);
        final var major = new SerializableSemVers(someProto, servicesMajorUpgrade);
        final var mockVersion = mock(SoftwareVersion.class);

        assertTrue(base.isNonPatchUpgradeFrom(null));
        assertFalse(base.isNonPatchUpgradeFrom(base));
        assertFalse(patch.isNonPatchUpgradeFrom(base));
        assertTrue(minor.isNonPatchUpgradeFrom(base));
        assertTrue(major.isNonPatchUpgradeFrom(base));
        assertThrows(IllegalArgumentException.class, () -> base.isNonPatchUpgradeFrom(mockVersion));
    }

    @Test
    void canOnlyCompareIfDeserializedAndToOwnType() {
        final var services = semVerWith(1, 1, 0, null, null);
        final var proto = semVerWith(1, 0, 1, null, null);

        final var mockVersion = mock(SoftwareVersion.class);
        final var firstUnpreparedSubject = new SerializableSemVers(proto, services);
        final var secondUnpreparedSubject = new SerializableSemVers(proto, services);
        firstUnpreparedSubject.setProto(null);
        assertThrows(
                IllegalStateException.class, () -> firstUnpreparedSubject.compareTo(mockVersion));
        secondUnpreparedSubject.setServices(null);
        assertThrows(
                IllegalStateException.class, () -> secondUnpreparedSubject.compareTo(mockVersion));
        final var preparedSubject =
                new SerializableSemVers(
                        semVerWith(1, 0, 1, null, null), semVerWith(1, 0, 1, null, null));
        assertThrows(IllegalArgumentException.class, () -> preparedSubject.compareTo(mockVersion));
    }

    private static SemanticVersion semVerWith(
            final int major,
            final int minor,
            final int patch,
            @Nullable final String pre,
            @Nullable final String build) {
        final var ans = SemanticVersion.newBuilder();
        ans.setMajor(major).setMinor(minor).setPatch(patch);
        if (pre != null) {
            ans.setPre(pre);
        }
        if (build != null) {
            ans.setBuild(build);
        }
        return ans.build();
    }
}
