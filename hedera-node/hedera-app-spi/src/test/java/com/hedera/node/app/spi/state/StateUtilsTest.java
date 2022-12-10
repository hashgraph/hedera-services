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
package com.hedera.node.app.spi.state;

class StateUtilsTest {
    //
    //    public static Stream<Arguments> illegalStateNames() {
    //        // The only valid characters are A-Za-z0-9_-. Any other character is a problem.
    //        // So I will construct three different types of invalid strings. Those that contain
    // only
    //        // an invalid character, those that start with an invalid character and are followed
    // by
    //        // valid characters, and those that start with valid characters and are trailed by a
    // single
    //        // invalid character. I will select invalid characters from the ASCII set, and a
    // subset of
    //        // values that are above the ASCII range. I will make sure to include multibyte
    // characters.
    //
    //        // Add every ASCII char that is illegal
    //        final List<String> illegalChars = new ArrayList<>();
    //        for (char i = 0; i < 255; i++) {
    //            if ((i >= 'A' && i <= 'Z')
    //                    || (i >= 'a' && i <= 'z')
    //                    || (i >= '0' && i <= '9')
    //                    || (i == '-' || i == '_')) {
    //                // This is a valid character, so skip it -- don't add it to illegalChars!
    //                continue;
    //            }
    //            illegalChars.add("" + i);
    //        }
    //        // And the illegal 😈 multibyte character
    //        illegalChars.add("\uD83D\uDE08");
    //
    //        // Construct the arguments
    //        final List<Arguments> argumentsList = new LinkedList<>();
    //
    //        // Add each illegal character on its own as a test case
    //        for (final var illegalChar : illegalChars) {
    //            argumentsList.add(Arguments.of(illegalChar));
    //        }
    //
    //        // Add each illegal character as the suffix to an otherwise legal string
    //        for (final var illegalChar : illegalChars) {
    //            argumentsList.add(Arguments.of("Some Legal Characters " + illegalChar));
    //        }
    //
    //        // Add each illegal character as the prefix to an otherwise legal string
    //        for (final var illegalChar : illegalChars) {
    //            argumentsList.add(Arguments.of(illegalChar + " Some Legal Characters"));
    //        }
    //
    //        // return it
    //        return argumentsList.stream();
    //    }
    //
    //    public static Stream<Arguments> legalStateNames() {
    //        List<Arguments> args = new LinkedList<>();
    //
    //        // An arbitrary collection of strings that contain 0-9A-Za-z and space.
    //        final String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    //        final String lower = upper.toLowerCase(Locale.ROOT);
    //        String digits = "0123456789";
    //        String validChars = upper + lower + digits + "-" + "_";
    //
    //        // Test scenarios where there is a single valid char
    //        for (int i = 0; i < validChars.length(); i++) {
    //            args.add(Arguments.of("" + validChars.charAt(i)));
    //        }
    //
    //        // Test scenarios where we have a set of valid chars
    //        for (int i = 0; i < 100; i++) {
    //            args.add(Arguments.of(TestUtils.randomString(validChars, 12)));
    //        }
    //
    //        return args.stream();
    //    }
    //
    //    @Test
    //    @DisplayName(
    //            "Verifies the hashing algorithm of computeValueClassId produces reasonably unique
    // values")
    //    void uniqueHashing() {
    //        // Given a set of serviceName and stateKey pairs
    //        final var numWords = 1000;
    //        final var hashes = new HashSet<Long>();
    //        final var fakeServiceNames = TestUtils.randomWords(numWords);
    //        final var fakeStateKeys = TestUtils.randomWords(numWords);
    //
    //        // When I call computeValueClassId with those and collect the resulting hash
    //        for (final var serviceName : fakeServiceNames) {
    //            for (final var stateKey : fakeStateKeys) {
    //                final var hash = StateUtils.computeValueClassId(serviceName, stateKey);
    //                hashes.add(hash);
    //            }
    //        }
    //
    //        // Then each hash is highly probabilistically unique (and for our test, definitely
    // unique)
    //        assertThat(hashes).hasSize(numWords * numWords);
    //    }
    //
    //    @Test
    //    @DisplayName("Validating a null state key throws an NPE")
    //    void validateNullStateKey() {
    //        //noinspection DataFlowIssue
    //        final var thrown = catchThrowable(() -> StateUtils.validateStateKey(null));
    //        assertThat(thrown).isInstanceOf(NullPointerException.class);
    //    }
    //
    //    @Test
    //    @DisplayName("Validating a state key with no characters throws an exception")
    //    void validateEmptyStateKey() {
    //        final var thrown = catchThrowable(() -> StateUtils.validateStateKey(""));
    //        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    //    }
    //
    //    @ParameterizedTest
    //    @MethodSource("illegalStateNames")
    //    @DisplayName("State keys with illegal characters throw an exception")
    //    void invalidStateKey(final String stateKey) {
    //        final var thrown = catchThrowable(() -> StateUtils.validateStateKey(stateKey));
    //        assertThat(thrown).isInstanceOf(IllegalArgumentException.class);
    //    }
    //
    //    @ParameterizedTest
    //    @MethodSource("legalStateNames")
    //    @DisplayName("State keys with legal characters are valid")
    //    void validStateKey(final String stateKey) {
    //
    //        assertThat(StateUtils.validateStateKey(stateKey)).isEqualTo(stateKey);
    //    }
}
