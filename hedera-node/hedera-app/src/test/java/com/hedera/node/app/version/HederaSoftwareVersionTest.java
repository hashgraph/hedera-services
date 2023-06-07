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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

final class HederaSoftwareVersionTest {
    private static final SemanticVersionConverter CONVERTER = new SemanticVersionConverter();

    @ParameterizedTest
    @CsvSource(textBlock = """
            0.0.1, 0.0.0
            1.0.0, 0.0.10
            """)
    @DisplayName("isAfter()")
    void isAfter(@NonNull final String a, @NonNull final String b) {
        final var versionA = new HederaSoftwareVersion(semver(a), semver(a));
        final var versionB = new HederaSoftwareVersion(semver(b), semver(b));

        assertThat(versionA.isBefore(versionB)).isFalse();
        assertThat(versionA.isAfter(versionB)).isTrue();
    }

    @ParameterizedTest
    @CsvSource(textBlock = """
            0.0.0, 0.0.1
            0.0.10, 1.0.0
            """)
    @DisplayName("isBefore()")
    void isBefore(@NonNull final String a, @NonNull final String b) {
        final var versionA = new HederaSoftwareVersion(semver(a), semver(a));
        final var versionB = new HederaSoftwareVersion(semver(b), semver(b));

        assertThat(versionA.isBefore(versionB)).isTrue();
        assertThat(versionA.isAfter(versionB)).isFalse();
    }

    @Test
    @DisplayName("Serialization")
    void serialization() throws IOException {
        final var version = new HederaSoftwareVersion(semver("1.2.3"), semver("4.5.6"));

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
