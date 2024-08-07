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

package com.hedera.node.app.service.token.impl.test.handlers;

import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.token.TokenClaimAirdropTransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class TokenClaimAirdropHandlerTest extends CryptoTransferHandlerTestBase {

    @Mock(strictness = LENIENT)
    protected PreHandleContext preHandleContext;

    @Mock
    private ReadableAccountStore accountStore;

    @SuppressWarnings("DataFlowIssue")
    @Test
    void pureChecksNullArgThrows() {
        Assertions.assertThatThrownBy(() -> tokenClaimAirdropHandler.pureChecks(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void pureChecksEmptyAirDropIDListThrows() {
        final var txn = newTokenClaimAirdrop(
                TokenClaimAirdropTransactionBody.newBuilder().build());
        final var msg = assertThrows(PreCheckException.class, () -> tokenClaimAirdropHandler.pureChecks(txn));
        assertEquals(ResponseCodeEnum.EMPTY_PENDING_AIRDROP_ID_LIST, msg.responseCode());
    }

    @Test
    void pureChecksPendingAirDropListIsNullThrows() {
        final var txn = newTokenClaimAirdrop(TokenClaimAirdropTransactionBody.newBuilder()
                .pendingAirdrops((List<PendingAirdropId>) null)
                .build());
        final var msg = assertThrows(PreCheckException.class, () -> tokenClaimAirdropHandler.pureChecks(txn));
        assertEquals(ResponseCodeEnum.EMPTY_PENDING_AIRDROP_ID_LIST, msg.responseCode());
    }

    @Test
    void pureChecksPendingAirDropDuplicateThrows() {
        final List<PendingAirdropId> pendingAirdropIds = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            pendingAirdropIds.add(PendingAirdropId.newBuilder()
                    .receiverId(ACCOUNT_ID_3333)
                    .senderId(ACCOUNT_ID_4444)
                    .fungibleTokenType(TOKEN_2468)
                    .build());
        }
        final var txn = newTokenClaimAirdrop(TokenClaimAirdropTransactionBody.newBuilder()
                .pendingAirdrops(pendingAirdropIds)
                .build());
        final var msg = assertThrows(PreCheckException.class, () -> tokenClaimAirdropHandler.pureChecks(txn));
        assertEquals(ResponseCodeEnum.PENDING_NFT_AIRDROP_ALREADY_EXISTS, msg.responseCode());
    }

    @Test
    void pureChecksHasValidPath() {
        final List<PendingAirdropId> pendingAirdropIds = new ArrayList<>();
        final var token9754 = asToken(9754);
        pendingAirdropIds.add(PendingAirdropId.newBuilder()
                .receiverId(ACCOUNT_ID_3333)
                .senderId(ACCOUNT_ID_4444)
                .fungibleTokenType(TOKEN_2468)
                .build());
        pendingAirdropIds.add(PendingAirdropId.newBuilder()
                .receiverId(ACCOUNT_ID_3333)
                .senderId(ACCOUNT_ID_4444)
                .fungibleTokenType(token9754)
                .build());
        pendingAirdropIds.add(PendingAirdropId.newBuilder()
                .receiverId(ACCOUNT_ID_3333)
                .senderId(ACCOUNT_ID_4444)
                .nonFungibleToken(NFT_ID)
                .build());
        final var txn = newTokenClaimAirdrop(TokenClaimAirdropTransactionBody.newBuilder()
                .pendingAirdrops(pendingAirdropIds)
                .build());
        Assertions.assertThatCode(() -> tokenClaimAirdropHandler.pureChecks(txn))
                .doesNotThrowAnyException();
    }

    @Test
    void preHAndleAccountNotExistPath() {
        final List<PendingAirdropId> pendingAirdropIds = new ArrayList<>();
        final var token9754 = asToken(9754);
        pendingAirdropIds.add(PendingAirdropId.newBuilder()
                .receiverId(ACCOUNT_ID_3333)
                .senderId(ACCOUNT_ID_4444)
                .fungibleTokenType(TOKEN_2468)
                .build());
        pendingAirdropIds.add(PendingAirdropId.newBuilder()
                .receiverId(ACCOUNT_ID_3333)
                .senderId(ACCOUNT_ID_4444)
                .fungibleTokenType(token9754)
                .build());
        pendingAirdropIds.add(PendingAirdropId.newBuilder()
                .receiverId(ACCOUNT_ID_3333)
                .senderId(ACCOUNT_ID_4444)
                .nonFungibleToken(NFT_ID)
                .build());
        final var txn = newTokenClaimAirdrop(TokenClaimAirdropTransactionBody.newBuilder()
                .pendingAirdrops(pendingAirdropIds)
                .build());
        given(preHandleContext.body()).willReturn(txn);
        given(preHandleContext.createStore(ReadableAccountStore.class)).willReturn(accountStore);
        given(accountStore.getAccountById(any())).willReturn(null);

        Assertions.assertThatThrownBy(() -> tokenClaimAirdropHandler.preHandle(preHandleContext))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void preHandleHasValidPath() {
        final List<PendingAirdropId> pendingAirdropIds = new ArrayList<>();
        final var token9754 = asToken(9754);
        pendingAirdropIds.add(PendingAirdropId.newBuilder()
                .receiverId(ACCOUNT_ID_3333)
                .senderId(ACCOUNT_ID_4444)
                .fungibleTokenType(TOKEN_2468)
                .build());
        pendingAirdropIds.add(PendingAirdropId.newBuilder()
                .receiverId(ACCOUNT_ID_3333)
                .senderId(ACCOUNT_ID_4444)
                .fungibleTokenType(token9754)
                .build());
        pendingAirdropIds.add(PendingAirdropId.newBuilder()
                .receiverId(ACCOUNT_ID_3333)
                .senderId(ACCOUNT_ID_4444)
                .nonFungibleToken(NFT_ID)
                .build());
        final var txn = newTokenClaimAirdrop(TokenClaimAirdropTransactionBody.newBuilder()
                .pendingAirdrops(pendingAirdropIds)
                .build());
        given(preHandleContext.body()).willReturn(txn);
        given(preHandleContext.createStore(ReadableAccountStore.class)).willReturn(accountStore);
        given(accountStore.getAccountById(any())).willReturn(ACCOUNT_3333);

        Assertions.assertThatCode(() -> tokenClaimAirdropHandler.preHandle(preHandleContext))
                .doesNotThrowAnyException();
    }
}
