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

import static com.hedera.services.txns.validation.PureValidation.checkKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CUSTOM_FEE_SCHEDULE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAUSE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MAX_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;

import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenSupplyType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

public final class TokenListChecks {
    static Predicate<Key> adminKeyRemoval = ImmutableKeyUtils::signalsKeyRemoval;

    private TokenListChecks() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static boolean repeatsItself(final List<TokenID> tokens) {
        return new HashSet<>(tokens).size() < tokens.size();
    }

    public static ResponseCodeEnum typeCheck(
            final TokenType type, final long initialSupply, final int decimals) {
        switch (type) {
            case FUNGIBLE_COMMON:
                return fungibleCommonTypeCheck(initialSupply, decimals);
            case NON_FUNGIBLE_UNIQUE:
                return nonFungibleUniqueCheck(initialSupply, decimals);
            default:
                return NOT_SUPPORTED;
        }
    }

    public static ResponseCodeEnum nonFungibleUniqueCheck(
            final long initialSupply, final int decimals) {
        if (initialSupply != 0) {
            return INVALID_TOKEN_INITIAL_SUPPLY;
        }

        return decimals != 0 ? INVALID_TOKEN_DECIMALS : OK;
    }

    public static ResponseCodeEnum fungibleCommonTypeCheck(
            final long initialSupply, final int decimals) {
        if (initialSupply < 0) {
            return INVALID_TOKEN_INITIAL_SUPPLY;
        }

        return decimals < 0 ? INVALID_TOKEN_DECIMALS : OK;
    }

    public static ResponseCodeEnum suppliesCheck(final long initialSupply, final long maxSupply) {
        if (maxSupply > 0 && initialSupply > maxSupply) {
            return INVALID_TOKEN_INITIAL_SUPPLY;
        }

        return OK;
    }

    public static ResponseCodeEnum supplyTypeCheck(
            final TokenSupplyType supplyType, final long maxSupply) {
        switch (supplyType) {
            case INFINITE:
                return maxSupply != 0 ? INVALID_TOKEN_MAX_SUPPLY : OK;
            case FINITE:
                return maxSupply <= 0 ? INVALID_TOKEN_MAX_SUPPLY : OK;
            default:
                return NOT_SUPPORTED;
        }
    }

    public static ResponseCodeEnum checkKeys(
            final boolean hasAdminKey,
            final Key adminKey,
            final boolean hasKycKey,
            final Key kycKey,
            final boolean hasWipeKey,
            final Key wipeKey,
            final boolean hasSupplyKey,
            final Key supplyKey,
            final boolean hasFreezeKey,
            final Key freezeKey,
            final boolean hasFeeScheduleKey,
            final Key feeScheduleKey,
            final boolean hasPauseKey,
            final Key pauseKey) {
        ResponseCodeEnum validity = checkAdminKey(hasAdminKey, adminKey);
        if (validity != OK) {
            return validity;
        }

        validity = checkKeyOfType(hasKycKey, kycKey, INVALID_KYC_KEY);
        if (validity != OK) {
            return validity;
        }

        validity = checkKeyOfType(hasWipeKey, wipeKey, INVALID_WIPE_KEY);
        if (validity != OK) {
            return validity;
        }

        validity = checkKeyOfType(hasSupplyKey, supplyKey, INVALID_SUPPLY_KEY);
        if (validity != OK) {
            return validity;
        }

        validity = checkKeyOfType(hasFreezeKey, freezeKey, INVALID_FREEZE_KEY);
        if (validity != OK) {
            return validity;
        }

        validity =
                checkKeyOfType(hasFeeScheduleKey, feeScheduleKey, INVALID_CUSTOM_FEE_SCHEDULE_KEY);
        if (validity != OK) {
            return validity;
        }

        validity = checkKeyOfType(hasPauseKey, pauseKey, INVALID_PAUSE_KEY);
        return validity;
    }

    private static ResponseCodeEnum checkAdminKey(final boolean hasAdminKey, final Key adminKey) {
        if (hasAdminKey && !adminKeyRemoval.test(adminKey)) {
            return checkKey(adminKey, INVALID_ADMIN_KEY);
        }
        return OK;
    }

    private static ResponseCodeEnum checkKeyOfType(
            final boolean hasKey, final Key key, final ResponseCodeEnum code) {
        if (hasKey) {
            return checkKey(key, code);
        }
        return OK;
    }

    public static ResponseCodeEnum nftSupplyKeyCheck(
            final TokenType tokenType, final boolean supplyKey) {
        if (tokenType == TokenType.NON_FUNGIBLE_UNIQUE && !supplyKey) {
            return TOKEN_HAS_NO_SUPPLY_KEY;
        }
        return OK;
    }
}
