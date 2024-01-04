/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.context.properties;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(LogCaptureExtension.class)
class SemanticVersionsTest {
    @LoggingTarget
    private LogCaptor logCaptor;

    @LoggingSubject
    private SemanticVersions subject = SemanticVersions.SEMANTIC_VERSIONS;

    @Test
    void canParseFullSemver() {
        final var literal = "1.2.4-alpha.1+2b26be40";
        final var expected = baseSemVer().setPre("alpha.1").setBuild("2b26be40").build();

        final var actual = SemanticVersions.asSemVer(literal);

        assertEquals(expected, actual);
    }

    @Test
    void serializableViewWorks() {
        assertInstanceOf(SerializableSemVers.class, subject.deployedSoftwareVersion());
    }

    @Test
    void canParseWithoutBuildMeta() {
        final var literal = "1.2.4-alpha.1";
        final var expected = baseSemVer().setPre("alpha.1").build();

        final var actual = SemanticVersions.asSemVer(literal);

        assertEquals(expected, actual);
    }

    @Test
    void canParseWithoutJustReqFields() {
        final var literal = "1.2.4";
        final var expected = baseSemVer().build();

        final var actual = SemanticVersions.asSemVer(literal);

        assertEquals(expected, actual);
    }

    @Test
    void canParseWithoutJustBuildMeta() {
        final var literal = "1.2.4+2b26be40";
        final var expected = baseSemVer().setBuild("2b26be40").build();

        final var actual = SemanticVersions.asSemVer(literal);

        assertEquals(expected, actual);
    }

    @Test
    void throwsIaeWithInvalidLiteral() {
        final var literal = "1.2..4+2b26be40";

        assertThrows(IllegalArgumentException.class, () -> SemanticVersions.asSemVer(literal));
    }

    @Test
    void recognizesAvailableResourceAndDoesItOnlyOnce() {
        final var versions = subject.getDeployed();
        final var sameVersions = subject.getDeployed();

        assertSame(versions, sameVersions);
    }

    @Test
    void warnsOfUnavailableSemversAndUsesEmpty() {
        final var shouldBeEmpty =
                SemanticVersions.fromResource("nonExistent.properties", "w/e", "n/a", "hedera.config.version");
        final var desiredPrefix =
                "Failed to parse resource 'nonExistent.properties' (keys 'w/e' and 'n/a') and resource 'bootstrap.properties' (key 'hedera.config.version'). Version info will be unavailable! java.lang.NullPointerException: inStream parameter is null";

        assertEquals(SemanticVersion.getDefaultInstance(), shouldBeEmpty.hederaSemVer());
        assertEquals(SemanticVersion.getDefaultInstance(), shouldBeEmpty.protoSemVer());
        assertThat(logCaptor.warnLogs(), contains(Matchers.startsWith(desiredPrefix)));
    }

    @Test
    void warnsOfUnavailableSemversAndUsesEmptyConfig() {
        final var shouldBeEmpty = SemanticVersions.fromResource(
                "nonExistent.properties", "hapi.proto.version", "hedera.services.version", "test");
        final var desiredPrefix =
                "Failed to parse resource 'nonExistent.properties' (keys 'hapi.proto.version' and 'hedera.services.version') and resource 'bootstrap.properties' (key 'test'). Version info will be unavailable! java.lang.NullPointerException: inStream parameter is null";

        assertEquals(SemanticVersion.getDefaultInstance(), shouldBeEmpty.hederaSemVer());
        assertEquals(SemanticVersion.getDefaultInstance(), shouldBeEmpty.protoSemVer());
        assertThat(logCaptor.warnLogs(), contains(Matchers.startsWith(desiredPrefix)));
    }

    @Test
    void doesntAppendBuildWhenConfigVersionIsZero() {
        final var version = SemanticVersions.fromResource(
                "semantic-version.properties",
                "hapi.proto.version",
                "hedera.services.version",
                "hedera.config.version");
        assertTrue(version.hederaSemVer().getBuild().isEmpty());
    }

    @Test
    void doesntAppendBuildWhenConfigVersionIsEmpty() {
        final var hederaSemVer =
                SemanticVersion.newBuilder().setMajor(1).setMinor(2).setPatch(4).build();
        final var version = SemanticVersions.addConfigVersionToBuild("", hederaSemVer);
        assertTrue(version.getBuild().isEmpty());
    }

    @Test
    void appendsBuildWhenConfigVersionIsNonZero() {
        final var hederaSemVer =
                SemanticVersion.newBuilder().setMajor(1).setMinor(2).setPatch(4).build();
        final var version = SemanticVersions.addConfigVersionToBuild("10", hederaSemVer);
        assertEquals("10", version.getBuild());
    }

    @Test
    void appendsBuildCorrectly() {
        final var literal = "1.2.4+1";
        final var expected = baseSemVer().setBuild("1").build();

        final var actual = SemanticVersions.asSemVer(literal);

        assertEquals(expected, actual);
    }

    private static SemanticVersion.Builder baseSemVer() {
        return baseSemVer(1, 2, 4);
    }

    private static SemanticVersion.Builder baseSemVer(final int major, final int minor, final int patch) {
        return SemanticVersion.newBuilder().setMajor(major).setMinor(minor).setPatch(patch);
    }
}
