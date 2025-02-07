/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.test.handlers.transfer.AccountAmountUtils.aaAlias;
import static com.hedera.node.app.service.token.impl.test.handlers.transfer.AccountAmountUtils.aaWith;
import static com.hedera.node.app.service.token.impl.test.handlers.transfer.AccountAmountUtils.asAccountWithAlias;
import static com.hedera.node.app.service.token.impl.test.handlers.transfer.AccountAmountUtils.nftTransferWith;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.EnsureAliasesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.records.CryptoCreateStreamBuilder;
import com.hedera.node.app.spi.workflows.WorkflowException;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
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
        ensureAliasesInternalSetup(true);
    }

    private void ensureAliasesInternalSetup(final boolean prepopulateReceiverIds) {
        super.baseInternalSetUp(prepopulateReceiverIds);
        givenStoresAndConfig(handleContext);
        givenTxn();
        ensureAliasesStep = new EnsureAliasesStep(body);
        transferContext = new TransferContextImpl(handleContext);
    }

    @Test
    void autoCreatesAccounts() {
        ensureAliasesInternalSetup(false);
        given(handleContext.dispatch(
                        argThat(options -> CryptoCreateStreamBuilder.class.equals(options.streamBuilderType())
                                && payerId.equals(options.payerId()))))
                .will((invocation) -> {
                    final var copy =
                            account.copyBuilder().accountId(hbarReceiverId).build();
                    writableAccountStore.putAndIncrementCount(copy);
                    writableAliases.put(ecKeyAlias, asAccount(hbarReceiver));
                    writableAccountStore.putAndIncrementCountAlias(ecKeyAlias.value(), asAccount(hbarReceiver));
                    given(cryptoCreateRecordBuilder.status()).willReturn(SUCCESS);
                    return cryptoCreateRecordBuilder;
                })
                .will((invocation) -> {
                    final var copy =
                            account.copyBuilder().accountId(tokenReceiverId).build();
                    writableAccountStore.putAndIncrementCount(copy);
                    writableAliases.put(edKeyAlias, asAccount(tokenReceiver));
                    writableAccountStore.putAndIncrementCountAlias(edKeyAlias.value(), asAccount(tokenReceiver));
                    given(cryptoCreateRecordBuilder.status()).willReturn(SUCCESS);
                    return cryptoCreateRecordBuilder;
                });
        given(storeFactory.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        assertThat(writableAccountStore.sizeOfAliasesState()).isEqualTo(2);
        assertThat(writableAccountStore.modifiedAccountsInState()).isEmpty();
        assertThat(writableAccountStore.getAliasedAccountById(asAccount(hbarReceiver)))
                .isNull();
        assertThat(writableAccountStore.getAliasedAccountById(asAccount(tokenReceiver)))
                .isNull();
        assertThat(writableAliases.get(ecKeyAlias)).isNull();
        assertThat(writableAliases.get(edKeyAlias)).isNull();

        ensureAliasesStep.doIn(transferContext);

        assertThat(writableAccountStore.modifiedAliasesInState()).hasSize(2);
        assertThat(writableAccountStore.modifiedAccountsInState()).hasSize(2);
        assertThat(writableAccountStore.sizeOfAliasesState()).isEqualTo(4);
        assertThat(writableAccountStore.getAliasedAccountById(asAccount(hbarReceiver)))
                .isNotNull();
        assertThat(writableAccountStore.getAliasedAccountById(asAccount(tokenReceiver)))
                .isNotNull();
        assertThat(writableAliases.get(ecKeyAlias).accountNum()).isEqualTo(hbarReceiver);
        assertThat(writableAliases.get(edKeyAlias).accountNum()).isEqualTo(tokenReceiver);

        assertThat(transferContext.numOfAutoCreations()).isEqualTo(2);
        assertThat(transferContext.numOfLazyCreations()).isZero();
        assertThat(transferContext.resolutions()).containsKey(edKeyAlias.value());
        assertThat(transferContext.resolutions()).containsKey(ecKeyAlias.value());
    }

    @Test
    void autoCreateEvmAddressesAccounts() {
        final var evmAddressAlias1 = new ProtoBytes(Bytes.wrap(unhex("0000000000000000000000000000000000000004")));
        final var evmAddressAlias2 = new ProtoBytes(Bytes.wrap(unhex("0000000000000000000000000000000000000005")));
        final var evmAddressAlias3 = new ProtoBytes(Bytes.wrap(unhex("0000000000000000000000000000000000000002")));
        body = CryptoTransferTransactionBody.newBuilder()
                .transfers(TransferList.newBuilder()
                        .accountAmounts(aaWith(ownerId, -1_000), aaAlias(evmAddressAlias1.value(), +1_000))
                        .build())
                .tokenTransfers(
                        TokenTransferList.newBuilder()
                                .token(fungibleTokenId)
                                .transfers(List.of(aaWith(ownerId, -1_000), aaAlias(evmAddressAlias2.value(), +1_000)))
                                .build(),
                        TokenTransferList.newBuilder()
                                .token(nonFungibleTokenId)
                                .nftTransfers(nftTransferWith(ownerId, asAccountWithAlias(evmAddressAlias3.value()), 1))
                                .build())
                .build();
        givenTxn(body, payerId);

        given(handleContext.dispatch(
                        argThat(options -> CryptoCreateStreamBuilder.class.equals(options.streamBuilderType())
                                && payerId.equals(options.payerId()))))
                .will((invocation) -> {
                    final var copy = account.copyBuilder()
                            .accountId(hbarReceiverId)
                            .alias(evmAddressAlias1.value())
                            .build();
                    writableAccountStore.putAndIncrementCount(copy);
                    writableAliases.put(evmAddressAlias1, asAccount(hbarReceiver));
                    writableAccountStore.putAndIncrementCountAlias(evmAddressAlias1.value(), asAccount(hbarReceiver));
                    given(cryptoCreateRecordBuilder.status()).willReturn(SUCCESS);
                    return cryptoCreateRecordBuilder;
                })
                .will((invocation) -> {
                    final var copy = account.copyBuilder()
                            .accountId(tokenReceiverId)
                            .alias(evmAddressAlias2.value())
                            .build();
                    writableAccountStore.putAndIncrementCount(copy);
                    writableAliases.put(evmAddressAlias2, asAccount(tokenReceiver));
                    writableAccountStore.putAndIncrementCountAlias(evmAddressAlias2.value(), asAccount(tokenReceiver));
                    given(cryptoCreateRecordBuilder.status()).willReturn(SUCCESS);
                    return cryptoCreateRecordBuilder;
                })
                .will((invocation) -> {
                    final var copy = account.copyBuilder()
                            .accountId(AccountID.newBuilder().accountNum(hbarReceiver + 2))
                            .alias(evmAddressAlias3.value())
                            .build();
                    writableAccountStore.putAndIncrementCount(copy);
                    writableAliases.put(evmAddressAlias3, asAccount(hbarReceiver + 2));
                    writableAccountStore.putAndIncrementCountAlias(
                            evmAddressAlias3.value(), asAccount(hbarReceiver + 2));
                    given(cryptoCreateRecordBuilder.status()).willReturn(SUCCESS);
                    return cryptoCreateRecordBuilder;
                });
        given(storeFactory.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        ensureAliasesStep = new EnsureAliasesStep(body);

        ensureAliasesStep.doIn(transferContext);

        assertThat(writableAccountStore.modifiedAliasesInState()).hasSize(3);
        assertThat(writableAccountStore.modifiedAccountsInState()).hasSize(3);
        assertThat(writableAccountStore.sizeOfAliasesState()).isEqualTo(5);
        assertThat(writableAccountStore.getAliasedAccountById(asAccount(hbarReceiver)))
                .isNotNull();
        assertThat(writableAccountStore.getAliasedAccountById(asAccount(tokenReceiver)))
                .isNotNull();
        assertThat(writableAccountStore.getAliasedAccountById(asAccount(hbarReceiver + 2)))
                .isNotNull();
        assertThat(writableAliases.get(evmAddressAlias1).accountNum()).isEqualTo(hbarReceiver);
        assertThat(writableAliases.get(evmAddressAlias2).accountNum()).isEqualTo(tokenReceiver);
        assertThat(writableAliases.get(evmAddressAlias3).accountNum()).isEqualTo(hbarReceiver + 2);

        assertThat(transferContext.numOfAutoCreations()).isZero();
        assertThat(transferContext.numOfLazyCreations()).isEqualTo(3);
        assertThat(transferContext.resolutions()).containsKey(evmAddressAlias1.value());
        assertThat(transferContext.resolutions()).containsKey(evmAddressAlias2.value());
        assertThat(transferContext.resolutions()).containsKey(evmAddressAlias3.value());
    }

    @Test
    void resolvedExistingAliases() {
        // insert aliases into state
        setUpInsertingKnownAliasesToState();

        assertThat(writableAccountStore.sizeOfAliasesState()).isEqualTo(2);
        assertThat(writableAccountStore.getAliasedAccountById(unknownAliasedId)).isNotNull();
        assertThat(writableAccountStore.getAliasedAccountById(unknownAliasedId1))
                .isNotNull();

        ensureAliasesStep.doIn(transferContext);

        assertThat(writableAccountStore.modifiedAliasesInState()).isEmpty();
        assertThat(writableAccountStore.sizeOfAliasesState()).isEqualTo(2);
        assertThat(writableAliases.get(ecKeyAlias).accountNum()).isEqualTo(hbarReceiver);
        assertThat(writableAliases.get(edKeyAlias).accountNum()).isEqualTo(tokenReceiver);

        assertThat(transferContext.numOfAutoCreations()).isZero();
        assertThat(transferContext.numOfLazyCreations()).isZero();
        assertThat(transferContext.resolutions()).containsKey(edKeyAlias.value());
        assertThat(transferContext.resolutions()).containsKey(ecKeyAlias.value());
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
        txn = asTxn(body, payerId);
        given(handleContext.body()).willReturn(txn);
        ensureAliasesStep = new EnsureAliasesStep(body);
        transferContext = new TransferContextImpl(handleContext);

        given(handleContext.dispatch(
                        argThat(options -> CryptoCreateStreamBuilder.class.equals(options.streamBuilderType())
                                && payerId.equals(options.payerId()))))
                .will((invocation) -> {
                    final var copy =
                            account.copyBuilder().accountId(hbarReceiverId).build();
                    writableAccountStore.put(copy);
                    writableAliases.put(ecKeyAlias, asAccount(hbarReceiver));
                    given(cryptoCreateRecordBuilder.status()).willReturn(SUCCESS);
                    return cryptoCreateRecordBuilder;
                })
                .will((invocation) -> {
                    final var copy =
                            account.copyBuilder().accountId(tokenReceiverId).build();
                    writableAccountStore.put(copy);
                    writableAliases.put(edKeyAlias, asAccount(tokenReceiver));
                    given(cryptoCreateRecordBuilder.status()).willReturn(SUCCESS);
                    return cryptoCreateRecordBuilder;
                });
        given(storeFactory.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);

        assertThatThrownBy(() -> ensureAliasesStep.doIn(transferContext))
                .isInstanceOf(WorkflowException.class)
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
        txn = asTxn(body, payerId);
        given(handleContext.body()).willReturn(txn);
        ensureAliasesStep = new EnsureAliasesStep(body);
        transferContext = new TransferContextImpl(handleContext);

        givenAutoCreationDispatchEffects(payerId);
        assertThatThrownBy(() -> ensureAliasesStep.doIn(transferContext))
                .isInstanceOf(WorkflowException.class)
                .has(responseCode(ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS));
    }

    @Test
    void resolvesMirrorAddressInHbarList() {
        final var mirrorAdjust = aaAlias(mirrorAlias.value(), +100);
        body = CryptoTransferTransactionBody.newBuilder()
                .transfers(
                        TransferList.newBuilder().accountAmounts(mirrorAdjust).build())
                .build();
        txn = asTxn(body, payerId);
        given(handleContext.body()).willReturn(txn);
        ensureAliasesStep = new EnsureAliasesStep(body);
        transferContext = new TransferContextImpl(handleContext);

        ensureAliasesStep.doIn(transferContext);

        assertThat(transferContext.resolutions()).containsEntry(mirrorAlias.value(), payerId);
        assertThat(transferContext.numOfLazyCreations()).isZero();
    }

    @Test
    void resolvesMirrorAddressInNftTransfer() {
        body = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(TokenTransferList.newBuilder()
                        .token(nonFungibleTokenId)
                        .nftTransfers(NftTransfer.newBuilder()
                                .receiverAccountID(AccountID.newBuilder()
                                        .alias(mirrorAlias.value())
                                        .build())
                                .senderAccountID(payerId)
                                .serialNumber(1)
                                .build())
                        .build())
                .build();
        txn = asTxn(body, payerId);
        given(handleContext.body()).willReturn(txn);
        ensureAliasesStep = new EnsureAliasesStep(body);
        transferContext = new TransferContextImpl(handleContext);

        ensureAliasesStep.doIn(transferContext);

        assertThat(transferContext.resolutions()).containsEntry(mirrorAlias.value(), payerId);
        assertThat(transferContext.numOfLazyCreations()).isZero();
    }

    private void setUpInsertingKnownAliasesToState() {
        final var readableBuilder = emptyReadableAliasStateBuilder();
        readableBuilder.value(ecKeyAlias, asAccount(hbarReceiver));
        readableBuilder.value(edKeyAlias, asAccount(tokenReceiver));
        readableAliases = readableBuilder.build();

        final var writableBuilder = emptyWritableAliasStateBuilder();
        writableBuilder.value(ecKeyAlias, asAccount(hbarReceiver));
        writableBuilder.value(edKeyAlias, asAccount(tokenReceiver));
        writableAliases = writableBuilder.build();

        given(writableStates.<ProtoBytes, AccountID>get(ALIASES)).willReturn(writableAliases);
        writableAccountStore = new WritableAccountStore(writableStates, writableEntityCounters);

        writableAccountStore.put(account.copyBuilder()
                .accountId(hbarReceiverId)
                .alias(ecKeyAlias.value())
                .build());
        writableAccountStore.put(account.copyBuilder()
                .accountId(tokenReceiverId)
                .alias(edKeyAlias.value())
                .build());

        given(storeFactory.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        transferContext = new TransferContextImpl(handleContext);
    }

    @Test
    void doesntAutoCreateWhenTransferToAliasFeatureDisabled() {
        configuration = HederaTestConfigBuilder.create()
                .withValue("autoCreation.enabled", false)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(configuration);
        transferContext = new TransferContextImpl(handleContext);
        assertThatThrownBy(() -> ensureAliasesStep.doIn(transferContext))
                .isInstanceOf(WorkflowException.class)
                .has(responseCode(NOT_SUPPORTED));
    }

    @Test
    void doesntAutoCreateWhenTokenTransferToAliasFeatureDisabled() {
        configuration = HederaTestConfigBuilder.create()
                .withValue("tokens.autoCreations.isEnabled", false)
                .getOrCreateConfig();
        body = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(TokenTransferList.newBuilder()
                        .token(fungibleTokenId)
                        .transfers(List.of(aaWith(ownerId, -1_000), aaWith(unknownAliasedId1, +1_000)))
                        .build())
                .build();
        txn = asTxn(body, payerId);
        given(handleContext.body()).willReturn(txn);
        given(handleContext.configuration()).willReturn(configuration);

        ensureAliasesStep = new EnsureAliasesStep(body);
        transferContext = new TransferContextImpl(handleContext);

        assertThatThrownBy(() -> ensureAliasesStep.doIn(transferContext))
                .isInstanceOf(WorkflowException.class)
                .has(responseCode(NOT_SUPPORTED));
    }
}
