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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.airdrops;

import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_REFERENCE_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.token.TokenAirdropTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TokenAirdropDecoder {

    // Tuple indexes
    private static final int TOKEN = 0;
    private static final int TOKEN_TRANSFERS = 1;
    private static final int NFT_AMOUNT = 2;
    private static final int TOKEN_ACCOUNT_ID = 0;
    private static final int TOKEN_AMOUNT = 1;
    private static final int TOKEN_IS_APPROVAL = 2;
    private static final int NFT_SENDER = 0;
    private static final int NFT_RECEIVER = 1;
    private static final int NFT_SERIAL = 2;
    private static final int NFT_IS_APPROVAL = 3;

    @Inject
    public TokenAirdropDecoder() {
        // Dagger2
    }

    public TransactionBody decodeAirdrop(@NonNull final HtsCallAttempt attempt) {
        final var call = TokenAirdropTranslator.TOKEN_AIRDROP.decodeCall(attempt.inputBytes());
        final var transferList = (Tuple[]) call.get(0);
        final var maxAirdropsAllowed =
                attempt.configuration().getConfigData(TokensConfig.class).maxAllowedAirdropTransfersPerTx();
        validateFalse(transferList.length > maxAirdropsAllowed, TOKEN_REFERENCE_LIST_SIZE_LIMIT_EXCEEDED);
        final var ledgerConfig = attempt.configuration().getConfigData(LedgerConfig.class);
        final var tokenAirdrop = bodyForAirdrop(transferList, attempt.addressIdConverter(), ledgerConfig);
        return TransactionBody.newBuilder().tokenAirdrop(tokenAirdrop).build();
    }

    private TokenAirdropTransactionBody bodyForAirdrop(
            @NonNull final Tuple[] transferList,
            @NonNull final AddressIdConverter addressIdConverter,
            @NonNull final LedgerConfig ledgerConfig) {
        final var transferBuilderList = new ArrayList<TokenTransferList>();
        Arrays.stream(transferList).forEach(transfer -> {
            final var tokenTransferList = TokenTransferList.newBuilder();
            final var token = ConversionUtils.asTokenId(transfer.get(TOKEN));
            tokenTransferList.token(token);
            final var tokenAmountsTuple = (Tuple[]) transfer.get(TOKEN_TRANSFERS);
            final var nftAmountsTuple = (Tuple[]) transfer.get(NFT_AMOUNT);
            validateFalse(
                    tokenAmountsTuple.length > ledgerConfig.tokenTransfersMaxLen(),
                    TOKEN_REFERENCE_LIST_SIZE_LIMIT_EXCEEDED);
            validateFalse(
                    nftAmountsTuple.length > ledgerConfig.nftTransfersMaxLen(),
                    TOKEN_REFERENCE_LIST_SIZE_LIMIT_EXCEEDED);
            if (tokenAmountsTuple.length > 0) {
                final var aaList = new ArrayList<AccountAmount>();
                Arrays.stream(tokenAmountsTuple).forEach(tokenAmount -> {
                    final var amount = (long) tokenAmount.get(TOKEN_AMOUNT);
                    final var account = addressIdConverter.convert(tokenAmount.get(TOKEN_ACCOUNT_ID));
                    final var isApproval = (boolean) tokenAmount.get(TOKEN_IS_APPROVAL);
                    aaList.add(AccountAmount.newBuilder()
                            .amount(amount)
                            .accountID(account)
                            .isApproval(isApproval)
                            .build());
                });
                tokenTransferList.transfers(aaList);
            }
            if (nftAmountsTuple.length > 0) {
                final var nftAmount = nftAmountsTuple[0];
                final var serial = (long) nftAmount.get(NFT_SERIAL);
                final var sender = addressIdConverter.convert(nftAmount.get(NFT_SENDER));
                final var receiver = addressIdConverter.convert(nftAmount.get(NFT_RECEIVER));
                final var isApproval = (boolean) nftAmount.get(NFT_IS_APPROVAL);
                tokenTransferList.nftTransfers(NftTransfer.newBuilder()
                        .senderAccountID(sender)
                        .receiverAccountID(receiver)
                        .serialNumber(serial)
                        .isApproval(isApproval)
                        .build());
            }
            transferBuilderList.add(tokenTransferList.build());
        });
        return TokenAirdropTransactionBody.newBuilder()
                .tokenTransfers(transferBuilderList)
                .build();
    }
}
