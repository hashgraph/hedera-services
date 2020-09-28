package com.hedera.services.txns.validation;

import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;

import java.util.HashSet;
import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ADMIN_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_KYC_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SUPPLY_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_DECIMALS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_INITIAL_SUPPLY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_WIPE_KEY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;

public class TokenChecks {
    public static boolean hasRepeatedTokenID(List<TokenID> tokens) {
        var unique = new HashSet<TokenID>(tokens);
        return unique.size() < tokens.size();
    }

    public static ResponseCodeEnum checkTokenTransfers(List<TokenTransferList> tokenTransferLists) {
        var uniqueTokens = new HashSet<TokenID>();

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

    public static ResponseCodeEnum checkKey(Key key, ResponseCodeEnum failure) {
        try {
            var fcKey = JKey.mapKey(key);
            if (!fcKey.isValid()) {
                return failure;
            }
            return OK;
        } catch (Exception ignore) {
            return failure;
        }
    }


    public static ResponseCodeEnum checkKeys(boolean hasAdminKey, Key adminKey, boolean hasKycKey, Key kycKey, boolean hasWipeKey, Key wipeKey, boolean hasSupplyKey, Key supplyKey) {
        var validity = OK;

        if (hasAdminKey) {
            validity = checkKey(adminKey, INVALID_ADMIN_KEY);
            if (validity != OK) {
                return validity;
            }
        }

        if (hasKycKey) {
            validity = checkKey(kycKey, INVALID_KYC_KEY);
            if (validity != OK) {
                return validity;
            }
        }

        if (hasWipeKey) {
            validity = checkKey(wipeKey, INVALID_WIPE_KEY);
            if (validity != OK) {
                return validity;
            }
        }

        if (hasSupplyKey) {
            validity = checkKey(supplyKey, INVALID_SUPPLY_KEY);
            if (validity != OK) {
                return validity;
            }
        }
        return validity;
    }
}