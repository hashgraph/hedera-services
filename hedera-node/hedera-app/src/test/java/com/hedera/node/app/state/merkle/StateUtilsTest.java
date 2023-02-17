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

package com.hedera.node.app.state.merkle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.node.app.spi.fixtures.state.TestSchema;
import com.hedera.node.app.spi.state.StateDefinition;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StateUtilsTest extends MerkleTestBase {

    @Test
    @DisplayName("Validating a null service name throws an NPE")
    void nullServiceNameThrows() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> StateUtils.validateServiceName(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Validating a service name with no characters throws an exception")
    void emptyServiceNameThrows() {
        assertThatThrownBy(() -> StateUtils.validateServiceName(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("service name");
    }

    @ParameterizedTest
    @MethodSource("illegalIdentifiers")
    @DisplayName("Service Names with illegal characters throw an exception")
    void invalidServiceNameThrows(final String serviceName) {
        assertThatThrownBy(() -> StateUtils.validateServiceName(serviceName))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("legalIdentifiers")
    @DisplayName("Service names with legal characters are valid")
    void validServiceNameWorks(final String serviceName) {
        assertThat(StateUtils.validateServiceName(serviceName)).isEqualTo(serviceName);
    }

    @Test
    @DisplayName("Validating a null state key throws an NPE")
    void nullStateKeyThrows() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> StateUtils.validateStateKey(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Validating a state key with no characters throws an exception")
    void emptyStateKeyThrows() {
        assertThatThrownBy(() -> StateUtils.validateStateKey(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("state key");
    }

    @ParameterizedTest
    @MethodSource("illegalIdentifiers")
    @DisplayName("State keys with illegal characters throw an exception")
    void invalidStateKeyThrows(final String stateKey) {
        assertThatThrownBy(() -> StateUtils.validateStateKey(stateKey)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("legalIdentifiers")
    @DisplayName("State keys with legal characters are valid")
    void validStateKeyWorks(final String stateKey) {
        assertThat(StateUtils.validateServiceName(stateKey)).isEqualTo(stateKey);
    }

    @Test
    @DisplayName("Validating a null identifier throws an NPE")
    void nullIdentifierThrows() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> StateUtils.validateIdentifier(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Validating an identifier with no characters throws an exception")
    void emptyIdentifierThrows() {
        assertThatThrownBy(() -> StateUtils.validateIdentifier("")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("illegalIdentifiers")
    @DisplayName("Identifiers with illegal characters throw an exception")
    void invalidIdentifierThrows(final String identifier) {
        assertThatThrownBy(() -> StateUtils.validateIdentifier(identifier))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("legalIdentifiers")
    @DisplayName("Identifiers with legal characters are valid")
    void validIdentifierWorks(final String identifier) {
        assertThat(StateUtils.validateIdentifier(identifier)).isEqualTo(identifier);
    }

    @Test
    @DisplayName("`computeLabel` with a null service name throws")
    void computeLabel_nullServiceNameThrows() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> StateUtils.computeLabel(null, FRUIT_STATE_KEY))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("`computeLabel` with a null state key throws")
    void computeLabel_nullStateKeyThrows() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> StateUtils.computeLabel(FIRST_SERVICE, null)).isInstanceOf(NullPointerException.class);
    }

    /**
     * NOTE: This test may look silly, because it literally does what is in the source code. But
     * this is actually very important! This computation MUST NOT CHANGE. If any change is made in
     * the sources, it will cause this test to fail. That will cause the engineer to look at this
     * test and SEE THIS NOTE. And then realize, they CANNOT MAKE THIS CHANGE.
     */
    @Test
    @DisplayName("`computeLabel` is always serviceName.stateKey")
    void computeLabel() {
        assertThat(StateUtils.computeLabel(FIRST_SERVICE, FRUIT_STATE_KEY))
                .isEqualTo(FIRST_SERVICE + "." + FRUIT_STATE_KEY);
    }

    /**
     * NOTE: This test may look silly, because it literally does what is in the source code. But
     * this is actually very important! This computation MUST NOT CHANGE. If any change is made in
     * the sources, it will cause this test to fail. That will cause the engineer to look at this
     * test and SEE THIS NOTE. And then realize, they CANNOT MAKE THIS CHANGE.
     */
    @Test
    @DisplayName("`computeClassId` is always {serviceName}:{stateKey}:v{version}:{extra}")
    void computeClassId() {
        final var classId = StateUtils.hashString("A:B:v1.0.0:C");
        assertThat(StateUtils.computeClassId("A", "B", version(1, 0, 0), "C")).isEqualTo(classId);
    }

    /**
     * NOTE: This test may look silly, because it literally does what is in the source code. But
     * this is actually very important! This computation MUST NOT CHANGE. If any change is made in
     * the sources, it will cause this test to fail. That will cause the engineer to look at this
     * test and SEE THIS NOTE. And then realize, they CANNOT MAKE THIS CHANGE.
     */
    @Test
    @DisplayName("`computeClassId` with metadata is always {serviceName}:{stateKey}:v{version}:{extra}")
    void computeClassId_withMetadata() {
        setupFruitMerkleMap();
        final var ver = fruitMetadata.schema().getVersion();
        final var classId = StateUtils.hashString(fruitMetadata.serviceName()
                + ":"
                + fruitMetadata.stateDefinition().stateKey()
                + ":v"
                + ver.getMajor()
                + "."
                + ver.getMinor()
                + "."
                + ver.getPatch()
                + ":C");
        assertThat(StateUtils.computeClassId(fruitMetadata, "C")).isEqualTo(classId);
    }

    @Test
    @DisplayName("Verifies the hashing algorithm of computeValueClassId produces reasonably unique" + " values")
    void uniqueHashing() {
        // Given a set of serviceName and stateKey pairs
        final var numWords = 1000;
        final var hashes = new HashSet<Long>();
        final var fakeServiceNames = randomWords(numWords);
        final var fakeStateKeys = randomWords(numWords);

        // When I call computeValueClassId with those and collect the resulting hash
        for (final var serviceName : fakeServiceNames) {
            for (final var stateKey : fakeStateKeys) {
                final var md = new StateMetadata<>(
                        serviceName,
                        new TestSchema(1),
                        StateDefinition.inMemory(stateKey, STRING_SERDES, STRING_SERDES));
                final var hash = StateUtils.computeClassId(md, "extra string");
                hashes.add(hash);
            }
        }

        // Then each hash is highly probabilistically unique (and for our test, definitely unique)
        assertThat(hashes).hasSize(numWords * numWords);
    }

    public static Stream<Arguments> illegalIdentifiers() {
        // The only valid characters are A-Za-z0-9_-. Any other character is a problem.
        // So I will construct three different types of invalid strings. Those that contain only
        // an invalid character, those that start with an invalid character and are followed by
        // valid characters, and those that start with valid characters and are trailed by a single
        // invalid character. I will select invalid characters from the ASCII set, and a subset of
        // values that are above the ASCII range. I will make sure to include multibyte characters.

        // Add every ASCII char that is illegal
        final List<String> illegalChars = new ArrayList<>();
        for (char i = 0; i < 255; i++) {
            if ((i >= 'A' && i <= 'Z') || (i >= 'a' && i <= 'z') || (i >= '0' && i <= '9') || (i == '-' || i == '_')) {
                // This is a valid character, so skip it -- don't add it to illegalChars!
                continue;
            }
            illegalChars.add("" + i);
        }
        // And the illegal 😈 multibyte character
        illegalChars.add("\uD83D\uDE08");

        // Construct the arguments
        final List<Arguments> argumentsList = new LinkedList<>();

        // Add each illegal character on its own as a test case
        for (final var illegalChar : illegalChars) {
            argumentsList.add(Arguments.of(illegalChar));
        }

        // Add each illegal character as the suffix to an otherwise legal string
        for (final var illegalChar : illegalChars) {
            argumentsList.add(Arguments.of("Some Legal Characters " + illegalChar));
        }

        // Add each illegal character as the prefix to an otherwise legal string
        for (final var illegalChar : illegalChars) {
            argumentsList.add(Arguments.of(illegalChar + " Some Legal Characters"));
        }

        // return it
        return argumentsList.stream();
    }

    public static Stream<Arguments> legalIdentifiers() {
        List<Arguments> args = new LinkedList<>();

        // An arbitrary collection of strings that contain 0-9A-Za-z and space.
        final String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final String lower = upper.toLowerCase(Locale.ROOT);
        String digits = "0123456789";
        String validChars = upper + lower + digits + "-" + "_";

        // Test scenarios where there is a single valid char
        for (int i = 0; i < validChars.length(); i++) {
            args.add(Arguments.of("" + validChars.charAt(i)));
        }

        // Test scenarios where we have a set of valid chars
        for (int i = 0; i < 100; i++) {
            args.add(Arguments.of(randomString(validChars, 12)));
        }

        return args.stream();
    }
}
