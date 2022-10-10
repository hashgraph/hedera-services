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
package com.hedera.services.txns.crypto.validators;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ALLOWANCES_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.ReadOnlyTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.HashSet;
import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Validations for {@link com.hederahashgraph.api.proto.java.CryptoApproveAllowance} transaction
 * allowances
 */
public class AllowanceChecks {
    private final GlobalDynamicProperties dynamicProperties;

    protected AllowanceChecks(final GlobalDynamicProperties dynamicProperties) {
        this.dynamicProperties = dynamicProperties;
    }

    /**
     * Check if the allowance feature is enabled
     *
     * @return true if the feature is enabled in {@link
     *     com.hedera.services.context.properties.GlobalDynamicProperties}
     */
    public boolean isEnabled() {
        return dynamicProperties.areAllowancesEnabled();
    }

    ResponseCodeEnum validateTotalAllowances(final int totalAllowances) {
        if (exceedsTxnLimit(totalAllowances, dynamicProperties.maxAllowanceLimitPerTransaction())) {
            return MAX_ALLOWANCES_EXCEEDED;
        }
        if (emptyAllowances(totalAllowances)) {
            return EMPTY_ALLOWANCES;
        }
        return OK;
    }

    /**
     * Validates serial numbers for {@link NftAllowance}
     *
     * @param serialNums given serial numbers in the {@link
     *     com.hederahashgraph.api.proto.java.CryptoApproveAllowance} operation
     * @param token token for which allowance is related to
     * @return response code after validation
     */
    ResponseCodeEnum validateSerialNums(
            final List<Long> serialNums, final Token token, final ReadOnlyTokenStore tokenStore) {
        final var serialsSet = new HashSet<>(serialNums);
        for (var serial : serialsSet) {
            if (serial <= 0) {
                return INVALID_TOKEN_NFT_SERIAL_NUMBER;
            }
            try {
                tokenStore.loadUniqueToken(token.getId(), serial);
            } catch (InvalidTransactionException ex) {
                return INVALID_TOKEN_NFT_SERIAL_NUMBER;
            }
        }

        return OK;
    }

    boolean exceedsTxnLimit(final int totalAllowances, final int maxLimit) {
        return totalAllowances > maxLimit;
    }

    boolean emptyAllowances(final int totalAllowances) {
        return totalAllowances == 0;
    }

    Pair<Account, ResponseCodeEnum> fetchOwnerAccount(
            final Id owner, final Account payerAccount, final AccountStore accountStore) {
        if (owner.equals(Id.MISSING_ID) || owner.equals(payerAccount.getId())) {
            return Pair.of(payerAccount, OK);
        } else {
            try {
                return Pair.of(accountStore.loadAccount(owner), OK);
            } catch (InvalidTransactionException ex) {
                return Pair.of(payerAccount, INVALID_ALLOWANCE_OWNER_ID);
            }
        }
    }
}
