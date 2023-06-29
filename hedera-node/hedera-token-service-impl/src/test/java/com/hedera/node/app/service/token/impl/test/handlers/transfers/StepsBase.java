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
import static com.hedera.node.app.service.token.impl.test.handlers.transfers.Utils.aaWith;
import static com.hedera.node.app.service.token.impl.test.handlers.transfers.Utils.nftTransferWith;
import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.google.common.primitives.Longs;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.service.token.impl.handlers.transfer.EnsureAliasesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.ReplaceAliasesWithIDsInOp;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Arrays;
import java.util.List;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class StepsBase extends CryptoTokenHandlerTestBase {
    @Mock(strictness = Mock.Strictness.LENIENT)
    protected HandleContext handleContext;

    @Mock
    protected ExpiryValidator expiryValidator;

    protected EnsureAliasesStep ensureAliasesStep;
    protected ReplaceAliasesWithIDsInOp replaceAliasesWithIDsInOp;
    protected CryptoTransferTransactionBody body;
    protected TransactionBody txn;
    protected TransferContextImpl transferContext;
    protected SingleTransactionRecordBuilder recordBuilder;

    @BeforeEach
    public void setUp() {
        super.setUp();
        recordBuilder = new SingleTransactionRecordBuilder(consensusInstant);
    }

    protected final AccountID unknownAliasedId =
            AccountID.newBuilder().alias(ecKeyAlias).build();
    protected final AccountID unknownAliasedId1 =
            AccountID.newBuilder().alias(edKeyAlias).build();

    protected static final Key aPrimitiveKey = Key.newBuilder()
            .ed25519(Bytes.wrap("01234567890123456789012345678911"))
            .build();
    protected static final Bytes edKeyAlias = Bytes.wrap(asBytes(Key.PROTOBUF, aPrimitiveKey));
    protected static final byte[] ecdsaKeyBytes =
            Hex.decode("3a21033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d");
    protected static final Bytes ecKeyAlias = Bytes.wrap(ecdsaKeyBytes);

    protected static final byte[] evmAddress = unhex("0000000000000000000000000000000000000003");
    protected static final byte[] create2Address = unhex("0111111111111111111111111111111111defbbb");
    protected static final Bytes mirrorAlias = Bytes.wrap(evmAddress);
    protected static final Bytes create2Alias = Bytes.wrap(create2Address);
    protected static final Long mirrorNum = Longs.fromByteArray(Arrays.copyOfRange(evmAddress, 12, 20));
    protected final int createdNumber = 10000000;

    protected TransactionBody asTxn(final CryptoTransferTransactionBody body) {
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
                                .transfers(List.of(aaWith(ownerId, -1_000), aaWith(unknownAliasedId1, +1_000)))
                                .build(),
                        TokenTransferList.newBuilder()
                                .token(nonFungibleTokenId)
                                .nftTransfers(nftTransferWith(ownerId, unknownAliasedId1, 1))
                                .build())
                .build();
        givenTxn(body);
    }

    protected void givenTxn(CryptoTransferTransactionBody txnBody) {
        body = txnBody;
        txn = asTxn(body);
        given(handleContext.body()).willReturn(txn);
        given(handleContext.configuration()).willReturn(configuration);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(handleContext.dispatchRemovableChildTransaction(any(), eq(CryptoCreateRecordBuilder.class)))
                .willReturn(recordBuilder);
        //        given(handleContext.feeCalculator()).willReturn(fees);
        //        given(fees.computePayment(any(), any())).willReturn(new FeeObject(100, 100, 100));
    }
}
