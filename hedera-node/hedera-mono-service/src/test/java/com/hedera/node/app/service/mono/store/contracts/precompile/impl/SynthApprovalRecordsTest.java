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

package com.hedera.node.app.service.mono.store.contracts.precompile.impl;

import static com.hedera.node.app.service.mono.store.contracts.precompile.impl.TransferPrecompile.updateSynthOpForAutoApprovalOf;
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
        final var aaDebit = aaWith(debited.asGrpcAccount(), -amount).build();
        final var hbarAdjust = BalanceChange.changingHbar(aaDebit, payer.asGrpcAccount());
        final var synthOp = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(TransferList.newBuilder().addAccountAmounts(aaDebit));
        final var approvedSynthOp = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(TransferList.newBuilder()
                        .addAccountAmounts(
                                aaWith(debited.asGrpcAccount(), -amount).setIsApproval(true)));

        updateSynthOpForAutoApprovalOf(synthOp, hbarAdjust);

        assertEquals(approvedSynthOp.build(), synthOp.build());
    }

    @Test
    void doesntUpdateHbarAutoApprovalsIfMatchIsCredit() {
        final var amount = 10;
        final var debited = IdUtils.asModelId("0.0.3");
        final var payer = IdUtils.asModelId("0.0.5");
        final var aaDebit = aaWith(debited.asGrpcAccount(), -amount).build();
        final var aaCredit = aaWith(debited.asGrpcAccount(), +amount).build();
        final var hbarAdjust = BalanceChange.changingHbar(aaDebit, payer.asGrpcAccount());
        final var synthOp = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(TransferList.newBuilder().addAccountAmounts(aaCredit));
        final var unapprovedSynthOp = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(TransferList.newBuilder().addAccountAmounts(aaCredit));

        updateSynthOpForAutoApprovalOf(synthOp, hbarAdjust);

        assertEquals(unapprovedSynthOp.build(), synthOp.build());
    }

    @Test
    void updatesNoHbarAutoApprovalsIfNoneMatch() {
        final var amount = 10;
        final var debited = IdUtils.asModelId("0.0.3");
        final var actualDebited = IdUtils.asModelId("0.0.6");
        final var payer = IdUtils.asModelId("0.0.5");
        final var hbarAdjust = BalanceChange.changingHbar(
                aaWith(debited.asGrpcAccount(), -amount).build(), payer.asGrpcAccount());
        final var synthOp = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(
                        TransferList.newBuilder().addAccountAmounts(aaWith(actualDebited.asGrpcAccount(), -amount)));
        final var unApprovedSynthOp = CryptoTransferTransactionBody.newBuilder()
                .setTransfers(
                        TransferList.newBuilder().addAccountAmounts(aaWith(actualDebited.asGrpcAccount(), -amount)));

        updateSynthOpForAutoApprovalOf(synthOp, hbarAdjust);

        assertEquals(unApprovedSynthOp.build(), synthOp.build());
    }

    @Test
    void canUpdateFungibleAutoApprovals() {
        final var amount = 10;
        final var debited = IdUtils.asModelId("0.0.3");
        final var credited = IdUtils.asModelId("0.0.6");
        final var payer = IdUtils.asModelId("0.0.5");
        final var tokenId = IdUtils.asModelId("0.0.4");
        final var otherTokenId = IdUtils.asModelId("0.0.7");
        final var tokenDebit =
                BalanceChange.tokenAdjust(debited, tokenId, -amount, payer.asGrpcAccount(), false, false);
        final var synthOp = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(TokenTransferList.newBuilder().setToken(otherTokenId.asGrpcToken()))
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(tokenId.asGrpcToken())
                        .addTransfers(aaWith(credited.asGrpcAccount(), +amount))
                        .addTransfers(aaWith(debited.asGrpcAccount(), -amount)));
        final var approvedSynthOp = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(TokenTransferList.newBuilder().setToken(otherTokenId.asGrpcToken()))
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(tokenId.asGrpcToken())
                        .addTransfers(aaWith(credited.asGrpcAccount(), +amount))
                        .addTransfers(aaWith(debited.asGrpcAccount(), -amount).setIsApproval(true)));

        updateSynthOpForAutoApprovalOf(synthOp, tokenDebit);

        assertEquals(approvedSynthOp.build(), synthOp.build());
    }

    @Test
    void doesntUpdateFungibleAutoApprovalsIfMatchesCredit() {
        final var amount = 10;
        final var debited = IdUtils.asModelId("0.0.3");
        final var credited = IdUtils.asModelId("0.0.6");
        final var payer = IdUtils.asModelId("0.0.5");
        final var tokenId = IdUtils.asModelId("0.0.4");
        final var otherTokenId = IdUtils.asModelId("0.0.7");
        final var tokenDebit =
                BalanceChange.tokenAdjust(debited, tokenId, -amount, payer.asGrpcAccount(), false, false);
        final var synthOp = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(TokenTransferList.newBuilder().setToken(otherTokenId.asGrpcToken()))
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(tokenId.asGrpcToken())
                        .addTransfers(aaWith(credited.asGrpcAccount(), +amount))
                        // Clearly should be impossible, just to cover the warning log
                        .addTransfers(aaWith(debited.asGrpcAccount(), +amount)));
        final var unapprovedSynthOp = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(TokenTransferList.newBuilder().setToken(otherTokenId.asGrpcToken()))
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(tokenId.asGrpcToken())
                        .addTransfers(aaWith(credited.asGrpcAccount(), +amount))
                        // Clearly should be impossible, just to cover the warning log
                        .addTransfers(aaWith(debited.asGrpcAccount(), +amount)));

        updateSynthOpForAutoApprovalOf(synthOp, tokenDebit);

        assertEquals(unapprovedSynthOp.build(), synthOp.build());
    }

    @Test
    void updatesNoFungibleAutoApprovalsIfNoneMatch() {
        final var amount = 10;
        final var debited = IdUtils.asModelId("0.0.3");
        final var changeDebited = IdUtils.asModelId("0.0.8");
        final var credited = IdUtils.asModelId("0.0.6");
        final var payer = IdUtils.asModelId("0.0.5");
        final var tokenId = IdUtils.asModelId("0.0.4");
        final var otherTokenId = IdUtils.asModelId("0.0.7");
        final var tokenDebit =
                BalanceChange.tokenAdjust(changeDebited, tokenId, -amount, payer.asGrpcAccount(), false, false);
        final var synthOp = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(TokenTransferList.newBuilder().setToken(otherTokenId.asGrpcToken()))
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(tokenId.asGrpcToken())
                        .addTransfers(aaWith(credited.asGrpcAccount(), +amount))
                        .addTransfers(aaWith(debited.asGrpcAccount(), -amount)));
        final var unApprovedSynthOp = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(TokenTransferList.newBuilder().setToken(otherTokenId.asGrpcToken()))
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(tokenId.asGrpcToken())
                        .addTransfers(aaWith(credited.asGrpcAccount(), +amount))
                        .addTransfers(aaWith(debited.asGrpcAccount(), -amount)));

        updateSynthOpForAutoApprovalOf(synthOp, tokenDebit);

        assertEquals(unApprovedSynthOp.build(), synthOp.build());
    }

    @Test
    void doesNotUpdateFungibleAutoApprovalsForCustomFees() {
        final var amount = 10;
        final var debited = IdUtils.asModelId("0.0.3");
        final var credited = IdUtils.asModelId("0.0.6");
        final var tokenId = IdUtils.asModelId("0.0.4");
        final var otherTokenId = IdUtils.asModelId("0.0.7");
        final var tokenDebit = BalanceChange.tokenCustomFeeAdjust(debited, tokenId, -amount);
        final var synthOp = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(TokenTransferList.newBuilder().setToken(otherTokenId.asGrpcToken()))
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(tokenId.asGrpcToken())
                        .addTransfers(aaWith(credited.asGrpcAccount(), +amount))
                        .addTransfers(aaWith(debited.asGrpcAccount(), -amount)));
        final var unApprovedSynthOp = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(TokenTransferList.newBuilder().setToken(otherTokenId.asGrpcToken()))
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(tokenId.asGrpcToken())
                        .addTransfers(aaWith(credited.asGrpcAccount(), +amount))
                        .addTransfers(aaWith(debited.asGrpcAccount(), -amount)));

        updateSynthOpForAutoApprovalOf(synthOp, tokenDebit);

        assertEquals(unApprovedSynthOp.build(), synthOp.build());
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

        updateSynthOpForAutoApprovalOf(synthOp, nftExchange);

        assertEquals(approvedSynthOp.build(), synthOp.build());
    }

    @Test
    void updatesNoNonFungibleAutoApprovalsIfNonMatch() {
        final var changedSerialNo = 666L;
        final var unchangedSerialNo = 777L;
        final var sender = IdUtils.asModelId("0.0.3");
        final var otherSender = IdUtils.asModelId("0.0.8");
        final var receiver = IdUtils.asModelId("0.0.6");
        final var payer = IdUtils.asModelId("0.0.5");
        final var tokenId = IdUtils.asModelId("0.0.4");
        final var nftTransfer = ntWith(sender.asGrpcAccount(), receiver.asGrpcAccount(), changedSerialNo)
                .build();
        final var otherNftTransfer = ntWith(sender.asGrpcAccount(), receiver.asGrpcAccount(), unchangedSerialNo)
                .build();
        final var nftExchange = BalanceChange.changingNftOwnership(
                tokenId,
                tokenId.asGrpcToken(),
                ntWith(otherSender.asGrpcAccount(), receiver.asGrpcAccount(), changedSerialNo)
                        .build(),
                payer.asGrpcAccount());
        final var synthOp = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(tokenId.asGrpcToken())
                        .addNftTransfers(otherNftTransfer)
                        .addNftTransfers(nftTransfer));
        final var unApprovedSynthOp = CryptoTransferTransactionBody.newBuilder()
                .addTokenTransfers(TokenTransferList.newBuilder()
                        .setToken(tokenId.asGrpcToken())
                        .addNftTransfers(otherNftTransfer)
                        .addNftTransfers(nftTransfer));

        updateSynthOpForAutoApprovalOf(synthOp, nftExchange);

        assertEquals(unApprovedSynthOp.build(), synthOp.build());
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
