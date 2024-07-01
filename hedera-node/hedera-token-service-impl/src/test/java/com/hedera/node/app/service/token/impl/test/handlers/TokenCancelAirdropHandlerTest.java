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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static com.hedera.node.app.service.token.impl.test.handlers.util.TestStoreFactory.newReadableStoreWithAccounts;
import static com.hedera.node.app.service.token.impl.test.handlers.util.TestStoreFactory.newReadableStoreWithNfts;
import static com.hedera.node.app.service.token.impl.test.handlers.util.TestStoreFactory.newReadableStoreWithTokens;
import static com.hedera.node.app.service.token.impl.test.handlers.util.TestStoreFactory.newWritableStoreWithAirdrops;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.token.TokenCancelAirdropTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAirdropStore;
import com.hedera.node.app.service.token.impl.handlers.TokenCancelAirdropHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.TokenHandlerTestBase;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenCancelAirdropHandlerTest extends TokenHandlerTestBase {

    private TokenCancelAirdropHandler subject;

    @Mock
    private TransactionBody transactionBody;

    @Mock
    private HandleContext handleContext;

    @Mock
    private StoreFactory storeFactory;

    @Mock
    private ExpiryValidator expiryValidator;

    private WritableAirdropStore airdropStore;
    private ReadableAccountStore readableAccountStore;
    private ReadableNftStore readableNftStore;
    protected Configuration testConfig;
    private final AccountID senderId = asAccount(1001);
    private final AccountID receiverId = asAccount(1010);
    private final AccountID invalidAccountId = asAccount(1002);
    private final TokenID fungibleTokenId = asToken(333);
    private final NftID nftId = NftID.newBuilder().tokenId(fungibleTokenId).build();

    @BeforeEach
    public void setup() {
        subject = new TokenCancelAirdropHandler();
        testConfig = HederaTestConfigBuilder.create()
                .withValue("tokens.airdrops.cancel.enabled", true)
                .getOrCreateConfig();
        when(handleContext.configuration()).thenReturn(testConfig);
        when(handleContext.storeFactory()).thenReturn(storeFactory);
        airdropStore = newWritableStoreWithAirdrops();
        when(storeFactory.writableStore(WritableAirdropStore.class)).thenReturn(airdropStore);
    }

    @Test
    void cancelAirdropWithNoPendingAirdropsThrowsException() {
        transactionBody = TransactionBody.newBuilder()
                .tokenCancelAirdrop(tokenCancelAirdrops())
                .build();
        when(handleContext.body()).thenReturn(transactionBody);
        when(handleContext.payer()).thenReturn(senderId);
        when(handleContext.expiryValidator()).thenReturn(expiryValidator);
        when(expiryValidator.expirationStatus(any(), anyBoolean(), anyLong())).thenReturn(OK);
        airdropStore = newWritableStoreWithAirdrops();
        readableTokenStore = newReadableStoreWithTokens(
                Token.newBuilder().tokenId(fungibleTokenId).build());
        readableNftStore =
                newReadableStoreWithNfts(Nft.newBuilder().nftId(nftId).build());
        readableAccountStore = newReadableStoreWithAccounts(
                Account.newBuilder().accountId(senderId).build(),
                Account.newBuilder().accountId(receiverId).build());
        when(storeFactory.readableStore(ReadableNftStore.class)).thenReturn(readableNftStore);
        when(storeFactory.readableStore(ReadableTokenStore.class)).thenReturn(readableTokenStore);
        when(storeFactory.readableStore(ReadableAccountStore.class)).thenReturn(readableAccountStore);
        when(storeFactory.writableStore(WritableAirdropStore.class)).thenReturn(airdropStore);
        final var response = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INVALID_TRANSACTION_BODY, response.getStatus());
    }

    @Test
    void cancelAirdropWithInvalidTokenIdThrowsException() {
        transactionBody = TransactionBody.newBuilder()
                .tokenCancelAirdrop(tokenCancelAirdrop(true))
                .build();
        when(handleContext.body()).thenReturn(transactionBody);
        when(handleContext.payer()).thenReturn(senderId);
        airdropStore = newWritableStoreWithAirdrops(getFungibleAirdrop());
        readableTokenStore = newReadableStoreWithTokens();
        when(storeFactory.readableStore(ReadableTokenStore.class)).thenReturn(readableTokenStore);
        when(storeFactory.writableStore(WritableAirdropStore.class)).thenReturn(airdropStore);
        final var response = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INVALID_TOKEN_ID, response.getStatus());
        assertTrue(airdropStore.exists(getFungibleAirdrop()));
    }

    @Test
    void cancelAirdropWithPayerDifferentFromSenderThrowsException() {
        transactionBody = TransactionBody.newBuilder()
                .tokenCancelAirdrop(tokenCancelAirdrop(true))
                .build();
        when(handleContext.body()).thenReturn(transactionBody);
        when(handleContext.payer()).thenReturn(invalidAccountId);
        airdropStore = newWritableStoreWithAirdrops(getFungibleAirdrop());
        when(storeFactory.readableStore(ReadableTokenStore.class)).thenReturn(readableTokenStore);
        when(storeFactory.writableStore(WritableAirdropStore.class)).thenReturn(airdropStore);
        final var response = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INVALID_ACCOUNT_ID, response.getStatus());
        assertTrue(airdropStore.exists(getFungibleAirdrop()));
    }

    @Test
    void cancelAirdropWithInvalidReceiverAccountIdThrowsException() {
        transactionBody = TransactionBody.newBuilder()
                .tokenCancelAirdrop(TokenCancelAirdropTransactionBody.newBuilder()
                        .pendingAirdrops(PendingAirdropId.newBuilder()
                                .senderId(senderId)
                                .receiverId(receiverId)
                                .fungibleTokenType(fungibleTokenId)
                                .build())
                        .build())
                .build();
        when(handleContext.body()).thenReturn(transactionBody);
        when(handleContext.payer()).thenReturn(invalidAccountId);
        airdropStore = newWritableStoreWithAirdrops(getFungibleAirdrop());
        when(storeFactory.readableStore(ReadableTokenStore.class)).thenReturn(readableTokenStore);
        when(storeFactory.writableStore(WritableAirdropStore.class)).thenReturn(airdropStore);
        final var response = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INVALID_ACCOUNT_ID, response.getStatus());
        assertTrue(airdropStore.exists(getFungibleAirdrop()));
    }

    @Test
    void cancelAirdropWithInvalidNftIdThrowsException() {
        transactionBody = TransactionBody.newBuilder()
                .tokenCancelAirdrop(tokenCancelAirdrop(false))
                .build();
        when(handleContext.body()).thenReturn(transactionBody);
        when(handleContext.payer()).thenReturn(senderId);
        airdropStore = newWritableStoreWithAirdrops(getNftAirdrop());
        readableTokenStore = newReadableStoreWithTokens();
        readableNftStore = newReadableStoreWithNfts();
        when(storeFactory.readableStore(ReadableNftStore.class)).thenReturn(readableNftStore);
        when(storeFactory.readableStore(ReadableTokenStore.class)).thenReturn(readableTokenStore);
        when(storeFactory.writableStore(WritableAirdropStore.class)).thenReturn(airdropStore);
        final var response = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(INVALID_NFT_ID, response.getStatus());
        assertTrue(airdropStore.exists(getNftAirdrop()));
    }

    @Test
    void cancelAirdropHappyPath() {
        transactionBody = TransactionBody.newBuilder()
                .tokenCancelAirdrop(tokenCancelAirdrops())
                .build();
        when(handleContext.body()).thenReturn(transactionBody);
        when(handleContext.payer()).thenReturn(senderId);
        when(handleContext.expiryValidator()).thenReturn(expiryValidator);
        when(expiryValidator.expirationStatus(any(), anyBoolean(), anyLong())).thenReturn(OK);
        airdropStore = newWritableStoreWithAirdrops(getFungibleAirdrop(), getNftAirdrop());
        readableTokenStore = newReadableStoreWithTokens(
                Token.newBuilder().tokenId(fungibleTokenId).build());
        readableNftStore =
                newReadableStoreWithNfts(Nft.newBuilder().nftId(nftId).build());
        readableAccountStore = newReadableStoreWithAccounts(
                Account.newBuilder().accountId(senderId).build(),
                Account.newBuilder().accountId(receiverId).build());
        when(storeFactory.readableStore(ReadableNftStore.class)).thenReturn(readableNftStore);
        when(storeFactory.readableStore(ReadableTokenStore.class)).thenReturn(readableTokenStore);
        when(storeFactory.readableStore(ReadableAccountStore.class)).thenReturn(readableAccountStore);
        when(storeFactory.writableStore(WritableAirdropStore.class)).thenReturn(airdropStore);
        assertDoesNotThrow(() -> subject.handle(handleContext));
        assertFalse(airdropStore.exists(getFungibleAirdrop()));
        assertFalse(airdropStore.exists(getNftAirdrop()));
    }

    private TokenCancelAirdropTransactionBody tokenCancelAirdrop(final boolean isFungible) {
        return TokenCancelAirdropTransactionBody.newBuilder()
                .pendingAirdrops(isFungible ? getFungibleAirdrop() : getNftAirdrop())
                .build();
    }

    private TokenCancelAirdropTransactionBody tokenCancelAirdrops() {
        return TokenCancelAirdropTransactionBody.newBuilder()
                .pendingAirdrops(getFungibleAirdrop(), getNftAirdrop())
                .build();
    }

    private PendingAirdropId getFungibleAirdrop() {
        return PendingAirdropId.newBuilder()
                .senderId(senderId)
                .receiverId(receiverId)
                .fungibleTokenType(fungibleTokenId)
                .build();
    }

    private PendingAirdropId getNftAirdrop() {
        return PendingAirdropId.newBuilder()
                .senderId(senderId)
                .receiverId(receiverId)
                .nonFungibleToken(nftId)
                .build();
    }
}
