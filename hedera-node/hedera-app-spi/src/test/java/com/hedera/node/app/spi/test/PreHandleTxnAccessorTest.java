/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.test;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.spi.PreHandleTxnAccessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PreHandleTxnAccessorTest {
    @Mock private PreHandleTxnAccessor subject;

    @Test
    void getsPreTxnHandlersCorrectly() {
        willCallRealMethod().given(subject).getPreTxnHandler(any());

        subject.getPreTxnHandler(CryptoCreate);
        verify(subject, times(1)).getCryptoPreTransactionHandler();

        subject.getPreTxnHandler(CryptoDelete);
        verify(subject, times(2)).getCryptoPreTransactionHandler();

        subject.getPreTxnHandler(CryptoUpdate);
        verify(subject, times(3)).getCryptoPreTransactionHandler();

        subject.getPreTxnHandler(CryptoTransfer);
        verify(subject, times(4)).getCryptoPreTransactionHandler();

        subject.getPreTxnHandler(CryptoApproveAllowance);
        verify(subject, times(5)).getCryptoPreTransactionHandler();

        subject.getPreTxnHandler(CryptoDeleteAllowance);
        verify(subject, times(6)).getCryptoPreTransactionHandler();

        subject.getPreTxnHandler(ScheduleCreate);
        verify(subject, times(1)).getSchedulePreTransactionHandler();

        subject.getPreTxnHandler(ScheduleSign);
        verify(subject, times(2)).getSchedulePreTransactionHandler();

        subject.getPreTxnHandler(ScheduleDelete);
        verify(subject, times(3)).getSchedulePreTransactionHandler();

        assertThrows(
                UnsupportedOperationException.class, () -> subject.getPreTxnHandler(TokenCreate));
        assertThrows(
                UnsupportedOperationException.class, () -> subject.getPreTxnHandler(TokenBurn));
        assertThrows(
                UnsupportedOperationException.class, () -> subject.getPreTxnHandler(TokenDelete));
        assertThrows(
                UnsupportedOperationException.class, () -> subject.getPreTxnHandler(TokenMint));
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.getPreTxnHandler(TokenAccountWipe));
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.getPreTxnHandler(TokenAssociateToAccount));
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.getPreTxnHandler(TokenDissociateFromAccount));
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.getPreTxnHandler(TokenFeeScheduleUpdate));
        assertThrows(
                UnsupportedOperationException.class, () -> subject.getPreTxnHandler(FileAppend));
        assertThrows(
                UnsupportedOperationException.class, () -> subject.getPreTxnHandler(FileCreate));
        assertThrows(
                UnsupportedOperationException.class, () -> subject.getPreTxnHandler(FileUpdate));
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.getPreTxnHandler(ConsensusCreateTopic));
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.getPreTxnHandler(ConsensusSubmitMessage));
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.getPreTxnHandler(ConsensusDeleteTopic));
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.getPreTxnHandler(ConsensusUpdateTopic));
    }
}
