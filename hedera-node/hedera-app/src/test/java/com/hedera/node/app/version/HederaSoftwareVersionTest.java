/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.version;

import static com.hedera.node.app.version.HederaSoftwareVersion.RELEASE_027_VERSION;
import static com.swirlds.state.spi.HapiUtils.SEMANTIC_VERSION_COMPARATOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.config.converter.SemanticVersionConverter;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class HederaSoftwareVersionTest {
    private static final SemanticVersionConverter CONVERTER = new SemanticVersionConverter();

    @ParameterizedTest(name = "{0} {2} {1}")
    @CsvSource(
            textBlock =
                    """
            0.0.1,       0.0.0,        >
            1.0.0,       0.0.10,       >
            0.0.0,       0.0.1,        <
            0.0.10,      1.0.0,        <
            0.0.0,       0.0.0,        =
            1.2.3,       1.2.3-alpha.1,>
            1.2.4,       1.2.3-foo,    >
            1.2.2,       1.2.3-foo,    <
            1.2.3-alpha.1,       1.2.3-alpha.2+1,  <
            1.2.4,       1.2.3-foo+1,  >
            1.2.2,       1.2.3-foo+1,  <
            """)
    @DisplayName("compareTo()")
    void compareTo(@NonNull final String a, @NonNull final String b, final String expected) {
        final var versionA = new HederaSoftwareVersion(semver(a), semver(a), 0);
        final var versionB = new HederaSoftwareVersion(semver(b), semver(b), 0);

        switch (expected) {
            case "<" -> assertThat(versionA).isLessThan(versionB);
            case "=" -> assertThat(versionA).isEqualByComparingTo(versionB);
            case ">" -> assertThat(versionA).isGreaterThan(versionB);
            default -> throw new IllegalArgumentException("Unknown expected value: " + expected);
        }
        // Ensure that the PBJ versions are also ordered correctly.
        final SemanticVersion pbjA = versionA.servicesVersion();
        final SemanticVersion pbjB = versionB.servicesVersion();
        switch (expected) {
            case "<" -> assertThat(SEMANTIC_VERSION_COMPARATOR.compare(pbjA, pbjB))
                    .isLessThan(0);
            case "=" -> assertThat(SEMANTIC_VERSION_COMPARATOR.compare(pbjA, pbjB))
                    .isEqualTo(0);
            case ">" -> assertThat(SEMANTIC_VERSION_COMPARATOR.compare(pbjA, pbjB))
                    .isGreaterThan(0);
            default -> throw new IllegalArgumentException("Unknown expected value: " + expected);
        }
    }

    @Test
    void serializationRoundTripWithConfigVersionTest() throws IOException, ConstructableRegistryException {
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(HederaSoftwareVersion.class, HederaSoftwareVersion::new));

        final HederaSoftwareVersion v1 = new HederaSoftwareVersion(
                new SemanticVersion(0, 48, 0, "alpha.5", ""), new SemanticVersion(0, 48, 0, "", ""), 1);

        final HederaSoftwareVersion v2 = new HederaSoftwareVersion(
                new SemanticVersion(0, 48, 0, "alpha.5", ""), new SemanticVersion(0, 48, 0, "", ""), 1);

        assertEquals(0, v1.compareTo(v2));

        final ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        final SerializableDataOutputStream out = new SerializableDataOutputStream(byteOut);
        out.writeSerializable(v1, true);

        final SerializableDataInputStream in =
                new SerializableDataInputStream(new ByteArrayInputStream(byteOut.toByteArray()));
        final HederaSoftwareVersion v3 = in.readSerializable();

        assertEquals(0, v1.compareTo(v3));
    }

    @Test
    void byteFormatDoesNotChangeAfterMigration() throws IOException, ConstructableRegistryException {
        ConstructableRegistry.getInstance()
                .registerConstructable(
                        new ClassConstructorPair(HederaSoftwareVersion.class, HederaSoftwareVersion::new));

        /*
        // The following code was used to generate the serialized software version on disk.
        // File was generated using the branch release/0.47.

        final HederaSoftwareVersion version = new HederaSoftwareVersion(semver("1.2.3"), semver("4.5.6"));
        final FileOutputStream fos = new FileOutputStream("hederaSoftwareVersion_27.dat");
        final SerializableDataOutputStream out = new SerializableDataOutputStream(fos);
        out.writeSerializable(version, true);
        out.close();
         */

        final byte[] legacyBytes;
        try (final InputStream legacyFile =
                HederaSoftwareVersion.class.getClassLoader().getResourceAsStream("hederaSoftwareVersion_27.dat")) {
            assertNotNull(legacyFile);
            legacyBytes = legacyFile.readAllBytes();
        }

        final SerializableDataInputStream legacyIn =
                new SerializableDataInputStream(new ByteArrayInputStream(legacyBytes));
        final HederaSoftwareVersion deserializedVersion = legacyIn.readSerializable();

        assertEquals(RELEASE_027_VERSION, deserializedVersion.getVersion());
        assertEquals(semver("1.2.3"), deserializedVersion.getHapiVersion());
        assertEquals(semver("4.5.6-2147483647"), deserializedVersion.getPbjSemanticVersion());

        // Write the deserialized version back to a byte array. It should exactly match the original byte array.
        final ByteArrayOutputStream newBytes = new ByteArrayOutputStream();
        final SerializableDataOutputStream newOut = new SerializableDataOutputStream(newBytes);
        newOut.writeSerializable(deserializedVersion, true);
        newOut.close();
        final byte[] newBytesArray = newBytes.toByteArray();

        assertArrayEquals(legacyBytes, newBytesArray);
    }

    @Test
    @DisplayName("Sorting HederaSoftwareVersions")
    void sorting() {
        final var list = new ArrayList<HederaSoftwareVersion>();
        for (int i = 0; i < 20; i++) {
            list.add(new HederaSoftwareVersion(semver("1.2." + i), semver("1.2." + i), 0));
        }

        final var rand = new Random(3375);
        Collections.shuffle(list, rand);
        Collections.sort(list);

        for (int i = 0; i < 20; i++) {
            assertThat(list.get(i).getHapiVersion().patch()).isEqualTo(i);
        }
    }

    @Test
    @DisplayName("Serialization")
    void serialization() throws IOException {
        final var version = new HederaSoftwareVersion(semver("1.2.3"), semver("4.5.6"), 0);

        final var serializedBytes = new ByteArrayOutputStream();
        final var out = new SerializableDataOutputStream(serializedBytes);
        version.serialize(out);

        final var in = new SerializableDataInputStream(new ByteArrayInputStream(serializedBytes.toByteArray()));
        final var deserializedVersion = new HederaSoftwareVersion();
        deserializedVersion.deserialize(in, deserializedVersion.getVersion());

        assertThat(deserializedVersion.getHapiVersion()).isEqualTo(version.getHapiVersion());
        assertThat(deserializedVersion.getPbjSemanticVersion()).isEqualTo(version.getPbjSemanticVersion());
    }

    private SemanticVersion semver(@NonNull final String s) {
        return CONVERTER.convert(s);
    }
}
