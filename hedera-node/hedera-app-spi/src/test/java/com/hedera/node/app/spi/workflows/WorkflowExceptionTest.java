/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import org.junit.jupiter.api.Test;

class WorkflowExceptionTest {
    @Test
    void testConstructor() {
        final var exception = new WorkflowException(ResponseCodeEnum.UNAUTHORIZED);

        assertThat(exception.getStatus()).isEqualTo(ResponseCodeEnum.UNAUTHORIZED);
        assertThat(exception.getMessage()).isEqualTo(ResponseCodeEnum.UNAUTHORIZED.protoName());
        assertTrue(exception.shouldRollbackStack());
    }

    @Test
    void testConstructorMultipleParams() {
        final var exception =
                new WorkflowException(ResponseCodeEnum.UNAUTHORIZED, WorkflowException.ShouldRollbackStack.NO);

        assertThat(exception.getStatus()).isEqualTo(ResponseCodeEnum.UNAUTHORIZED);
        assertThat(exception.getMessage()).isEqualTo(ResponseCodeEnum.UNAUTHORIZED.protoName());
        assertFalse(exception.shouldRollbackStack());
    }

    @SuppressWarnings({"ThrowableNotThrown"})
    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new WorkflowException(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void trueIsntProblematic() {
        assertDoesNotThrow(() -> WorkflowException.validateTrue(true, MEMO_TOO_LONG));
    }

    @Test
    void falseIsProblem() {
        final var failure =
                assertThrows(WorkflowException.class, () -> WorkflowException.validateTrue(false, MEMO_TOO_LONG));

        assertEquals(MEMO_TOO_LONG, failure.getStatus());
    }

    @Test
    void trueIsProblemFromOtherPerspective() {
        final var failure =
                assertThrows(WorkflowException.class, () -> WorkflowException.validateFalse(true, MEMO_TOO_LONG));

        assertEquals(MEMO_TOO_LONG, failure.getStatus());
    }

    @Test
    void falseIsOkFromOtherPerspective() {
        assertDoesNotThrow(() -> WorkflowException.validateFalse(false, MEMO_TOO_LONG));
    }
}
