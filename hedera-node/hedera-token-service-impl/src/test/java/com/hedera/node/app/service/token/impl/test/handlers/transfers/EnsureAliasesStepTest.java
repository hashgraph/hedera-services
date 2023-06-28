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

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.service.token.impl.handlers.transfer.AutoAccountCreationStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.EnsureAliasesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.node.app.service.token.impl.test.handlers.transfers.AssociateTokenRecepientsStepTest.adjustFrom;
import static com.hedera.node.app.service.token.impl.test.handlers.transfers.AssociateTokenRecepientsStepTest.nftTransferWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
public class EnsureAliasesStepTest extends CryptoTokenHandlerTestBase {
    @Mock(strictness = Mock.Strictness.LENIENT)
    private HandleContext handleContext;

    @Mock
    private ExpiryValidator expiryValidator;
    @Mock
    private FeeCalculator fees;

    private EnsureAliasesStep subject;
    private CryptoTransferTransactionBody body;
    private TransactionBody txn;
    private TransferContextImpl transferContext;
    private SingleTransactionRecordBuilder recordBuilder;

    private final AccountID unknownAliasedId = AccountID.newBuilder()
            .alias(ecKeyAlias)
            .build();
    private final AccountID unknownAliasedId1 = AccountID.newBuilder()
            .alias(edKeyAlias)
            .build();

    private static final Key aPrimitiveKey = Key.newBuilder()
            .ed25519(Bytes.wrap("01234567890123456789012345678901"))
            .build();
    private static final Bytes edKeyAlias = Bytes.wrap("01234567890123456789012345678901");
    private static final byte[] ecdsaKeyBytes =
            Hex.decode("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
    private static final Bytes ecKeyAlias = Bytes.wrap(ecdsaKeyBytes);;

    @BeforeEach
    public void setUp() {
        super.setUp();
        givenTxn();
        refreshWritableStores();
        givenStoresAndConfig(handleContext);
        subject = new EnsureAliasesStep(body);
        transferContext = new TransferContextImpl(handleContext);
        recordBuilder = new SingleTransactionRecordBuilder(consensusInstant);
    }

    @Test
    void autoCreatesAccounts() {
        assertThat(writableAccountStore.modifiedAccountsInState()).hasSize(0);

        subject.doIn(transferContext);

        assertThat(writableAccountStore.modifiedAccountsInState()).hasSize(2);

    }

    private void givenTxn() {
        body = CryptoTransferTransactionBody.newBuilder()
                .transfers(TransferList.newBuilder()
                        .accountAmounts(adjustFrom(ownerId, -1_000))
                        .accountAmounts(adjustFrom(unknownAliasedId, +1_000))
                        .build())
                .tokenTransfers(
                        TokenTransferList.newBuilder()
                                .token(fungibleTokenId)
                                .transfers(List.of(adjustFrom(ownerId, -1_000),
                                        adjustFrom(unknownAliasedId1, +1_000)))
                                .build(),
                        TokenTransferList.newBuilder()
                                .token(nonFungibleTokenId)
                                .nftTransfers(nftTransferWith(ownerId, unknownAliasedId1, 1))
                                .build())
                .build();
        txn = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder()
                        .accountID(payerId)
                        .transactionValidStart(consensusTimestamp).build())
                .cryptoTransfer(body)
                .build();
        given(handleContext.body()).willReturn(txn);
        given(handleContext.configuration()).willReturn(configuration);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(handleContext.feeCalculator()).willReturn(fees);
        given(handleContext.dispatchRemovableChildTransaction(any(), eq(CryptoCreateRecordBuilder.class)))
                .willReturn(recordBuilder);
        given(fees.computePayment(any(), any())).willReturn(new FeeObject(100, 100, 100));
        given(expiryValidator.expirationStatus(any(), anyBoolean(), anyLong())).willReturn(ResponseCodeEnum.OK);
    }
}
