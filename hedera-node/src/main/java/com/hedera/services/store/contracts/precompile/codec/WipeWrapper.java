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
package com.hedera.services.store.contracts.precompile.codec;

import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.Collections;
import java.util.List;

public record WipeWrapper(TokenID token, AccountID account, long amount, List<Long> serialNumbers) {

    private static final long NON_FUNGIBLE_WIPE_AMOUNT = -1;
    private static final List<Long> FUNGIBLE_WIPE_SERIAL_NUMBERS = Collections.emptyList();

    public static WipeWrapper forNonFungible(
            final TokenID token, final AccountID account, final List<Long> serialNumbers) {
        return new WipeWrapper(token, account, NON_FUNGIBLE_WIPE_AMOUNT, serialNumbers);
    }

    public static WipeWrapper forFungible(
            final TokenID token, final AccountID account, final long amount) {
        return new WipeWrapper(token, account, amount, FUNGIBLE_WIPE_SERIAL_NUMBERS);
    }

    public TokenType type() {
        return (serialNumbers != null && !serialNumbers.isEmpty() && amount <= 0)
                ? NON_FUNGIBLE_UNIQUE
                : FUNGIBLE_COMMON;
    }
}
