/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.validation;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class TokenListChecksTest {
    @Test
    void permitsAdminKeyRemoval() {
        final Predicate<Key> adminKeyRemoval = mock(Predicate.class);
        TokenListChecks.adminKeyRemoval = adminKeyRemoval;
        given(adminKeyRemoval.test(any())).willReturn(true);

        final var validity =
                TokenListChecks.checkKeys(
                        true, Key.getDefaultInstance(),
                        false, Key.getDefaultInstance(),
                        false, Key.getDefaultInstance(),
                        false, Key.getDefaultInstance(),
                        false, Key.getDefaultInstance(),
                        false, Key.getDefaultInstance(),
                        false, Key.getDefaultInstance());

        assertEquals(OK, validity);

        TokenListChecks.adminKeyRemoval = ImmutableKeyUtils::signalsKeyRemoval;
    }

    @Test
    void typeChecks() {
        var validity = TokenListChecks.typeCheck(TokenType.FUNGIBLE_COMMON, 10, 5);
        assertEquals(OK, validity);

        validity = TokenListChecks.typeCheck(TokenType.NON_FUNGIBLE_UNIQUE, 0, 0);
        assertEquals(OK, validity);

        validity = TokenListChecks.typeCheck(TokenType.FUNGIBLE_COMMON, 10, -1);
        assertEquals(INVALID_TOKEN_DECIMALS, validity);
        validity = TokenListChecks.typeCheck(TokenType.FUNGIBLE_COMMON, -1, 100);
        assertEquals(INVALID_TOKEN_INITIAL_SUPPLY, validity);

        validity = TokenListChecks.typeCheck(TokenType.NON_FUNGIBLE_UNIQUE, 1, 0);
        assertEquals(INVALID_TOKEN_INITIAL_SUPPLY, validity);
        validity = TokenListChecks.typeCheck(TokenType.NON_FUNGIBLE_UNIQUE, 0, 1);
        assertEquals(INVALID_TOKEN_DECIMALS, validity);

        validity = TokenListChecks.typeCheck(TokenType.UNRECOGNIZED, 0, 0);
        assertEquals(NOT_SUPPORTED, validity);
    }

    @Test
    void suppliesChecks() {
        var validity = TokenListChecks.suppliesCheck(10, 100);
        assertEquals(OK, validity);

        validity = TokenListChecks.suppliesCheck(101, 100);
        assertEquals(INVALID_TOKEN_INITIAL_SUPPLY, validity);
    }

    @Test
    void supplyTypeChecks() {
        var validity = TokenListChecks.supplyTypeCheck(TokenSupplyType.FINITE, 10);
        assertEquals(OK, validity);
        validity = TokenListChecks.supplyTypeCheck(TokenSupplyType.INFINITE, 0);
        assertEquals(OK, validity);

        validity = TokenListChecks.supplyTypeCheck(TokenSupplyType.FINITE, 0);
        assertEquals(INVALID_TOKEN_MAX_SUPPLY, validity);
        validity = TokenListChecks.supplyTypeCheck(TokenSupplyType.INFINITE, 10);
        assertEquals(INVALID_TOKEN_MAX_SUPPLY, validity);

        validity = TokenListChecks.supplyTypeCheck(TokenSupplyType.UNRECOGNIZED, 10);
        assertEquals(NOT_SUPPORTED, validity);
    }

    @Test
    void checksInvalidFeeScheduleKey() {
        final var invalidKeyList1 = KeyList.newBuilder().build();
        final var invalidFeeScheduleKey = Key.newBuilder().setKeyList(invalidKeyList1).build();

        final var validity =
                TokenListChecks.checkKeys(
                        false, Key.getDefaultInstance(),
                        false, Key.getDefaultInstance(),
                        false, Key.getDefaultInstance(),
                        false, Key.getDefaultInstance(),
                        false, Key.getDefaultInstance(),
                        true, invalidFeeScheduleKey,
                        false, Key.getDefaultInstance());

        assertEquals(INVALID_CUSTOM_FEE_SCHEDULE_KEY, validity);
    }

    @Test
    void checksInvalidPauseKey() {
        final var invalidKeyList1 = KeyList.newBuilder().build();
        final var invalidPauseKey = Key.newBuilder().setKeyList(invalidKeyList1).build();

        final var validity =
                TokenListChecks.checkKeys(
                        false,
                        Key.getDefaultInstance(),
                        false,
                        Key.getDefaultInstance(),
                        false,
                        Key.getDefaultInstance(),
                        false,
                        Key.getDefaultInstance(),
                        false,
                        Key.getDefaultInstance(),
                        false,
                        Key.getDefaultInstance(),
                        true,
                        invalidPauseKey);

        assertEquals(INVALID_PAUSE_KEY, validity);
    }
}
