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

package com.swirlds.common.test.utility;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.test.io.SerializationUtils;
import com.swirlds.common.utility.InvalidSemanticVersionException;
import com.swirlds.common.utility.SemanticVersion;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class SemanticVersionTest {

    private static final String EXPECTED_NOT_NULL = "Expected %s to be non-null, but was a null reference";
    private static final String VALUES_NOT_EQUAL = "Expected %s to be equal to the reference value, but was not equal";
    private static final String DOES_NOT_THROW = "Expected %s to not throw an exception, but an exception was thrown";
    private static final String FAILED_TO_THROW =
            "Expected %s to throw %s, but no exception of the correct type was " + "thrown";

    @ParameterizedTest(name = "[{index}] {0}")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @MethodSource("validVersionSupplier")
    void testValidSemanticVersionParsing(
            final String version,
            final int major,
            final int minor,
            final int patch,
            final String prerelease,
            final String build) {
        final AtomicReference<SemanticVersion> versionRef = new AtomicReference<>();
        assertDoesNotThrow(
                () -> versionRef.set(SemanticVersion.parse(version)),
                String.format(DOES_NOT_THROW, "SemanticVersion.parse()"));

        final SemanticVersion calculatedVersion = versionRef.get();
        assertNotNull(calculatedVersion, String.format(EXPECTED_NOT_NULL, "calculatedVersion"));
        assertEquals(major, calculatedVersion.major(), String.format(VALUES_NOT_EQUAL, "calculatedVersion.major()"));
        assertEquals(minor, calculatedVersion.minor(), String.format(VALUES_NOT_EQUAL, "calculatedVersion.minor()"));
        assertEquals(patch, calculatedVersion.patch(), String.format(VALUES_NOT_EQUAL, "calculatedVersion.patch()"));
        assertEquals(
                prerelease,
                calculatedVersion.prerelease(),
                String.format(VALUES_NOT_EQUAL, "calculatedVersion.prerelease()"));
        assertEquals(build, calculatedVersion.build(), String.format(VALUES_NOT_EQUAL, "calculatedVersion.build()"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @MethodSource("invalidVersionSupplier")
    void testInvalidSemanticVersionParsing(
            final String version,
            final int major,
            final int minor,
            final int patch,
            final String prerelease,
            final String build) {
        assertThrows(
                InvalidSemanticVersionException.class,
                () -> SemanticVersion.parse(version),
                String.format(
                        FAILED_TO_THROW,
                        "SemanticVersion.parse()",
                        InvalidSemanticVersionException.class.getSimpleName()));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @MethodSource("validVersionSupplier")
    void testSemanticVersionToString(
            final String version,
            final int major,
            final int minor,
            final int patch,
            final String prerelease,
            final String build) {
        final SemanticVersion semanticVersion = new SemanticVersion(major, minor, patch, prerelease, build);
        assertEquals(
                version, semanticVersion.toString(), String.format(VALUES_NOT_EQUAL, "semanticVersion.toString()"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @MethodSource("validVersionSupplier")
    void testSerialization(
            final String version,
            final int major,
            final int minor,
            final int patch,
            final String prerelease,
            final String build)
            throws IOException, ConstructableRegistryException {
        ConstructableRegistry.getInstance()
                .registerConstructable(new ClassConstructorPair(SemanticVersion.class, SemanticVersion::new));
        final SemanticVersion semanticVersion = new SemanticVersion(major, minor, patch, prerelease, build);
        final SemanticVersion copy = SerializationUtils.serializeDeserialize(semanticVersion);
        assertEquals(semanticVersion, copy, String.format(VALUES_NOT_EQUAL, "SemanticVersion"));
        assertEquals(
                0, semanticVersion.compareTo(copy), String.format(VALUES_NOT_EQUAL, "semanticVersion.compareTo(copy)"));
    }

    static Stream<Arguments> validVersionSupplier() {
        return Stream.of(
                Arguments.of("0.0.0", 0, 0, 0, "", ""),
                Arguments.of("0.0.0-SNAPSHOT", 0, 0, 0, "SNAPSHOT", ""),
                Arguments.of("0.0.0-alpha.1", 0, 0, 0, "alpha.1", ""),
                Arguments.of("0.0.0-alpha.1+3afefbd", 0, 0, 0, "alpha.1", "3afefbd"),
                Arguments.of("0.27.0", 0, 27, 0, "", ""),
                Arguments.of("0.27.0-SNAPSHOT", 0, 27, 0, "SNAPSHOT", ""),
                Arguments.of("0.27.0-alpha.2", 0, 27, 0, "alpha.2", ""),
                Arguments.of("0.27.0-alpha.1+3afefbd", 0, 27, 0, "alpha.1", "3afefbd"));
    }

    static Stream<Arguments> invalidVersionSupplier() {
        return Stream.of(
                Arguments.of("a.0.0", 0, 0, 0, "", ""),
                Arguments.of("0.c.0-SNAPSHOT", 0, 0, 0, "SNAPSHOT", ""),
                Arguments.of("0.0.0-alpha-1", 0, 0, 0, "alpha.1", ""),
                Arguments.of("0.0.0-+3afefbd", 0, 0, 0, "alpha.1", "3afefbd"),
                Arguments.of("0.27.0+", 0, 27, 0, "", ""),
                Arguments.of("0.27.0-,SNAPSHOT", 0, 27, 0, "SNAPSHOT", ""),
                Arguments.of("0.27.0-", 0, 27, 0, "alpha.2", ""),
                Arguments.of("0.27.0-alpha.1+", 0, 27, 0, "alpha.1", "3afefbd"));
    }
}
