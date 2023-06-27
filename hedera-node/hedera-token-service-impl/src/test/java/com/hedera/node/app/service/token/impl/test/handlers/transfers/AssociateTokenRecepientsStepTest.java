/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.token.impl.test.handlers.transfers;

import static com.hedera.test.utils.IdUtils.adjustFrom;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.impl.handlers.transfer.AssociateTokenRecepientsStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.List;

public class AssociateTokenRecepientsStepTest  extends CryptoTokenHandlerTestBase {
    @Mock
    private HandleContext handleContext;
    private AssociateTokenRecepientsStep subject;
    private CryptoTransferTransactionBody txn;
    private TransferContextImpl transferContext;

    @BeforeEach
    public void setUp() {
        super.setUp();
        givenTxn();
        subject = new AssociateTokenRecepientsStep(txn);
        transferContext = new TransferContextImpl(handleContext);
    }

    @Test
    void associatesTokenRecepients() {
        subject.doIn(transferContext);
    }

    private void givenTxn(){
        txn = CryptoTransferTransactionBody.newBuilder()
                .transfers(TransferList.newBuilder()
                        .accountAmounts(adjustFrom(transferAccountId, -1_000))
                        .accountAmounts(adjustFrom(deleteAccountId, +1_000))
                        .build())
                .tokenTransfers(TokenTransferList.newBuilder()
                        .token(fungibleTokenId)
                        .transfers(List.of(
                                adjustFrom(ownerId, -1_000),
                                adjustFrom(spenderId, +1_000)))
                .build())
                .tokenTransfers(TokenTransferList.newBuilder()
                        .token(nonFungibleTokenId)
                        .nftTransfers(nftTransferWith(ownerId, spenderId, 1))
                        .build())
                .build();
        given(handleContext.configuration()).willReturn(configuration);
    }

    public static AccountAmount adjustFrom(AccountID account, long amount) {
        return AccountAmount.newBuilder()
                .accountID(account)
                .amount(amount)
                .build();
    }

    public static AccountAmount adjustFromWithAllowance(AccountID account, long amount) {
        return AccountAmount.newBuilder()
                .accountID(account)
                .amount(amount)
                .isApproval(true)
                .build();
    }

    public static AccountID asAccountWithAlias(String alias) {
        return AccountID.newBuilder().alias(Bytes.wrap(alias)).build();
    }

    public static NftTransfer nftTransferWith(AccountID from, AccountID to, long serialNo) {
        return NftTransfer.newBuilder()
                .senderAccountID(from)
                .receiverAccountID(to)
                .serialNumber(serialNo)
                .build();
    }
}
