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
import static com.hedera.node.app.service.token.impl.test.handlers.transfers.Utils.aaAlias;
import static com.hedera.node.app.service.token.impl.test.handlers.transfers.Utils.aaWith;
import static com.hedera.node.app.service.token.impl.test.handlers.transfers.Utils.nftTransferWith;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.EnsureAliasesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnsureAliasesStepTest extends StepsBase {
    @BeforeEach
    public void setUp() {
        super.setUp();
        recordBuilder = new SingleTransactionRecordBuilder(consensusInstant);
        givenTxn();
        refreshWritableStores();
        givenStoresAndConfig(handleContext);
        ensureAliasesStep = new EnsureAliasesStep(body);
        transferContext = new TransferContextImpl(handleContext);
    }

    @Test
    void autoCreatesAccounts() {
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

        assertThat(writableAccountStore.sizeOfAliasesState()).isEqualTo(2);
        assertThat(writableAccountStore.modifiedAccountsInState()).hasSize(0);
        assertThat(writableAccountStore.get(asAccount(createdNumber))).isNull();
        assertThat(writableAccountStore.get(asAccount(createdNumber + 1))).isNull();
        assertThat(writableAliases.get(ecKeyAlias)).isNull();
        assertThat(writableAliases.get(edKeyAlias)).isNull();

        ensureAliasesStep.doIn(transferContext);

        assertThat(writableAccountStore.modifiedAliasesInState()).hasSize(2);
        assertThat(writableAccountStore.modifiedAccountsInState()).hasSize(2);
        assertThat(writableAccountStore.sizeOfAliasesState()).isEqualTo(4);
        assertThat(writableAccountStore.get(asAccount(createdNumber))).isNotNull();
        assertThat(writableAccountStore.get(asAccount(createdNumber + 1))).isNotNull();
        assertThat(writableAliases.get(ecKeyAlias).accountNum()).isEqualTo(createdNumber);
        assertThat(writableAliases.get(edKeyAlias).accountNum()).isEqualTo(createdNumber + 1);

        assertThat(transferContext.numOfAutoCreations()).isEqualTo(2);
        assertThat(transferContext.numOfLazyCreations()).isEqualTo(0);
        assertThat(transferContext.resolutions()).containsKey(edKeyAlias);
        assertThat(transferContext.resolutions()).containsKey(ecKeyAlias);
    }

    @Test
    void resolvedExistingAliases() {
        // insert aliases into state
        setUpInsertingKnownAliasesToState();

        assertThat(writableAccountStore.sizeOfAliasesState()).isEqualTo(2);
        assertThat(writableAccountStore.get(unknownAliasedId)).isNotNull();
        assertThat(writableAccountStore.get(unknownAliasedId1)).isNotNull();

        ensureAliasesStep.doIn(transferContext);

        assertThat(writableAccountStore.modifiedAliasesInState()).isEmpty();
        assertThat(writableAccountStore.sizeOfAliasesState()).isEqualTo(2);
        assertThat(writableAliases.get(ecKeyAlias).accountNum()).isEqualTo(createdNumber);
        assertThat(writableAliases.get(edKeyAlias).accountNum()).isEqualTo(createdNumber + 1);

        assertThat(transferContext.numOfAutoCreations()).isEqualTo(0);
        assertThat(transferContext.numOfLazyCreations()).isEqualTo(0);
        assertThat(transferContext.resolutions()).containsKey(edKeyAlias);
        assertThat(transferContext.resolutions()).containsKey(ecKeyAlias);
    }

    @Test
    void failsOnRepeatedAliasesInTokenTransferList() {
        body = CryptoTransferTransactionBody.newBuilder()
                .transfers(TransferList.newBuilder()
                        .accountAmounts(aaWith(ownerId, -1_000), aaWith(unknownAliasedId, +1_000))
                        .build())
                .tokenTransfers(
                        TokenTransferList.newBuilder()
                                .token(fungibleTokenId)
                                .transfers(List.of(
                                        aaWith(ownerId, -1_000),
                                        aaWith(unknownAliasedId1, +1_000),
                                        aaWith(ownerId, -1_000),
                                        aaWith(unknownAliasedId1, +1_000)))
                                .build(),
                        TokenTransferList.newBuilder()
                                .token(nonFungibleTokenId)
                                .nftTransfers(nftTransferWith(ownerId, unknownAliasedId1, 1))
                                .build())
                .build();
        txn = asTxn(body);
        given(handleContext.body()).willReturn(txn);
        ensureAliasesStep = new EnsureAliasesStep(body);
        transferContext = new TransferContextImpl(handleContext);

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

        assertThatThrownBy(() -> ensureAliasesStep.doIn(transferContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_ALIAS_KEY));
    }

    @Test
    void failsOnRepeatedAliasesInHbarTransferList() {
        body = CryptoTransferTransactionBody.newBuilder()
                .transfers(TransferList.newBuilder()
                        .accountAmounts(
                                aaWith(ownerId, -1_000),
                                aaWith(unknownAliasedId, +1_000),
                                aaWith(ownerId, -1_000),
                                aaWith(unknownAliasedId, +1_000))
                        .build())
                .tokenTransfers()
                .build();
        txn = asTxn(body);
        given(handleContext.body()).willReturn(txn);
        ensureAliasesStep = new EnsureAliasesStep(body);
        transferContext = new TransferContextImpl(handleContext);

        givenConditions();
        assertThatThrownBy(() -> ensureAliasesStep.doIn(transferContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS));
    }

    @Test
    void resolvesMirrorAddressInHbarList() {
        final var mirrorAdjust = aaAlias(mirrorAlias, +100);
        body = CryptoTransferTransactionBody.newBuilder()
                .transfers(
                        TransferList.newBuilder().accountAmounts(mirrorAdjust).build())
                .build();
        txn = asTxn(body);
        given(handleContext.body()).willReturn(txn);
        ensureAliasesStep = new EnsureAliasesStep(body);
        transferContext = new TransferContextImpl(handleContext);

        ensureAliasesStep.doIn(transferContext);

        assertThat(transferContext.resolutions().get(mirrorAlias)).isEqualTo(payerId);
        assertThat(transferContext.numOfLazyCreations()).isZero();
    }

    @Test
    void resolvesMirrorAddressInNftTransfer() {
        body = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(TokenTransferList.newBuilder()
                        .token(nonFungibleTokenId)
                        .nftTransfers(NftTransfer.newBuilder()
                                .receiverAccountID(AccountID.newBuilder()
                                        .alias(mirrorAlias)
                                        .build())
                                .senderAccountID(payerId)
                                .serialNumber(1)
                                .build())
                        .build())
                .build();
        txn = asTxn(body);
        given(handleContext.body()).willReturn(txn);
        ensureAliasesStep = new EnsureAliasesStep(body);
        transferContext = new TransferContextImpl(handleContext);

        ensureAliasesStep.doIn(transferContext);

        assertThat(transferContext.resolutions().get(mirrorAlias)).isEqualTo(payerId);
        assertThat(transferContext.numOfLazyCreations()).isZero();
    }

    private void setUpInsertingKnownAliasesToState() {
        final var readableBuilder = emptyReadableAliasStateBuilder();
        readableBuilder.value(ecKeyAlias, asAccount(createdNumber));
        readableBuilder.value(edKeyAlias, asAccount(createdNumber + 1));
        readableAliases = readableBuilder.build();

        final var writableBuilder = emptyWritableAliasStateBuilder();
        writableBuilder.value(ecKeyAlias, asAccount(createdNumber));
        writableBuilder.value(edKeyAlias, asAccount(createdNumber + 1));
        writableAliases = writableBuilder.build();

        given(writableStates.<Bytes, AccountID>get(ALIASES)).willReturn(writableAliases);
        writableAccountStore = new WritableAccountStore(writableStates);

        writableAccountStore.put(account.copyBuilder()
                .accountNumber(createdNumber)
                .alias(ecKeyAlias)
                .build());
        writableAccountStore.put(account.copyBuilder()
                .accountNumber(createdNumber + 1)
                .alias(edKeyAlias)
                .build());

        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        transferContext = new TransferContextImpl(handleContext);
    }
}
