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

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.config.converter.SemanticVersionConverter;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
        assertThat(deserializedVersion.getServicesVersion()).isEqualTo(version.getServicesVersion());
    }

    private SemanticVersion semver(@NonNull final String s) {
        return CONVERTER.convert(s);
    }
}
