/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.transfer;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.SpecialRewardReceivers.SPECIAL_REWARD_RECEIVERS;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.HAPI_RECORD_BUILDER_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.B_NEW_ACCOUNT_ID;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import com.hedera.node.app.service.contract.impl.records.ContractOperationStreamBuilder;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SpecialRewardReceiversTest {
    @Mock
    private MessageFrame frame;

    @Mock
    private MessageFrame initialFrame;

    @Mock
    private ContractOperationStreamBuilder recordBuilder;

    private final Deque<MessageFrame> stack = new ArrayDeque<>();

    @BeforeEach
    void setUp() {
        stack.push(initialFrame);
        stack.addFirst(frame);
        given(frame.getMessageFrameStack()).willReturn(stack);
        given(initialFrame.getContextVariable(HAPI_RECORD_BUILDER_CONTEXT_VARIABLE))
                .willReturn(recordBuilder);
    }

    @Test
    void addsFungibleTokenTransfers() {
        final var body = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(TokenTransferList.newBuilder()
                        .transfers(List.of(
                                AccountAmount.newBuilder()
                                        .accountID(A_NEW_ACCOUNT_ID)
                                        .build(),
                                AccountAmount.newBuilder()
                                        .accountID(B_NEW_ACCOUNT_ID)
                                        .build()))
                        .build())
                .build();
        SPECIAL_REWARD_RECEIVERS.addInFrame(frame, body, List.of());

        verify(recordBuilder).trackExplicitRewardSituation(A_NEW_ACCOUNT_ID);
        verify(recordBuilder).trackExplicitRewardSituation(B_NEW_ACCOUNT_ID);
    }

    @Test
    void addsNftOwnershipChanges() {
        final var body = CryptoTransferTransactionBody.newBuilder()
                .tokenTransfers(TokenTransferList.newBuilder()
                        .nftTransfers(new NftTransfer(A_NEW_ACCOUNT_ID, B_NEW_ACCOUNT_ID, 123L, true))
                        .build())
                .build();
        SPECIAL_REWARD_RECEIVERS.addInFrame(frame, body, List.of());

        verify(recordBuilder).trackExplicitRewardSituation(A_NEW_ACCOUNT_ID);
        verify(recordBuilder).trackExplicitRewardSituation(B_NEW_ACCOUNT_ID);
    }

    @Test
    void addsHbarTransfers() {
        final var body = CryptoTransferTransactionBody.newBuilder()
                .transfers(TransferList.newBuilder()
                        .accountAmounts(List.of(
                                AccountAmount.newBuilder()
                                        .accountID(A_NEW_ACCOUNT_ID)
                                        .build(),
                                AccountAmount.newBuilder()
                                        .accountID(B_NEW_ACCOUNT_ID)
                                        .build()))
                        .build())
                .build();
        SPECIAL_REWARD_RECEIVERS.addInFrame(frame, body, List.of());

        verify(recordBuilder).trackExplicitRewardSituation(A_NEW_ACCOUNT_ID);
        verify(recordBuilder).trackExplicitRewardSituation(B_NEW_ACCOUNT_ID);
    }

    @Test
    void tracksFeeCollectionAccounts() {
        SPECIAL_REWARD_RECEIVERS.addInFrame(
                frame,
                CryptoTransferTransactionBody.DEFAULT,
                List.of(AssessedCustomFee.newBuilder()
                        .feeCollectorAccountId(A_NEW_ACCOUNT_ID)
                        .build()));
        verify(recordBuilder).trackExplicitRewardSituation(A_NEW_ACCOUNT_ID);
    }
}
