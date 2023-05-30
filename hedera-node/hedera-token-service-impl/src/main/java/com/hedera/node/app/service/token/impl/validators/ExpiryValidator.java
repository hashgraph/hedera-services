/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;

import com.hedera.hapi.node.base.ResponseCodeEnum;

public final class ExpiryValidator {

    private ExpiryValidator() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Calculates the expiry status of an account or contract based on inputs from the account/contract
     *
     * @param balance the current balance of the account or contract
     * @param isExpired whether the account or contract is expired
     * @param isContract true if the entity is a contract, false if the entity is an account
     * @param shouldAutoRenewContracts a config value determining if contracts should auto renew
     * @param shouldAutoRenewAccounts a config value determining if accounts should auto renew
     * @return OK if the entity is not expired, or the configured expiry status if the entity is expired
     */
    public static ResponseCodeEnum getAccountOrContractExpiryStatus(
            final long balance,
            final boolean isExpired,
            final boolean isContract,
            final boolean shouldAutoRenewContracts,
            final boolean shouldAutoRenewAccounts) {
        if (balance > 0 || !isExpired) {
            return OK;
        }
        return getConfiguredExpiryStatusForAccountOrContract(
                isContract, shouldAutoRenewContracts, shouldAutoRenewAccounts);
    }

    private static ResponseCodeEnum getConfiguredExpiryStatusForAccountOrContract(
            final boolean isContract, final boolean shouldAutoRenewContracts, final boolean shouldAutoRenewAccounts) {
        if (isExpiryDisabled(isContract, shouldAutoRenewContracts, shouldAutoRenewAccounts)) {
            return OK;
        }
        return isContract ? CONTRACT_EXPIRED_AND_PENDING_REMOVAL : ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
    }

    private static boolean isExpiryDisabled(
            final boolean isContract, final boolean shouldAutoRenewContracts, final boolean shouldAutoRenewAccounts) {
        return (isContract && !shouldAutoRenewContracts) || (!isContract && !shouldAutoRenewAccounts);
    }
}
