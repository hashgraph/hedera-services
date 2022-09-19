/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.exceptions.ValidationUtils.validateFalse;
import static com.hedera.services.exceptions.ValidationUtils.validateFalseOrRevert;
import static com.hedera.services.exceptions.ValidationUtils.validateResourceLimit;
import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.exceptions.ValidationUtils.validateTrueOrRevert;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

class ValidationUtilsTest {

    @Test
    void factoriesWorkAsExpected() {
        final var falseExCapturedByCode =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> validateTrue(false, MEMO_TOO_LONG));
        final var falseExCapturedByCodeAndMsg =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> validateTrue(false, INVALID_TOKEN_BURN_AMOUNT, "Should be true!"));
        final var trueExCapturedByCode =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> validateFalse(true, CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT));
        final var trueExCapturedByCodeAndMsg =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> validateFalse(true, TOKEN_HAS_NO_SUPPLY_KEY, "Should be false!"));
        final var resourceLimitCapturedByCode =
                assertThrows(
                        ResourceLimitException.class,
                        () ->
                                validateResourceLimit(
                                        false, MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED));

        assertEquals(MEMO_TOO_LONG, falseExCapturedByCode.getResponseCode());
        assertEquals(INVALID_TOKEN_BURN_AMOUNT, falseExCapturedByCodeAndMsg.getResponseCode());
        assertEquals("Should be true!", falseExCapturedByCodeAndMsg.getMessage());
        assertEquals(CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT, trueExCapturedByCode.getResponseCode());
        assertEquals(TOKEN_HAS_NO_SUPPLY_KEY, trueExCapturedByCodeAndMsg.getResponseCode());
        assertEquals("Should be false!", trueExCapturedByCodeAndMsg.getMessage());
        assertEquals(
                MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED,
                resourceLimitCapturedByCode.getResponseCode());
    }

    @Test
    void validatesWithRevertingReason() {
        final var capturedEx =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> validateTrueOrRevert(false, INVALID_ALLOWANCE_OWNER_ID));
        final var trueExCapturedByCode =
                assertThrows(
                        InvalidTransactionException.class,
                        () -> validateFalseOrRevert(true, CANNOT_WIPE_TOKEN_TREASURY_ACCOUNT));
        assertTrue(capturedEx.isReverting());
        assertTrue(trueExCapturedByCode.isReverting());
        final var reason = Bytes.of(INVALID_ALLOWANCE_OWNER_ID.name().getBytes());
        assertEquals(reason, capturedEx.getRevertReason());
    }
}
