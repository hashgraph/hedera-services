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
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.impl.handlers.transfer.AssociateTokenRecipientsStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.CustomFeeAssessmentStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.EnsureAliasesStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.ReplaceAliasesWithIDsInOp;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomFeeAssessmentStepTest extends StepsBase {
    private TransferContextImpl transferContext;
    private CustomFeeAssessmentStep subject;

    @BeforeEach
    public void setUp() {
        super.setUp();
        refreshWritableStores();

        givenStoresAndConfig(handleContext);
        givenTxn();
        given(handleContext.body()).willReturn(txn);
        givenConditions();

        transferContext = new TransferContextImpl(handleContext);
        ensureAliasesStep = new EnsureAliasesStep(body);
        replaceAliasesWithIDsInOp = new ReplaceAliasesWithIDsInOp();
        associateTokenRecepientsStep = new AssociateTokenRecipientsStep(body);

        final var replacedOp = getReplacedOp();
        subject = new CustomFeeAssessmentStep(replacedOp, transferContext);
    }

    @Test
    void chargesFixedAndFractionalFeesForFungible() {
        final var hbarsReceiver = asAccount(hbarReceiver);
        final var tokensReceiver = asAccount(tokenReceiver);

        givenTxn();
        final var collectorBalance = writableAccountStore.get(feeCollectorId).tinybarBalance();
        final var senderBalance = writableAccountStore.get(ownerId).tinybarBalance();

        final var listOfOps = subject.assessCustomFees(transferContext);
        assertThat(listOfOps).hasSize(2);

        final var expectedCollectorBalance = collectorBalance + 10;
        final var expectedSenderBalance = senderBalance - 1000L - 10L;
        final var receiverBalance = writableAccountStore.get(hbarsReceiver).tinybarBalance();

        final var realSenderBalance = writableAccountStore.get(ownerId).tinybarBalance();
        final var realCollectorBalance =
                writableAccountStore.get(feeCollectorId).tinybarBalance();
        assertThat(realSenderBalance).isEqualTo(expectedSenderBalance);
        assertThat(realCollectorBalance).isEqualTo(expectedCollectorBalance);
        assertThat(receiverBalance).isEqualTo(1000L);
    }

    CryptoTransferTransactionBody getReplacedOp() {
        givenConditions();
        ensureAliasesStep.doIn(transferContext);
        associateTokenRecepientsStep.doIn(transferContext);
        return replaceAliasesWithIDsInOp.replaceAliasesWithIds(body, transferContext);
    }
}
