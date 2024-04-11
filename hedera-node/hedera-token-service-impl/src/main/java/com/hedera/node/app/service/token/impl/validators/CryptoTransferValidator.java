/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hedera.node.app.spi.validation.Validations.validateAccountID;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashSet;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
public class CryptoTransferValidator {

    @Inject
    public CryptoTransferValidator() {
        // For Dagger injection
    }

    /**
     * Performs pure checks that validates basic fields in the crypto transfer transaction.
     * @param op the crypto transfer transaction body
     * @throws PreCheckException if any of the checks fail
     */
    public void pureChecks(@NonNull final CryptoTransferTransactionBody op) throws PreCheckException {
        final var acctAmounts = op.transfersOrElse(TransferList.DEFAULT).accountAmountsOrElse(emptyList());
        final var uniqueAcctIds = new HashSet<Pair<AccountID, Boolean>>();
        long netBalance = 0;
        // Validate hbar transfers
        for (final AccountAmount acctAmount : acctAmounts) {
            validateTruePreCheck(acctAmount.hasAccountID(), INVALID_ACCOUNT_ID);
            final var acctId = validateAccountID(acctAmount.accountIDOrThrow(), null);
            uniqueAcctIds.add(Pair.of(acctId, acctAmount.isApproval()));
            netBalance += acctAmount.amount();
        }
        validateTruePreCheck(netBalance == 0, INVALID_ACCOUNT_AMOUNTS);
        validateFalsePreCheck(uniqueAcctIds.size() < acctAmounts.size(), ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS);

        // Validate token transfers
        final var tokenTransfers = op.tokenTransfersOrElse(emptyList());
        final var nftIds = new HashSet<Long>();
        final var tokenIds = new HashSet<TokenID>();
        for (final TokenTransferList tokenTransfer : tokenTransfers) {
            final var tokenID = tokenTransfer.token();
            tokenIds.add(tokenID);
            validateTruePreCheck(tokenID != null && !tokenID.equals(TokenID.DEFAULT), INVALID_TOKEN_ID);

            // Validate the fungible transfers
            final var uniqueTokenAcctIds = new HashSet<Pair<AccountID, Boolean>>();
            final var fungibleTransfers = tokenTransfer.transfersOrElse(emptyList());
            long netTokenBalance = 0;
            boolean nonZeroFungibleValueFound = false;
            for (final AccountAmount acctAmount : fungibleTransfers) {
                validateTruePreCheck(acctAmount.hasAccountID(), INVALID_TRANSFER_ACCOUNT_ID);
                uniqueTokenAcctIds.add(Pair.of(acctAmount.accountIDOrThrow(), acctAmount.isApproval()));
                netTokenBalance += acctAmount.amount();
                if (!nonZeroFungibleValueFound && acctAmount.amount() != 0) {
                    nonZeroFungibleValueFound = true;
                }
            }
            validateFalsePreCheck(
                    uniqueTokenAcctIds.size() < fungibleTransfers.size(), ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS);
            validateTruePreCheck(netTokenBalance == 0, TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);

            // Validate the nft transfers
            final var nftTransfers = tokenTransfer.nftTransfersOrElse(emptyList());
            nftIds.clear();
            for (final NftTransfer nftTransfer : nftTransfers) {
                validateTruePreCheck(nftTransfer.serialNumber() > 0, INVALID_TOKEN_NFT_SERIAL_NUMBER);
                validateTruePreCheck(nftTransfer.hasSenderAccountID(), INVALID_TRANSFER_ACCOUNT_ID);
                validateTruePreCheck(nftTransfer.hasReceiverAccountID(), INVALID_TRANSFER_ACCOUNT_ID);
                validateFalsePreCheck(
                        !nftIds.isEmpty() && nftIds.contains(nftTransfer.serialNumber()), INVALID_ACCOUNT_AMOUNTS);
                validateFalsePreCheck(
                        nftTransfer.senderAccountID().equals(nftTransfer.receiverAccountID()),
                        ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS);
                nftIds.add(nftTransfer.serialNumber());
            }
            // Verify that one and only one of the two types of transfers (fungible or non-fungible) is present
            validateFalsePreCheck(
                    uniqueTokenAcctIds.isEmpty() && nftIds.isEmpty(), EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS);
        }
        validateFalsePreCheck(tokenIds.size() < tokenTransfers.size(), TOKEN_ID_REPEATED_IN_TOKEN_LIST);
    }

    /**
     * All validations needed for the crypto transfer operation, that include state or config.
     * @param op the crypto transfer operation
     * @param ledgerConfig the ledger config
     * @param hederaConfig the hedera config
     * @param tokensConfig the tokens config
     */
    public void validateSemantics(
            @NonNull final CryptoTransferTransactionBody op,
            @NonNull final LedgerConfig ledgerConfig,
            @NonNull final HederaConfig hederaConfig,
            @NonNull final TokensConfig tokensConfig) {
        final var transfers = op.transfersOrElse(TransferList.DEFAULT);

        // Validate that there aren't too many hbar transfers
        final var hbarTransfers = transfers.accountAmountsOrElse(emptyList());
        validateTrue(hbarTransfers.size() <= ledgerConfig.transfersMaxLen(), TRANSFER_LIST_SIZE_LIMIT_EXCEEDED);

        // Validate that allowances are enabled, or that no hbar transfers are an allowance transfer
        final var allowancesEnabled = hederaConfig.allowancesIsEnabled();
        validateTrue(allowancesEnabled || !isTransferWithApproval(hbarTransfers), NOT_SUPPORTED);

        // The loop below will validate the counts for token transfers (both fungible and non-fungible)
        final var tokenTransfers = op.tokenTransfersOrElse(emptyList());
        var totalFungibleTransfers = 0;
        var totalNftTransfers = 0;
        final var nftsEnabled = tokensConfig.nftsAreEnabled();
        for (final TokenTransferList tokenTransfer : tokenTransfers) {
            // Validate the fungible token transfer(s) (if present)
            final var fungibleTransfers = tokenTransfer.transfersOrElse(emptyList());
            validateTrue(allowancesEnabled || !isTransferWithApproval(fungibleTransfers), NOT_SUPPORTED);
            totalFungibleTransfers += fungibleTransfers.size();

            // Validate the nft transfer(s) (if present)
            final var nftTransfers = tokenTransfer.nftTransfersOrElse(emptyList());
            validateTrue(nftsEnabled || nftTransfers.isEmpty(), NOT_SUPPORTED);
            validateTrue(allowancesEnabled || !isNftTransferWithApproval(nftTransfers), NOT_SUPPORTED);
            totalNftTransfers += nftTransfers.size();

            // Verify that the current total number of (counted) fungible transfers does not exceed the limit
            validateTrue(
                    totalFungibleTransfers <= ledgerConfig.tokenTransfersMaxLen(),
                    TOKEN_TRANSFER_LIST_SIZE_LIMIT_EXCEEDED);
            // Verify that the current total number of (counted) nft transfers does not exceed the limit
            validateTrue(totalNftTransfers <= ledgerConfig.nftTransfersMaxLen(), BATCH_SIZE_LIMIT_EXCEEDED);
        }
    }

    /**
     * Checks if any of the transfers is with approval flag set.
     * @param transfers the transfers
     * @return true if any of the transfers is with approval flag set, false otherwise
     */
    private boolean isTransferWithApproval(@NonNull final List<AccountAmount> transfers) {
        for (final AccountAmount transfer : transfers) {
            if (transfer.isApproval()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if any of the nft transfers is with approval flag set.
     * @param nftTransfers the nft transfers
     * @return true if any of the nft transfers is with approval flag set, false otherwise
     */
    private boolean isNftTransferWithApproval(@NonNull final List<NftTransfer> nftTransfers) {
        for (final NftTransfer nftTransfer : nftTransfers) {
            if (nftTransfer.isApproval()) {
                return true;
            }
        }

        return false;
    }
}
