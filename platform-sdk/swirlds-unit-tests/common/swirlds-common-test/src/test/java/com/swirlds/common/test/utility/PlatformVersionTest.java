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
import com.swirlds.common.utility.PlatformVersion;
import com.swirlds.common.utility.SemanticVersion;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestTypeTags;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class PlatformVersionTest {

    private static final String EXPECTED_NOT_NULL = "Expected %s to be non-null, but was a null reference";
    private static final String VALUES_NOT_EQUAL = "Expected %s to be equal to the reference value, but was not equal";
    private static final String DOES_NOT_THROW = "Expected %s to not throw an exception, but an exception was thrown";
    private static final String FAILED_TO_THROW =
            "Expected %s to throw %s, but no exception of the correct type was " + "thrown";

    @ParameterizedTest(name = "[{index}] {0}")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @MethodSource("validJarFileSupplier")
    void testValidVersionFromJarFile(
            final Path file,
            final int major,
            final int minor,
            final int patch,
            final String prerelease,
            final String build,
            final String commitId) {
        final AtomicReference<PlatformVersion> versionRef = new AtomicReference<>();
        assertDoesNotThrow(
                () -> versionRef.set(PlatformVersion.fromJarFile(file)),
                String.format(DOES_NOT_THROW, "PlatformVersion.fromJarFile()"));

        final SemanticVersion expectedSemVer = new SemanticVersion(major, minor, patch, prerelease, build);
        final PlatformVersion version = versionRef.get();
        assertNotNull(version, String.format(EXPECTED_NOT_NULL, "version"));
        assertEquals(
                expectedSemVer, version.versionNumber(), String.format(VALUES_NOT_EQUAL, "version.versionNumber()"));
        assertEquals(commitId, version.commit(), String.format(VALUES_NOT_EQUAL, "version.commit()"));
        assertNotNull(version.license(), String.format(EXPECTED_NOT_NULL, "version.license()"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @MethodSource("validInputStreamSupplier")
    void testValidVersionFromStream(
            final InputStream stream,
            final int major,
            final int minor,
            final int patch,
            final String prerelease,
            final String build,
            final String commitId) {
        final AtomicReference<PlatformVersion> versionRef = new AtomicReference<>();
        assertDoesNotThrow(
                () -> versionRef.set(PlatformVersion.fromStream(stream)),
                String.format(DOES_NOT_THROW, "PlatformVersion.fromStream()"));

        final SemanticVersion expectedSemVer = new SemanticVersion(major, minor, patch, prerelease, build);
        final PlatformVersion version = versionRef.get();
        assertNotNull(version, String.format(EXPECTED_NOT_NULL, "version"));
        assertEquals(
                expectedSemVer, version.versionNumber(), String.format(VALUES_NOT_EQUAL, "version.versionNumber()"));
        assertEquals(commitId, version.commit(), String.format(VALUES_NOT_EQUAL, "version.commit()"));
        assertNotNull(version.license(), String.format(EXPECTED_NOT_NULL, "version.license()"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @MethodSource("validPropertiesSupplier")
    void testValidVersionFromProperties(
            final Properties properties,
            final int major,
            final int minor,
            final int patch,
            final String prerelease,
            final String build,
            final String commitId) {
        final AtomicReference<PlatformVersion> versionRef = new AtomicReference<>();
        assertDoesNotThrow(
                () -> versionRef.set(PlatformVersion.fromProperties(properties)),
                String.format(DOES_NOT_THROW, "PlatformVersion.fromProperties()"));

        final SemanticVersion expectedSemVer = new SemanticVersion(major, minor, patch, prerelease, build);
        final PlatformVersion version = versionRef.get();
        assertNotNull(version, String.format(EXPECTED_NOT_NULL, "version"));
        assertEquals(
                expectedSemVer, version.versionNumber(), String.format(VALUES_NOT_EQUAL, "version.versionNumber()"));
        assertEquals(commitId, version.commit(), String.format(VALUES_NOT_EQUAL, "version.commit()"));
        assertNotNull(version.license(), String.format(EXPECTED_NOT_NULL, "version.license()"));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @MethodSource("invalidJarFileSupplier")
    void testInvalidVersionFromJarFile(
            final Path file,
            final int major,
            final int minor,
            final int patch,
            final String prerelease,
            final String build,
            final String commitId) {
        assertThrows(
                InvalidSemanticVersionException.class,
                () -> PlatformVersion.fromJarFile(file),
                String.format(
                        FAILED_TO_THROW,
                        "PlatformVersion.fromJarFile()",
                        InvalidSemanticVersionException.class.getSimpleName()));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @MethodSource("invalidInputStreamSupplier")
    void testInvalidVersionFromStream(
            final InputStream stream,
            final int major,
            final int minor,
            final int patch,
            final String prerelease,
            final String build,
            final String commitId) {
        assertThrows(
                InvalidSemanticVersionException.class,
                () -> PlatformVersion.fromStream(stream),
                String.format(
                        FAILED_TO_THROW,
                        "PlatformVersion.fromStream()",
                        InvalidSemanticVersionException.class.getSimpleName()));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @MethodSource("invalidPropertiesSupplier")
    void testInvalidVersionFromProperties(
            final Properties properties,
            final int major,
            final int minor,
            final int patch,
            final String prerelease,
            final String build,
            final String commitId) {
        assertThrows(
                InvalidSemanticVersionException.class,
                () -> PlatformVersion.fromProperties(properties),
                String.format(
                        FAILED_TO_THROW,
                        "PlatformVersion.fromProperties()",
                        InvalidSemanticVersionException.class.getSimpleName()));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.PLATFORM)
    @MethodSource({"validPropertiesSupplier", "validInputStreamSupplier", "validJarFileSupplier"})
    void testSerialization(
            final Object ignored,
            final int major,
            final int minor,
            final int patch,
            final String prerelease,
            final String build,
            final String commitId)
            throws ConstructableRegistryException, IOException {
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();

        registry.registerConstructable(new ClassConstructorPair(SemanticVersion.class, SemanticVersion::new));
        registry.registerConstructable(new ClassConstructorPair(PlatformVersion.class, PlatformVersion::new));
        final PlatformVersion original =
                new PlatformVersion(new SemanticVersion(major, minor, patch, prerelease, build), commitId);
        final PlatformVersion copy = SerializationUtils.serializeDeserialize(original);
        assertEquals(original, copy, String.format(VALUES_NOT_EQUAL, "PlatformVersion"));
        assertEquals(0, original.compareTo(copy), String.format(VALUES_NOT_EQUAL, "PlatformVersion.compareTo(copy)"));
    }

    static Stream<Arguments> validInputStreamSupplier() {
        final ClassLoader loader = PlatformVersionTest.class.getClassLoader();
        assertNotNull(loader);

        final InputStream stream = loader.getResourceAsStream("platform/version/git.properties");
        assertNotNull(stream);

        return Stream.of(Arguments.of(stream, 0, 27, 0, "SNAPSHOT", "", "380ec7887491c0bf2d2089e968bdbef9fa8e8292"));
    }

    static Stream<Arguments> invalidInputStreamSupplier() {
        final ClassLoader loader = PlatformVersionTest.class.getClassLoader();
        assertNotNull(loader);

        final InputStream stream = loader.getResourceAsStream("platform/version/bad.git.properties");
        assertNotNull(stream);

        return Stream.of(Arguments.of(stream, 0, 27, 0, "SNAPSHOT", "", "380ec7887491c0bf2d2089e968bdbef9fa8e8292"));
    }

    static Stream<Arguments> validJarFileSupplier() throws URISyntaxException {
        final ClassLoader loader = PlatformVersionTest.class.getClassLoader();
        assertNotNull(loader);

        final URL resourceUrl = loader.getResource("platform/version/example-version-descriptor.jar");
        assertNotNull(resourceUrl);

        return Stream.of(Arguments.of(
                Path.of(resourceUrl.toURI()), 0, 27, 0, "SNAPSHOT", "", "380ec7887491c0bf2d2089e968bdbef9fa8e8292"));
    }

    static Stream<Arguments> invalidJarFileSupplier() throws URISyntaxException {
        final ClassLoader loader = PlatformVersionTest.class.getClassLoader();
        assertNotNull(loader);

        final URL resourceUrl = loader.getResource("platform/version/bad-version-descriptor.jar");
        assertNotNull(resourceUrl);

        return Stream.of(Arguments.of(
                Path.of(resourceUrl.toURI()), 0, 27, 0, "SNAPSHOT", "", "380ec7887491c0bf2d2089e968bdbef9fa8e8292"));
    }

    static Stream<Arguments> validPropertiesSupplier() {
        final ClassLoader loader = PlatformVersionTest.class.getClassLoader();
        assertNotNull(loader);

        final InputStream stream = loader.getResourceAsStream("platform/version/git.properties");
        assertNotNull(stream);

        final Properties properties = new Properties();
        assertDoesNotThrow(() -> properties.load(stream));

        return Stream.of(
                Arguments.of(properties, 0, 27, 0, "SNAPSHOT", "", "380ec7887491c0bf2d2089e968bdbef9fa8e8292"));
    }

    static Stream<Arguments> invalidPropertiesSupplier() {
        final ClassLoader loader = PlatformVersionTest.class.getClassLoader();
        assertNotNull(loader);

        final InputStream stream = loader.getResourceAsStream("platform/version/bad.git.properties");
        assertNotNull(stream);

        final Properties properties = new Properties();
        assertDoesNotThrow(() -> properties.load(stream));

        return Stream.of(
                Arguments.of(properties, 0, 27, 0, "SNAPSHOT", "", "380ec7887491c0bf2d2089e968bdbef9fa8e8292"));
    }
}
