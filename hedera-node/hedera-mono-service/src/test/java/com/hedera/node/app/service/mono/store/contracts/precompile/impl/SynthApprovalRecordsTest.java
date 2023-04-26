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

package com.hedera.node.app.service.mono.store.contracts.precompile.impl;

import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile.updateBodyForAutoApprovalOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.node.app.service.mono.ledger.BalanceChange;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.Test;

class SynthApprovalRecordsTest {
    @Test
    void canUpdateHbarAutoApprovals() {
        final var amount = 10;
        final var debited = IdUtils.asModelId("0.0.3");
        final var payer = IdUtils.asModelId("0.0.5");
        final var hbarAdjust = BalanceChange.changingHbar(
                aaWith(debited.asGrpcAccount(), -amount).build(), payer.asGrpcAccount());
        final var synthOp = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(TransferList.newBuilder().addAccountAmounts(aaWith(debited.asGrpcAccount(), -amount)));
        final var approvedSynthOp = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(TransferList.newBuilder()
                        .addAccountAmounts(
                                aaWith(debited.asGrpcAccount(), -amount).setIsApproval(true)));

        updateBodyForAutoApprovalOf(synthOp, hbarAdjust);

        assertEquals(approvedSynthOp.build(), synthOp.build());
    }

    @Test
    void canUpdateFungibleAutoApprovals() {
        final var amount = 10;
        final var debited = IdUtils.asModelId("0.0.3");
        final var credited = IdUtils.asModelId("0.0.6");
        final var payer = IdUtils.asModelId("0.0.5");
        final var tokenId = IdUtils.asModelId("0.0.4");
        final var tokenCredit =
                BalanceChange.tokenAdjust(credited, tokenId, +amount, payer.asGrpcAccount(), false, false);
        final var tokenDebit =
                BalanceChange.tokenAdjust(debited, tokenId, -amount, payer.asGrpcAccount(), false, false);
        final var synthOp = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(tokenId.asGrpcToken())
                        .addTransfers(aaWith(credited.asGrpcAccount(), +amount))
                        .addTransfers(aaWith(debited.asGrpcAccount(), -amount)));
        final var approvedSynthOp = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(tokenId.asGrpcToken())
                        .addTransfers(aaWith(credited.asGrpcAccount(), +amount))
                        .addTransfers(aaWith(debited.asGrpcAccount(), -amount).setIsApproval(true)));

        updateBodyForAutoApprovalOf(synthOp, tokenDebit);

        assertEquals(approvedSynthOp.build(), synthOp.build());
    }

    @Test
    void canUpdateNonFungibleAutoApprovals() {
        final var changedSerialNo = 666L;
        final var unchangedSerialNo = 777L;
        final var sender = IdUtils.asModelId("0.0.3");
        final var receiver = IdUtils.asModelId("0.0.6");
        final var payer = IdUtils.asModelId("0.0.5");
        final var tokenId = IdUtils.asModelId("0.0.4");
        final var nftTransfer = ntWith(sender.asGrpcAccount(), receiver.asGrpcAccount(), changedSerialNo)
                .build();
        final var otherNftTransfer = ntWith(sender.asGrpcAccount(), receiver.asGrpcAccount(), unchangedSerialNo)
                .build();
        final var nftExchange =
                BalanceChange.changingNftOwnership(tokenId, tokenId.asGrpcToken(), nftTransfer, payer.asGrpcAccount());
        final var synthOp = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(tokenId.asGrpcToken())
                        .addNftTransfers(otherNftTransfer)
                        .addNftTransfers(nftTransfer));
        final var approvedSynthOp = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(tokenId.asGrpcToken())
                        .addNftTransfers(otherNftTransfer)
                        .addNftTransfers(
                                nftTransfer.toBuilder().setIsApproval(true).build()));

        updateBodyForAutoApprovalOf(synthOp, nftExchange);

        assertEquals(approvedSynthOp.build(), synthOp.build());
    }

    private static AccountAmount.Builder aaWith(final AccountID account, final long amount) {
        return AccountAmount.newBuilder().setAccountID(account).setAmount(amount);
    }

    private static NftTransfer.Builder ntWith(final AccountID sender, final AccountID receiver, final long serialNo) {
        return NftTransfer.newBuilder()
                .setSenderAccountID(sender)
                .setReceiverAccountID(receiver)
                .setSerialNumber(serialNo);
    }
}
