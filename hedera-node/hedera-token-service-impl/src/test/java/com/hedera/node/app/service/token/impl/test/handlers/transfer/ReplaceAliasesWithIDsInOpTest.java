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

package com.hedera.node.app.service.token.impl.test.handlers.transfer;

import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.test.handlers.transfer.AccountAmountUtils.aaWith;
import static com.hedera.node.app.service.token.impl.test.handlers.transfer.AccountAmountUtils.nftTransferWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.EnsureAliasesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.ReplaceAliasesWithIDsInOp;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.records.CryptoCreateRecordBuilder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReplaceAliasesWithIDsInOpTest extends StepsBase {
    @BeforeEach
    public void setUp() {
        super.setUp();
        recordBuilder = new SingleTransactionRecordBuilder(consensusInstant);
        givenTxn();
        refreshWritableStores();
        givenStoresAndConfig(handleContext);
        ensureAliasesStep = new EnsureAliasesStep(body);
        replaceAliasesWithIDsInOp = new ReplaceAliasesWithIDsInOp();
        transferContext = new TransferContextImpl(handleContext);
    }

    @Test
    void replacesAliasesInOp() {
        given(handleContext.dispatchRemovableChildTransaction(any(), eq(CryptoCreateRecordBuilder.class)))
                .will((invocation) -> {
                    final var copy =
                            account.copyBuilder().accountNumber(createdNumber).build();
                    writableAccountStore.put(copy);
                    writableAliases.put(ecKeyAlias, asAccount(createdNumber));
                    return recordBuilder.accountID(asAccount(createdNumber));
                })
                .will((invocation) -> {
                    final var copy = account.copyBuilder()
                            .accountNumber(createdNumber + 1)
                            .build();
                    writableAccountStore.put(copy);
                    writableAliases.put(edKeyAlias, asAccount(createdNumber + 1));
                    return recordBuilder.accountID(asAccount(createdNumber + 1));
                });
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        ensureAliasesStep.doIn(transferContext);

        assertThat(writableAccountStore.modifiedAliasesInState()).hasSize(2);
        assertThat(writableAccountStore.modifiedAccountsInState()).hasSize(2);
        assertThat(writableAccountStore.sizeOfAliasesState()).isEqualTo(4);
        assertThat(writableAccountStore.get(asAccount(createdNumber))).isNotNull();
        assertThat(writableAccountStore.get(asAccount(createdNumber + 1))).isNotNull();
        assertThat(writableAliases.get(ecKeyAlias).accountNum()).isEqualTo(createdNumber);
        assertThat(writableAliases.get(edKeyAlias).accountNum()).isEqualTo(createdNumber + 1);

        assertThat(transferContext.numOfAutoCreations()).isEqualTo(2);
        assertThat(transferContext.numOfLazyCreations()).isZero();
        assertThat(transferContext.resolutions()).containsKey(edKeyAlias);
        assertThat(transferContext.resolutions()).containsKey(ecKeyAlias);

        final var replacedOp = replaceAliasesWithIDsInOp.replaceAliasesWithIds(body, transferContext);

        final var expectedOp = CryptoTransferTransactionBody.newBuilder()
                .transfers(TransferList.newBuilder()
                        .accountAmounts(aaWith(ownerId, -1_000), aaWith(asAccount(createdNumber), +1_000))
                        .build())
                .tokenTransfers(
                        TokenTransferList.newBuilder()
                                .token(fungibleTokenId)
                                .transfers(
                                        List.of(aaWith(ownerId, -1_000), aaWith(asAccount(createdNumber + 1), +1_000)))
                                .build(),
                        TokenTransferList.newBuilder()
                                .token(nonFungibleTokenId)
                                .nftTransfers(nftTransferWith(ownerId, asAccount(createdNumber + 1), 1))
                                .build())
                .build();
        assertThat(replacedOp).isEqualTo(expectedOp);
    }
}
