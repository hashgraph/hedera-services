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

package com.hedera.node.app.fees;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.node.app.workflows.handle.HandleContextImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChildFeeContextImplTest {
    private static final Configuration DEFAULT_CONFIG = HederaTestConfigBuilder.createConfig();
    private static final Instant NOW = Instant.ofEpochSecond(1_234_567, 890);
    private static final AccountID PAYER_ID =
            AccountID.newBuilder().accountNum(666L).build();
    private static final TransactionBody SAMPLE_BODY = TransactionBody.newBuilder()
            .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                    .tokenTransfers(TokenTransferList.newBuilder()
                            .token(TokenID.newBuilder().tokenNum(666L).build())
                            .transfers(
                                    AccountAmount.newBuilder()
                                            .accountID(AccountID.newBuilder().accountNum(1234))
                                            .amount(-1000)
                                            .build(),
                                    AccountAmount.newBuilder()
                                            .accountID(AccountID.newBuilder().accountNum(5678))
                                            .amount(+1000)
                                            .build())
                            .build()))
            .build();

    @Mock
    private Authorizer authorizer;

    @Mock
    private FeeManager feeManager;

    @Mock
    private FeeCalculator feeCalculator;

    @Mock
    private HandleContextImpl context;

    @Mock
    private ReadableAccountStore readableAccountStore;

    private ChildFeeContextImpl subject;

    @BeforeEach
    void setUp() {
        subject = new ChildFeeContextImpl(feeManager, context, SAMPLE_BODY, PAYER_ID, true);
    }

    @Test
    void returnsChildBody() {
        assertSame(SAMPLE_BODY, subject.body());
    }

    @Test
    void delegatesFeeCalculatorCreation() {
        given(context.consensusNow()).willReturn(NOW);
        given(context.savepointStack()).willReturn(new SavepointStackImpl(new FakeHederaState()));
        given(feeManager.createFeeCalculator(
                        eq(SAMPLE_BODY),
                        eq(Key.DEFAULT),
                        eq(HederaFunctionality.CRYPTO_TRANSFER),
                        eq(0),
                        eq(0),
                        eq(NOW),
                        eq(SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES),
                        eq(true),
                        any(ReadableStoreFactory.class)))
                .willReturn(feeCalculator);
        assertSame(feeCalculator, subject.feeCalculator(SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES));
    }

    @Test
    void propagatesInvalidBodyAsIllegalStateException() {
        given(context.savepointStack()).willReturn(new SavepointStackImpl(new FakeHederaState()));
        subject = new ChildFeeContextImpl(feeManager, context, TransactionBody.DEFAULT, PAYER_ID, true);
        assertThrows(
                IllegalStateException.class,
                () -> subject.feeCalculator(SubType.TOKEN_FUNGIBLE_COMMON_WITH_CUSTOM_FEES));
    }

    @Test
    void delegatesReadableStoreCreation() {
        given(context.readableStore(ReadableAccountStore.class)).willReturn(readableAccountStore);

        assertSame(readableAccountStore, subject.readableStore(ReadableAccountStore.class));
    }

    @Test
    void delegatesConfiguration() {
        given(context.configuration()).willReturn(DEFAULT_CONFIG);

        assertSame(DEFAULT_CONFIG, subject.configuration());
    }

    @Test
    void delegatesAuthorizer() {
        given(context.authorizer()).willReturn(authorizer);

        assertSame(authorizer, subject.authorizer());
    }
}
