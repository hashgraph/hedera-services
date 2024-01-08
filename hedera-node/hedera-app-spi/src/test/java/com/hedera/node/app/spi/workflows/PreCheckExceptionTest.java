/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.workflows;

import static com.hedera.hapi.node.base.ResponseCodeEnum.MEMO_TOO_LONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import org.junit.jupiter.api.Test;

class PreCheckExceptionTest {

    @Test
    void testConstructor() {
        // when
        final PreCheckException exception = new PreCheckException(ResponseCodeEnum.UNAUTHORIZED);

        // then
        assertThat(exception.responseCode()).isEqualTo(ResponseCodeEnum.UNAUTHORIZED);
        assertThat(exception.getMessage()).isEqualTo(ResponseCodeEnum.UNAUTHORIZED.protoName());
    }

    @SuppressWarnings({"ThrowableNotThrown", "ConstantConditions"})
    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new PreCheckException(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void trueIsntProblematic() {
        assertDoesNotThrow(() -> PreCheckException.validateTruePreCheck(true, MEMO_TOO_LONG));
    }

    @Test
    void falseIsProblem() {
        final var failure = assertThrows(
                PreCheckException.class, () -> PreCheckException.validateTruePreCheck(false, MEMO_TOO_LONG));

        assertEquals(MEMO_TOO_LONG, failure.responseCode());
    }

    @Test
    void trueIsProblemFromOtherPerspective() {
        final var failure = assertThrows(
                PreCheckException.class, () -> PreCheckException.validateFalsePreCheck(true, MEMO_TOO_LONG));

        assertEquals(MEMO_TOO_LONG, failure.responseCode());
    }

    @Test
    void falseIsOkFromOtherPerspective() {
        assertDoesNotThrow(() -> PreCheckException.validateFalsePreCheck(false, MEMO_TOO_LONG));
    }
}
