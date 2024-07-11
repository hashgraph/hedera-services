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

package com.hedera.node.app.service.token.impl.test.handlers.transfer;

import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.test.handlers.transfer.AccountAmountUtils.aaWith;
import static com.hedera.node.app.service.token.impl.test.handlers.transfer.AccountAmountUtils.aaWithAllowance;
import static com.hedera.node.app.service.token.impl.test.handlers.transfer.AccountAmountUtils.nftTransferWith;
import static com.hedera.node.app.service.token.impl.test.handlers.transfer.AccountAmountUtils.nftTransferWithAllowance;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.transfer.AdjustFungibleTokenChangesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.AdjustHbarChangesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.AssociateTokenRecipientsStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.EnsureAliasesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.NFTOwnersChangeStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.ReplaceAliasesWithIDsInOp;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.test.fixtures.FakeCryptoCreateRecordBuilder;
import com.hedera.node.app.service.token.impl.test.fixtures.FakeCryptoTransferRecordBuilder;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.records.RecordBuilders;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.config.ConfigProvider;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import java.util.function.Predicate;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Provides common setup for transfer handler tests.
 */
@ExtendWith(MockitoExtension.class)
public class StepsBase extends CryptoTokenHandlerTestBase {
    protected CryptoTransferRecordBuilder xferRecordBuilder = new FakeCryptoTransferRecordBuilder().create();
    protected CryptoCreateRecordBuilder cryptoCreateRecordBuilder = new FakeCryptoCreateRecordBuilder().create();

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected ConfigProvider configProvider;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected HandleContext handleContext;

    @Mock(strictness = Mock.Strictness.LENIENT)
    protected RecordBuilders recordBuilders;

    @Mock
    protected ExpiryValidator expiryValidator;

    protected EnsureAliasesStep ensureAliasesStep;
    protected ReplaceAliasesWithIDsInOp replaceAliasesWithIDsInOp;
    protected AssociateTokenRecipientsStep associateTokenRecepientsStep;
    protected NFTOwnersChangeStep changeNFTOwnersStep;
    protected AdjustHbarChangesStep adjustHbarChangesStep;
    protected AdjustFungibleTokenChangesStep adjustFungibleTokenChangesStep;
    protected CryptoTransferTransactionBody body;
    protected TransactionBody txn;
    protected TransferContextImpl transferContext;

    @BeforeEach
    public void setUp() {
        baseInternalSetUp(true);
    }

    protected void baseInternalSetUp(final boolean prepopulateReceiverIds) {
        super.handlerTestBaseInternalSetUp(prepopulateReceiverIds);
        refreshWritableStores();
        given(handleContext.recordBuilders()).willReturn(recordBuilders);
    }

    protected final AccountID unknownAliasedId =
            AccountID.newBuilder().alias(ecKeyAlias.value()).build();
    protected final AccountID unknownAliasedId1 =
            AccountID.newBuilder().alias(edKeyAlias.value()).build();

    public static final Key AN_ED25519_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("0101010101010101010101010101010101010101010101010101010101010101"))
            .build();
    protected static final ProtoBytes edKeyAlias = new ProtoBytes(Key.PROTOBUF.toBytes(AN_ED25519_KEY));
    protected static final byte[] ecdsaKeyBytes =
            Hex.decode("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
    protected static final ProtoBytes ecKeyAlias = new ProtoBytes(Bytes.wrap(ecdsaKeyBytes));

    protected static final byte[] evmAddress = unhex("0000000000000000000000000000000000000003");
    protected static final ProtoBytes mirrorAlias = new ProtoBytes(Bytes.wrap(evmAddress));

    protected TransactionBody asTxn(final CryptoTransferTransactionBody body, final AccountID payerId) {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(payerId)
                        .transactionValidStart(consensusTimestamp)
                        .build())
                .cryptoTransfer(body)
                .build();
    }

    protected void givenTxn() {
        body = CryptoTransferTransactionBody.newBuilder()
                .transfers(TransferList.newBuilder()
                        .accountAmounts(aaWith(ownerId, -1_000), aaWith(unknownAliasedId, +1_000))
                        .build())
                .tokenTransfers(
                        TokenTransferList.newBuilder()
                                .token(fungibleTokenId)
                                .expectedDecimals(1000)
                                .transfers(List.of(aaWith(ownerId, -1_000), aaWith(unknownAliasedId1, +1_000)))
                                .build(),
                        TokenTransferList.newBuilder()
                                .token(nonFungibleTokenId)
                                .expectedDecimals(1000)
                                .nftTransfers(nftTransferWith(ownerId, unknownAliasedId1, 1))
                                .build())
                .build();
        givenTxn(body, payerId);
    }

    protected void givenTxnWithAllowances() {
        body = CryptoTransferTransactionBody.newBuilder()
                .transfers(TransferList.newBuilder()
                        .accountAmounts(aaWithAllowance(ownerId, -1_000), aaWith(unknownAliasedId, +1_000))
                        .build())
                .tokenTransfers(
                        TokenTransferList.newBuilder()
                                .expectedDecimals(1000)
                                .token(fungibleTokenId)
                                .transfers(List.of(aaWithAllowance(ownerId, -1_000), aaWith(unknownAliasedId1, +1_000)))
                                .build(),
                        TokenTransferList.newBuilder()
                                .token(nonFungibleTokenId)
                                .nftTransfers(nftTransferWithAllowance(ownerId, unknownAliasedId1, 1))
                                .build())
                .build();
        givenTxn(body, spenderId);
    }

    protected void givenTxn(CryptoTransferTransactionBody txnBody, AccountID payerId) {
        body = txnBody;
        txn = asTxn(body, payerId);
        given(handleContext.payer()).willReturn(payerId);
        given(handleContext.body()).willReturn(txn);
        given(handleContext.configuration()).willReturn(configuration);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(handleContext.dispatchRemovableChildTransaction(
                        any(),
                        eq(CryptoCreateRecordBuilder.class),
                        any(Predicate.class),
                        eq(payerId),
                        any(ExternalizedRecordCustomizer.class)))
                .willReturn(cryptoCreateRecordBuilder);
        given(handleContext.dispatchComputeFees(any(), any())).willReturn(new Fees(1l, 2l, 3l));
        transferContext = new TransferContextImpl(handleContext);
        given(configProvider.getConfiguration()).willReturn(versionedConfig);
        //        given(handleContext.feeCalculator()).willReturn(fees);
        //        given(fees.computeFees(any(), any())).willReturn(new FeeObject(100, 100, 100));
    }

    protected void givenAutoCreationDispatchEffects() {
        givenAutoCreationDispatchEffects(spenderId);
    }

    protected void givenAutoCreationDispatchEffects(AccountID syntheticPayer) {
        given(handleContext.dispatchRemovablePrecedingTransaction(
                        any(), eq(CryptoCreateRecordBuilder.class), eq(null), eq(syntheticPayer)))
                .will((invocation) -> {
                    final var copy = writableAccountStore
                            .get(hbarReceiverId)
                            .copyBuilder()
                            .alias(ecKeyAlias.value())
                            .build();
                    writableAccountStore.put(copy);
                    writableAliases.put(ecKeyAlias, asAccount(hbarReceiver));
                    return cryptoCreateRecordBuilder.accountID(asAccount(hbarReceiver));
                })
                .will((invocation) -> {
                    final var copy = writableAccountStore
                            .get(tokenReceiverId)
                            .copyBuilder()
                            .alias(edKeyAlias.value())
                            .build();
                    writableAccountStore.put(copy);
                    writableAliases.put(edKeyAlias, asAccount(tokenReceiver));
                    return cryptoCreateRecordBuilder.accountID(asAccount(tokenReceiver));
                });
        given(storeFactory.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(recordBuilders.getOrCreate(CryptoCreateRecordBuilder.class)).willReturn(cryptoCreateRecordBuilder);
        given(recordBuilders.getOrCreate(CryptoTransferRecordBuilder.class)).willReturn(xferRecordBuilder);
    }
}
