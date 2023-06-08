/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.NftAllowance;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.data.HederaConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashSet;
import java.util.List;
import javax.inject.Inject;

public class AllowanceValidator {
    final ConfigProvider configProvider;

    @Inject
    public AllowanceValidator(final ConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    /**
     * Check if the allowance feature is enabled
     *
     * @return true if the feature is enabled in {@link HederaConfig}
     */
    public boolean isEnabled() {
        final var hederaConfig = configProvider.getConfiguration().getConfigData(HederaConfig.class);
        return hederaConfig.allowancesIsEnabled();
    }

    protected void validateTotalAllowancesPerTxn(final int totalAllowances) {
        final var hederaConfig = configProvider.getConfiguration().getConfigData(HederaConfig.class);
        validateFalse(
                exceedsTxnLimit(totalAllowances, hederaConfig.allowancesMaxTransactionLimit()),
                MAX_ALLOWANCES_EXCEEDED);
    }

    protected void validateSerialNums(
            final List<Long> serialNums, final TokenID tokenId, final ReadableNftStore nftStore) {
        final var serialsSet = new HashSet<>(serialNums);
        for (final var serial : serialsSet) {
            validateTrue(serial > 0, INVALID_TOKEN_NFT_SERIAL_NUMBER);
            final var nft = nftStore.get(tokenId, serial);
            validateTrue(nft != null, INVALID_TOKEN_NFT_SERIAL_NUMBER);
        }
    }

    private boolean exceedsTxnLimit(final int totalAllowances, final int maxLimit) {
        return totalAllowances > maxLimit;
    }

    /* ------------------------ Helper methods needed for allowances validation ------------------------ */
    /**
     * Aggregate total serial numbers in CryptoApproveAllowance transaction.
     * Each serial number in an {@code NftAllowance} is considered as an allowance.
     *
     * @param nftAllowances a list of NFT individual allowances
     * @return the number of mentioned serial numbers
     */
    public static int aggregateApproveNftAllowances(final List<NftAllowance> nftAllowances) {
        int nftAllowancesTotal = 0;
        final var setOfSerials = new HashSet<Long>();

        for (final var allowances : nftAllowances) {
            setOfSerials.addAll(allowances.serialNumbers());
            if (!setOfSerials.isEmpty()) {
                nftAllowancesTotal += setOfSerials.size();
                setOfSerials.clear();
            } else {
                nftAllowancesTotal++;
            }
        }
        return nftAllowancesTotal;
    }

    /**
     * Checks if the total allowances of an account will exceed the limit after applying this
     * transaction. This limit doesn't include number of serials for nfts, since they are not stored
     * on account. The limit includes number of crypto allowances, number of fungible token
     * allowances and number of approvedForAll Nft allowances on owner account
     *
     * @param owner The Account to validate the allowances limit on.
     * @param allowanceMaxAccountLimit maximum number of allowances an Account can have.
     */
    public static void validateAllowanceLimit(final Account owner, final int allowanceMaxAccountLimit) {
        final var totalAllowances = owner.cryptoAllowancesOrElse(emptyList()).size()
                + owner.tokenAllowancesOrElse(emptyList()).size()
                + owner.approveForAllNftAllowancesOrElse(emptyList()).size();
        validateFalse(totalAllowances > allowanceMaxAccountLimit, MAX_ALLOWANCES_EXCEEDED);
    }

    /**
     * Checks the owner of token is treasury or the owner id given in allowance. If not, considers
     * as an invalid owner and returns false.
     *
     * @param nft given nft
     * @param ownerNum owner given in allowance
     * @param token token for which nft belongs to
     * @return whether the owner is valid
     */
    public static boolean isValidOwner(final Nft nft, final long ownerNum, final Token token) {
        final var listedOwner = nft.ownerNumber();
        return listedOwner == 0 ? ownerNum == token.treasuryAccountNumber() : listedOwner == ownerNum;
    }

    /**
     * Returns owner account to be considered for the allowance changes. If the owner is missing in
     * allowance, considers payer of the transaction as the owner. This is same for
     * CryptoApproveAllowance and CryptoDeleteAllowance transaction. Looks at entitiesChanged map
     * before fetching from accountStore for performance.
     *
     * @param owner given owner
     * @param payer given payer for the transaction
     * @param accountStore account store
     * @return owner account
     */
    public static Account getEffectiveOwner(
            @Nullable final AccountID owner,
            @NonNull final Account payer,
            @NonNull final ReadableAccountStore accountStore) {
        final var ownerNum = owner != null ? owner.accountNumOrElse(0L) : 0L;
        if (ownerNum == 0 || ownerNum == payer.accountNumber()) {
            return payer;
        } else {
            // If owner is in modifications get the modified account from state
            final var ownerAccount = accountStore.getAccountById(owner);
            validateTrue(ownerAccount != null, INVALID_ALLOWANCE_OWNER_ID);
            return ownerAccount;
        }
    }
}
