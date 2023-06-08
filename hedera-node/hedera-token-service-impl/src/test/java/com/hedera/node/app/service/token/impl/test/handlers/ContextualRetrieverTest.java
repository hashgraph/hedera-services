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

import static com.hedera.node.app.service.token.impl.handlers.TokenHandlerHelper.getIfUsable;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.mockito.ArgumentMatchers.notNull;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.workflows.HandleException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContextualRetrieverTest {
    private static final TokenID TOKEN_ID_45 = TokenID.newBuilder().tokenNum(45).build();

    @Mock
    private ReadableTokenStore tokenStore;

    @SuppressWarnings("DataFlowIssue")
    @Test
    void getIfUsable_nullArg() {
        Assertions.assertThatThrownBy(() -> getIfUsable(null, tokenStore)).isInstanceOf(NullPointerException.class);

        Assertions.assertThatThrownBy(() -> getIfUsable(TOKEN_ID_45, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void getIfUsable_nullToken() {
        BDDMockito.given(tokenStore.get(notNull())).willReturn(null);

        Assertions.assertThatThrownBy(() -> getIfUsable(TOKEN_ID_45, tokenStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.INVALID_TOKEN_ID));
    }

    @Test
    void getIfUsable_deletedToken() {
        BDDMockito.given(tokenStore.get(notNull()))
                .willReturn(Token.newBuilder()
                        .tokenNumber(TOKEN_ID_45.tokenNum())
                        .deleted(true)
                        .paused(false)
                        .build());

        Assertions.assertThatThrownBy(() -> getIfUsable(TOKEN_ID_45, tokenStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.TOKEN_WAS_DELETED));
    }

    @Test
    void getIfUsable_pausedToken() {
        BDDMockito.given(tokenStore.get(notNull()))
                .willReturn(Token.newBuilder()
                        .tokenNumber(TOKEN_ID_45.tokenNum())
                        .deleted(false)
                        .paused(true)
                        .build());

        Assertions.assertThatThrownBy(() -> getIfUsable(TOKEN_ID_45, tokenStore))
                .isInstanceOf(HandleException.class)
                .has(responseCode(ResponseCodeEnum.TOKEN_IS_PAUSED));
    }

    @Test
    void getIfUsable_usableToken() {
        BDDMockito.given(tokenStore.get(notNull()))
                .willReturn(Token.newBuilder()
                        .tokenNumber(TOKEN_ID_45.tokenNum())
                        .deleted(false)
                        .paused(false)
                        .build());

        final var result = getIfUsable(TOKEN_ID_45, tokenStore);
        Assertions.assertThat(result).isNotNull();
    }
}
