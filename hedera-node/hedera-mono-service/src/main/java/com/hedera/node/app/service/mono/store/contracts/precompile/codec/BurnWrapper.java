/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.store.contracts.precompile.codec;

import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenType;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collections;
import java.util.List;

public record BurnWrapper(long amount, TokenID tokenType, List<Long> serialNos, boolean fungibleToken) {
    private static long NONFUNGIBLE_BURN_AMOUNT = 0L;
    private static final List<Long> FUNGIBLE_BURN_SERIAL_NOS = Collections.emptyList();

    @NonNull
    public static BurnWrapper forNonFungible(final TokenID tokenType, final List<Long> serialNos) {
        return new BurnWrapper(NONFUNGIBLE_BURN_AMOUNT, tokenType, serialNos, false);
    }

    @NonNull
    public static BurnWrapper forFungible(final TokenID tokenType, final long amount) {
        return new BurnWrapper(amount, tokenType, FUNGIBLE_BURN_SERIAL_NOS, true);
    }

    public TokenType type() {
        return fungibleToken ? FUNGIBLE_COMMON : NON_FUNGIBLE_UNIQUE;
    }
}
