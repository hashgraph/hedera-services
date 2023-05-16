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

package com.hedera.node.app.service.token.impl.test.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.token.TokenFeeScheduleUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.impl.config.TokenServiceConfig;
import com.hedera.node.app.service.token.impl.handlers.TokenFeeScheduleUpdateHandler;
import com.hedera.node.app.service.token.impl.validators.CustomFeesValidator;
import com.hedera.node.app.spi.meta.HandleContext;
import com.swirlds.config.api.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TokenFeeScheduleUpdateHandlerTest extends HandlerTestBase {
    private TokenFeeScheduleUpdateHandler subject;
    private CustomFeesValidator validator;
    private TransactionBody txn;
    private TokenServiceConfig config = new TokenServiceConfig(1000);

    @Mock
    private HandleContext context;

    @Mock
    private Configuration tokenServiceConfig;

    @BeforeEach
    void setup() {
        super.setUp();
        refreshStoresWithEntitiesInWritable();
        validator = new CustomFeesValidator();
        subject = new TokenFeeScheduleUpdateHandler(validator);
        givenTxn();
        given(context.getConfiguration()).willReturn(tokenServiceConfig);
        given(tokenServiceConfig.getConfigData(TokenServiceConfig.class)).willReturn(config);
        given(context.createReadableStore(ReadableAccountStore.class)).willReturn(readableAccountStore);
        given(context.createReadableStore(ReadableTokenRelationStore.class)).willReturn(readableTokenRelStore);
    }

    @Test
    void handleWorksAsExpectedForFungibleToken() {
        // before fee schedule update
        final var originalToken = writableTokenStore.get(fungibleTokenNum.longValue());
        assertThat(originalToken.get().customFees().size()).isEqualTo(0);
        assertThat(writableTokenStore.modifiedTokens().size()).isEqualTo(0);

        subject.handle(context, txn, writableTokenStore);

        // validate after fee schedule update
        assertThat(writableTokenStore.modifiedTokens().size()).isEqualTo(1);
        assertThat(writableTokenStore.modifiedTokens().contains(fungibleTokenNum));

        final var expectedToken = writableTokenStore.get(fungibleTokenNum.longValue());
        assertThat(expectedToken.get().customFees().size()).isEqualTo(2);
        assertThat(expectedToken.get().customFees().contains(withFixedFee(fixedFee)));
        assertThat(expectedToken.get().customFees().contains(withFractionalFee(fractionalFee)));
    }

    @Test
    void validatesTokenHasFeeScheduleKey() {}

    @Test
    void happyPathWorks() {}

    @Test
    void rejectsInvalidTokenId() {}

    @Test
    void acceptsValidTokenId() {}

    @Test
    void hasCorrectApplicability() {}

    private void givenTxn() {
        txn = TransactionBody.newBuilder()
                .tokenFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody.newBuilder()
                        .tokenId(TokenID.newBuilder()
                                .tokenNum(fungibleTokenNum.longValue())
                                .build())
                        .customFees(customFees)
                        .build())
                .build();
    }
}
