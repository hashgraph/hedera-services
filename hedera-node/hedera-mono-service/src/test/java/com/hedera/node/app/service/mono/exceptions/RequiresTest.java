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

package com.hedera.node.app.service.mono.exceptions;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class RequiresTest {
    @Test
    void NonNullTest() {
        {
            // trivial case
            Requires.nonNull();
        }

        {
            // Success cases
            Requires.nonNull("foo", "foo");
            Requires.nonNull("bar", "bar", "bar", "bar");
            Requires.nonNull("bear", "bear", "bear", "bear", "bear", "bear");
        }

        {
            // Null args detected cases
            assertThatExceptionOfType(Requires.NullArgumentsException.class)
                    .isThrownBy(() -> {
                        Requires.nonNull(null, "argument-1");
                    })
                    .withMessage("null argument found: argument-1");
            assertThatExceptionOfType(Requires.NullArgumentsException.class)
                    .isThrownBy(() -> {
                        Requires.nonNull(this, "argument-1", null, "argument-2");
                    })
                    .withMessage("null argument found: argument-2");
            assertThatExceptionOfType(Requires.NullArgumentsException.class)
                    .isThrownBy(() -> {
                        Requires.nonNull(null, "argument-1", null, "argument-2");
                    })
                    .withMessage("null arguments found: argument-1, argument-2");
            assertThatExceptionOfType(Requires.NullArgumentsException.class)
                    .isThrownBy(() -> {
                        Requires.nonNull(null, "argument-1", this, "argument-2", null, "argument-3");
                    })
                    .withMessage("null arguments found: argument-1, argument-3");
        }

        {
            // Bad #args
            assertThatIllegalArgumentException().isThrownBy(() -> {
                Requires.nonNull("foo");
            });
            assertThatIllegalArgumentException().isThrownBy(() -> {
                Requires.nonNull("foo", "foo", "foo", "foo", "foo");
            });
        }

        {
            // Null arg but also null name
            assertThatIllegalArgumentException().isThrownBy(() -> {
                Requires.nonNull(null, null);
            });
        }
    }
}
