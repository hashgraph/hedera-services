package com.hedera.services.txns.validation;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.services.txns.validation.PureValidation.checkKey;
import com.hedera.services.sigs.utils.ImmutableKeyUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;

public class TokenListChecks {
    static Predicate<Key> ADMIN_KEY_REMOVAL = ImmutableKeyUtils::signalsKeyRemoval;

    public static boolean repeatsItself(List<TokenID> tokens) {
        return new HashSet<>(tokens).size() < tokens.size();
    }

    public static ResponseCodeEnum checkTokenTransfers(List<TokenTransferList> tokenTransferLists) {
    	if (tokenTransferLists.isEmpty()) {
    	    return OK;
        }

        Set<TokenID> uniqueTokens = new HashSet<>();
        for (TokenTransferList tokenTransferList : tokenTransferLists) {
            if (!tokenTransferList.hasToken()) {
                return INVALID_TOKEN_ID;
            }

            var net = 0;
            uniqueTokens.add(tokenTransferList.getToken());

            var uniqueAccounts = new HashSet<AccountID>();
            for (AccountAmount adjustment : tokenTransferList.getTransfersList()) {
                if (!adjustment.hasAccountID()) {
                    return INVALID_ACCOUNT_ID;
                }

                if (adjustment.getAmount() == 0) {
                    return INVALID_ACCOUNT_AMOUNTS;
                }

                uniqueAccounts.add(adjustment.getAccountID());
                net += adjustment.getAmount();
            }

            if (uniqueAccounts.size() < tokenTransferList.getTransfersCount()) {
                return ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
            }

            if (net != 0) {
                return TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
            }
        }

        if (uniqueTokens.size() < tokenTransferLists.size()) {
            return TOKEN_ID_REPEATED_IN_TOKEN_LIST;
        }

        return OK;
    }

    public static ResponseCodeEnum initialSupplyAndDecimalsCheck(long initialSupply, int decimals) {
        if (initialSupply < 0) {
            return INVALID_TOKEN_INITIAL_SUPPLY;
        }
        return decimals < 0 ? INVALID_TOKEN_DECIMALS : OK;
    }

    public static ResponseCodeEnum checkKeys(
            boolean hasAdminKey, Key adminKey,
            boolean hasKycKey, Key kycKey,
            boolean hasWipeKey, Key wipeKey,
            boolean hasSupplyKey, Key supplyKey,
            boolean hasFreezeKey, Key freezeKey
    ) {
        ResponseCodeEnum validity = OK;

        if (hasAdminKey && !ADMIN_KEY_REMOVAL.test(adminKey)) {
            if ((validity = checkKey(adminKey, INVALID_ADMIN_KEY)) != OK) {
                return validity;
            }
        }
        if (hasKycKey) {
            if ((validity = checkKey(kycKey, INVALID_KYC_KEY)) != OK) {
                return validity;
            }
        }
        if (hasWipeKey) {
            if ((validity = checkKey(wipeKey, INVALID_WIPE_KEY)) != OK) {
                return validity;
            }
        }
        if (hasSupplyKey) {
            if ((validity = checkKey(supplyKey, INVALID_SUPPLY_KEY)) != OK) {
                return validity;
            }
        }
        if (hasFreezeKey) {
            if ((validity = checkKey(freezeKey, INVALID_FREEZE_KEY)) != OK) {
                return validity;
            }
        }

        return validity;
    }
}