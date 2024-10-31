/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.system.SoftwareVersion;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class ServicesSoftwareVersionTest {
    private static final int DEFAULT_CONFIG_VERSION = 0;
    private static final int OVERRIDE_CONFIG_VERSION = 1;

    private static final SemanticVersion EARLY = new SemanticVersion(0, 0, 1, "", "");
    private static final SemanticVersion MIDDLE = new SemanticVersion(0, 1, 0, "", "");
    private static final SemanticVersion MIDDLE_ALPHA_1 = new SemanticVersion(0, 1, 0, "alpha.1", "");
    private static final SemanticVersion MIDDLE_ALPHA_2 = new SemanticVersion(0, 1, 0, "alpha.2", "");
    private static final SemanticVersion LATE = new SemanticVersion(1, 0, 0, "", "");
    private static final SemanticVersion LATE_WITH_IGNORED_BUILD = new SemanticVersion(1, 0, 0, "", "ignored");
    private static final SemanticVersion LATE_WITH_CONFIG_VERSION = new SemanticVersion(1, 0, 0, "", "1");
    private static final SemanticVersion MISC = new SemanticVersion(1, 2, 3, "alpha.4", "5");

    @Test
    void returnsStateSemverFromPbjGetter() {
        final var expected = new SemanticVersion(0, 1, 0, "alpha.1", "1");
        final var subject = new ServicesSoftwareVersion(MIDDLE_ALPHA_1, OVERRIDE_CONFIG_VERSION);
        assertThat(subject.getPbjSemanticVersion()).isEqualTo(expected);
    }

    @Test
    void canOnlyCompareToKnownVersions() {
        assertThatThrownBy(() -> new ServicesSoftwareVersion(EARLY, DEFAULT_CONFIG_VERSION)
                        .compareTo(mock(SoftwareVersion.class)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageStartingWith("Unknown SoftwareVersion type");
    }

    @Test
    void alwaysLaterThanNull() {
        final var subject = new ServicesSoftwareVersion(EARLY, DEFAULT_CONFIG_VERSION);
        assertThat(subject.compareTo(null)).isGreaterThan(0);
    }

    @Test
    void alwaysLaterThanHederaSoftwareVersion() {
        final var prevVersion = new HederaSoftwareVersion(LATE, LATE, DEFAULT_CONFIG_VERSION);
        final var subject = new ServicesSoftwareVersion(EARLY, DEFAULT_CONFIG_VERSION);
        assertThat(subject.compareTo(prevVersion)).isGreaterThan(0);
    }

    @Test
    void majorIsLaterThanMinor() {
        final var prevVersion = new ServicesSoftwareVersion(MIDDLE, DEFAULT_CONFIG_VERSION);
        final var subject = new ServicesSoftwareVersion(LATE, DEFAULT_CONFIG_VERSION);
        assertThat(subject.compareTo(prevVersion)).isGreaterThan(0);
    }

    @Test
    void minorIsLaterThanPatch() {
        final var prevVersion = new ServicesSoftwareVersion(EARLY, DEFAULT_CONFIG_VERSION);
        final var subject = new ServicesSoftwareVersion(MIDDLE, DEFAULT_CONFIG_VERSION);
        assertThat(subject.compareTo(prevVersion)).isGreaterThan(0);
    }

    @Test
    void alphaIsBeforeNonAlpha() {
        final var prevVersion = new ServicesSoftwareVersion(MIDDLE, DEFAULT_CONFIG_VERSION);
        final var subject = new ServicesSoftwareVersion(MIDDLE_ALPHA_1, DEFAULT_CONFIG_VERSION);
        assertThat(subject.compareTo(prevVersion)).isLessThan(0);
    }

    @Test
    void alpha1IsBeforeAlpha2() {
        final var prevVersion = new ServicesSoftwareVersion(MIDDLE_ALPHA_1, DEFAULT_CONFIG_VERSION);
        final var subject = new ServicesSoftwareVersion(MIDDLE_ALPHA_2, DEFAULT_CONFIG_VERSION);
        assertThat(subject.compareTo(prevVersion)).isGreaterThan(0);
    }

    @Test
    void configVersionOverrides() {
        final var prevVersion = new ServicesSoftwareVersion(MIDDLE, DEFAULT_CONFIG_VERSION);
        final var subject = new ServicesSoftwareVersion(MIDDLE, OVERRIDE_CONFIG_VERSION);
        assertThat(subject.compareTo(prevVersion)).isGreaterThan(0);
    }

    @Test
    void nonNumericBuildIsNotConsidered() {
        final var prevVersion = new ServicesSoftwareVersion(LATE, DEFAULT_CONFIG_VERSION);
        final var subject = new ServicesSoftwareVersion(LATE_WITH_IGNORED_BUILD, DEFAULT_CONFIG_VERSION);
        assertThat(subject.compareTo(prevVersion)).isEqualTo(0);
    }

    @Test
    void numericBuildIsConsidered() {
        final var prevVersion = new ServicesSoftwareVersion(LATE, DEFAULT_CONFIG_VERSION);
        final var subject = new ServicesSoftwareVersion(LATE_WITH_CONFIG_VERSION);
        assertThat(subject.compareTo(prevVersion)).isEqualTo(1);
    }

    @Test
    void toStringIsSemverToString() {
        final var subject = new ServicesSoftwareVersion(MIDDLE_ALPHA_1, DEFAULT_CONFIG_VERSION);
        assertThat(subject.toString()).isEqualTo("SemanticVersion[major=0, minor=1, patch=0, pre=alpha.1, build=0]");
    }

    @Test
    void versionIsOne() {
        final var subject = new ServicesSoftwareVersion(MIDDLE_ALPHA_1, DEFAULT_CONFIG_VERSION);
        assertThat(subject.getClassVersion()).isEqualTo(1);
    }

    @Test
    void serdeWorks() throws IOException {
        final var baos = new ByteArrayOutputStream();
        final var out = new SerializableDataOutputStream(baos);
        final var subject = new ServicesSoftwareVersion(MISC);
        subject.serialize(out);
        out.flush();
        final var encoded = baos.toByteArray();
        final var in = new SerializableDataInputStream(new ByteArrayInputStream(encoded));
        final var recovered = new ServicesSoftwareVersion();
        recovered.deserialize(in, 1);
        assertThat(recovered.getPbjSemanticVersion()).isEqualTo(subject.getPbjSemanticVersion());
    }
}
