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

import static com.hedera.node.app.service.mono.pbj.PbjConverter.asBytes;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.test.handlers.transfers.Utils.aaAlias;
import static com.hedera.node.app.service.token.impl.test.handlers.transfers.Utils.aaWith;
import static com.hedera.node.app.service.token.impl.test.handlers.transfers.Utils.nftTransferWith;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.google.common.primitives.Longs;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.EnsureAliasesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Arrays;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnsureAliasesStepTest extends CryptoTokenHandlerTestBase {
    @Mock(strictness = Mock.Strictness.LENIENT)
    private HandleContext handleContext;

    @Mock
    private ExpiryValidator expiryValidator;

    private EnsureAliasesStep subject;
    private CryptoTransferTransactionBody body;
    private TransactionBody txn;
    private TransferContextImpl transferContext;
    private SingleTransactionRecordBuilder recordBuilder;

    private final AccountID unknownAliasedId =
            AccountID.newBuilder().alias(ecKeyAlias).build();
    private final AccountID unknownAliasedId1 =
            AccountID.newBuilder().alias(edKeyAlias).build();

    private static final Key aPrimitiveKey = Key.newBuilder()
            .ed25519(Bytes.wrap("01234567890123456789012345678911"))
            .build();
    private static final Bytes edKeyAlias = Bytes.wrap(asBytes(Key.PROTOBUF, aPrimitiveKey));
    private static final byte[] ecdsaKeyBytes =
            Hex.decode("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
    private static final Bytes ecKeyAlias = Bytes.wrap(ecdsaKeyBytes);

    private static final byte[] evmAddress = unhex("0000000000000000000000000000000000000003");
    private static final byte[] create2Address = unhex("0111111111111111111111111111111111defbbb");
    private static final Bytes mirrorAlias = Bytes.wrap(evmAddress);
    private static final Bytes create2Alias = Bytes.wrap(create2Address);
    private static final Long mirrorNum = Longs.fromByteArray(Arrays.copyOfRange(evmAddress, 12, 20));
    private final int createdNumber = 10000000;

    @BeforeEach
    public void setUp() {
        super.setUp();
        recordBuilder = new SingleTransactionRecordBuilder(consensusInstant);
        givenTxn();
        refreshWritableStores();
        givenStoresAndConfig(handleContext);
        subject = new EnsureAliasesStep(body);
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

        subject.doIn(transferContext);

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

        subject.doIn(transferContext);

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
        subject = new EnsureAliasesStep(body);
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

        assertThatThrownBy(() -> subject.doIn(transferContext))
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
        subject = new EnsureAliasesStep(body);
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

        assertThatThrownBy(() -> subject.doIn(transferContext))
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
        subject = new EnsureAliasesStep(body);
        transferContext = new TransferContextImpl(handleContext);

        subject.doIn(transferContext);

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
        subject = new EnsureAliasesStep(body);
        transferContext = new TransferContextImpl(handleContext);

        subject.doIn(transferContext);

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

    private void givenTxn() {
        body = CryptoTransferTransactionBody.newBuilder()
                .transfers(TransferList.newBuilder()
                        .accountAmounts(aaWith(ownerId, -1_000), aaWith(unknownAliasedId, +1_000))
                        .build())
                .tokenTransfers(
                        TokenTransferList.newBuilder()
                                .token(fungibleTokenId)
                                .transfers(List.of(aaWith(ownerId, -1_000), aaWith(unknownAliasedId1, +1_000)))
                                .build(),
                        TokenTransferList.newBuilder()
                                .token(nonFungibleTokenId)
                                .nftTransfers(nftTransferWith(ownerId, unknownAliasedId1, 1))
                                .build())
                .build();
        txn = asTxn(body);
        given(handleContext.body()).willReturn(txn);
        given(handleContext.configuration()).willReturn(configuration);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(handleContext.dispatchRemovableChildTransaction(any(), eq(CryptoCreateRecordBuilder.class)))
                .willReturn(recordBuilder);
        //        given(handleContext.feeCalculator()).willReturn(fees);
        //        given(fees.computePayment(any(), any())).willReturn(new FeeObject(100, 100, 100));
    }

    public TransactionBody asTxn(final CryptoTransferTransactionBody body) {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(payerId)
                        .transactionValidStart(consensusTimestamp)
                        .build())
                .cryptoTransfer(body)
                .build();
    }
}
