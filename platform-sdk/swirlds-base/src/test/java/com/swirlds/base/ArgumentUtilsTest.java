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

package com.swirlds.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArgumentUtilsTest {

    @Test
    @DisplayName("Check null argument")
    void checkNullArgument() {
        // given
        final Object argument = null;
        final String argumentName = "argument";

        // then
        assertThrows(NullPointerException.class, () -> ArgumentUtils.throwArgNull(argument, argumentName));
    }

    @Test
    @DisplayName("Check non-null argument with null as argument name")
    void checkNullArgumentName() {
        // given
        final Object argument = "value";
        final String argumentName = null;

        // when
        final Object value = ArgumentUtils.throwArgNull(argument, argumentName);

        // then
        assertEquals(argument, value);
    }

    @Test
    @DisplayName("Check null argument with null as argument name")
    void checkNullArgumentAndNullArgumentName() {
        // given
        final Object argument = null;
        final String argumentName = null;

        // when
        final NullPointerException npe =
                assertThrows(NullPointerException.class, () -> ArgumentUtils.throwArgNull(argument, argumentName));

        // then
        assertEquals("The supplied argument 'null' cannot be null!", npe.getMessage());
    }
}
