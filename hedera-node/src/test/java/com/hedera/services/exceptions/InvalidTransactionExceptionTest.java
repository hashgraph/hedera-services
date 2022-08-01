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
package com.hedera.services.exceptions;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class InvalidTransactionExceptionTest {
    @Test
    void canBuildRevertingExceptionWithDetail() {
        final var reason = "I don't like it!";
        final var frameReason = Bytes.of(reason.getBytes());
        final var revertingEx =
                new InvalidTransactionException(reason, INVALID_ALLOWANCE_OWNER_ID, true);

        assertTrue(revertingEx.isReverting());
        assertEquals(frameReason, revertingEx.getRevertReason());
    }

    @Test
    void canBuildRevertingExceptionNoDetail() {
        final var frameReason = Bytes.of(INVALID_ALLOWANCE_OWNER_ID.name().getBytes());
        final var revertingEx = new InvalidTransactionException(INVALID_ALLOWANCE_OWNER_ID, true);

        assertTrue(revertingEx.isReverting());
        assertEquals(frameReason, revertingEx.getRevertReason());
    }

    @Test
    void mostExceptionsArentReverting() {
        final var otherEx = new InvalidTransactionException(INVALID_TRANSACTION_BODY);

        assertFalse(otherEx.isReverting());
        assertThrows(IllegalStateException.class, otherEx::getRevertReason);
    }
}
