/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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
import static com.hedera.hapi.node.base.ResponseCodeEnum.EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSFER_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.math.BigInteger.ZERO;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.token.TokenAirdropTransactionBody;
import com.hedera.node.app.spi.workflows.PreCheckException;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;

@Singleton
public class TokenAirdropValidator {

    private static final int MAX_TOKEN_TRANSFERS = 10;

    @Inject
    public TokenAirdropValidator() {
        // For Dagger injection
    }

    /**
     * Performs pure checks that validates basic fields in the token airdrop transaction.
     * @param op the token airdrop transaction body
     * @throws PreCheckException if any of the checks fail
     */
    public void pureChecks(@NonNull final TokenAirdropTransactionBody op) throws PreCheckException {
        // Validate token transfers
        final var tokenTransfers = op.tokenTransfers();
        validateTruePreCheck(tokenTransfers.size() <= MAX_TOKEN_TRANSFERS, INVALID_TRANSACTION);

        final var tokenIds = new HashSet<TokenID>();
        for (final TokenTransferList tokenTransfer : tokenTransfers) {
            final var tokenID = tokenTransfer.token();
            tokenIds.add(tokenID);
            validateTruePreCheck(tokenID != null && !tokenID.equals(TokenID.DEFAULT), INVALID_TOKEN_ID);

            // Validate the fungible transfers
            final var uniqueTokenAcctIds = new HashSet<Pair<AccountID, Boolean>>();
            final var fungibleTransfers = tokenTransfer.transfers();
            validateTruePreCheck(isNetZeroAdjustment(fungibleTransfers), TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);
            boolean nonZeroFungibleValueFound = false;
            for (final AccountAmount acctAmount : fungibleTransfers) {
                validateTruePreCheck(acctAmount.hasAccountID(), INVALID_TRANSFER_ACCOUNT_ID);
                uniqueTokenAcctIds.add(Pair.of(acctAmount.accountIDOrThrow(), acctAmount.isApproval()));
                if (!nonZeroFungibleValueFound && acctAmount.amount() != 0) {
                    nonZeroFungibleValueFound = true;
                }
            }
            validateFalsePreCheck(
                    uniqueTokenAcctIds.size() < fungibleTransfers.size(), ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS);

            // Validate the nft transfers
            final var nftTransfers = tokenTransfer.nftTransfers();
            final var nftIds = new HashSet<Long>();
            for (final NftTransfer nftTransfer : nftTransfers) {
                validateTruePreCheck(nftTransfer.serialNumber() > 0, INVALID_TOKEN_NFT_SERIAL_NUMBER);
                validateTruePreCheck(nftTransfer.hasSenderAccountID(), INVALID_TRANSFER_ACCOUNT_ID);
                validateTruePreCheck(nftTransfer.hasReceiverAccountID(), INVALID_TRANSFER_ACCOUNT_ID);
                validateFalsePreCheck(
                        !nftIds.isEmpty() && nftIds.contains(nftTransfer.serialNumber()), INVALID_ACCOUNT_AMOUNTS);
                validateFalsePreCheck(
                        nftTransfer.senderAccountIDOrThrow().equals(nftTransfer.receiverAccountID()),
                        ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS);
                nftIds.add(nftTransfer.serialNumber());
            }
            // Verify that one and only one of the two types of transfers (fungible or non-fungible) is present
            validateFalsePreCheck(
                    uniqueTokenAcctIds.isEmpty() && nftIds.isEmpty(), EMPTY_TOKEN_TRANSFER_ACCOUNT_AMOUNTS);
        }
        validateFalsePreCheck(tokenIds.size() < tokenTransfers.size(), TOKEN_ID_REPEATED_IN_TOKEN_LIST);
    }

    private static boolean isNetZeroAdjustment(@NonNull final List<AccountAmount> adjusts) {
        var net = ZERO;
        for (var adjust : adjusts) {
            net = net.add(BigInteger.valueOf(adjust.amount()));
        }
        return net.equals(ZERO);
    }
}
