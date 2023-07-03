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

package com.hedera.node.app.service.token.impl.test.handlers.transfers;

import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.service.token.impl.handlers.transfer.AssociateTokenRecepientsStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.EnsureAliasesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.NFTOwnersChangeStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.ReplaceAliasesWithIDsInOp;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChangeNFTOwnersStepTest extends StepsBase {

    @BeforeEach
    public void setUp() {
        super.setUp();
        recordBuilder = new SingleTransactionRecordBuilder(consensusInstant);
        givenTxn();
        refreshWritableStores();
        // since we can't change NFT owner with auto association if KYC key exists on token
        writableTokenStore.put(nonFungibleToken.copyBuilder().kycKey((Key) null).build());
        givenStoresAndConfig(handleContext);
        ensureAliasesStep = new EnsureAliasesStep(body);
        replaceAliasesWithIDsInOp = new ReplaceAliasesWithIDsInOp();
        associateTokenRecepientsStep = new AssociateTokenRecepientsStep(body);
        transferContext = new TransferContextImpl(handleContext);
    }

    @Test
    void changesNftOwners() {
        final var receiver = asAccount(tokenReceiver);
        final var replacedOp = getReplacedOp();
        changeNFTOwnersStep = new NFTOwnersChangeStep(replacedOp, payerId);

        final var nft = writableNftStore.get(uniqueTokenIdSl1);
        assertThat(nft.ownerId()).isEqualTo(ownerId);

        final var senderAccount = writableAccountStore.get(ownerId);
        final var receiverAccount = writableAccountStore.get(receiver);

        final var numNftsOwnedBySender = senderAccount.numberOwnedNfts();
        assertThat(numNftsOwnedBySender).isEqualTo(2);
        final var numNftsOwnedByReceiver = receiverAccount.numberOwnedNfts();
        assertThat(numNftsOwnedByReceiver).isEqualTo(2);
        final var numPositiveBalancesSender = senderAccount.numberPositiveBalances();
        assertThat(numPositiveBalancesSender).isEqualTo(2);
        final var numPositiveBalancesReceiver = receiverAccount.numberPositiveBalances();
        assertThat(numPositiveBalancesReceiver).isEqualTo(2);

        final var senderTokenRelBalance =
                writableTokenRelStore.get(ownerId, nonFungibleTokenId).balance();
        assertThat(senderTokenRelBalance).isEqualTo(1);
        final var receiverTokenRelBalance =
                writableTokenRelStore.get(receiver, nonFungibleTokenId).balance();
        assertThat(receiverTokenRelBalance).isEqualTo(0);

        changeNFTOwnersStep.doIn(transferContext);

        // owner Id on NFT should change to receiver
        final var nftChanged = writableNftStore.get(uniqueTokenIdSl1);
        assertThat(nftChanged.ownerId()).isEqualTo(receiver);

        // see numPositiveBalances and numOwnedNfts change
        final var senderAccountAfter = writableAccountStore.get(ownerId);
        final var receiverAccountAfter = writableAccountStore.get(receiver);

        final var numNftsOwnedBySenderAfter = senderAccountAfter.numberOwnedNfts();
        assertThat(numNftsOwnedBySenderAfter).isEqualTo(numNftsOwnedBySender - 1);
        final var numNftsOwnedByReceiverAfter = receiverAccountAfter.numberOwnedNfts();
        assertThat(numNftsOwnedByReceiverAfter).isEqualTo(numNftsOwnedByReceiver + 1);
        final var numPositiveBalancesSenderAfter = senderAccountAfter.numberPositiveBalances();
        assertThat(numPositiveBalancesSenderAfter).isEqualTo(numPositiveBalancesSender - 1);
        final var numPositiveBalancesReceiverAfter = receiverAccountAfter.numberPositiveBalances();
        assertThat(numPositiveBalancesReceiverAfter).isEqualTo(numPositiveBalancesReceiver + 1);

        // see token relation balances for sender and receiver change
        final var senderTokenRelBalanceAfter = writableTokenRelStore.get(ownerId, nonFungibleTokenId);
        final var receiverTokenRelBalanceAfter = writableTokenRelStore.get(receiver, nonFungibleTokenId);
        assertThat(senderTokenRelBalanceAfter.balance()).isEqualTo(senderTokenRelBalance - 1);
        assertThat(receiverTokenRelBalanceAfter.balance()).isEqualTo(receiverTokenRelBalance + 1);
    }

    CryptoTransferTransactionBody getReplacedOp() {
        givenConditions();
        ensureAliasesStep.doIn(transferContext);
        associateTokenRecepientsStep.doIn(transferContext);
        return replaceAliasesWithIDsInOp.replaceAliasesWithIds(body, transferContext);
    }
}
