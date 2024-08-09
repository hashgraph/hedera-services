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
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_PENDING_AIRDROP_ID_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler.asToken;
import static com.hedera.node.app.service.token.impl.test.handlers.util.TestStoreFactory.newReadableStoreWithNfts;
import static com.hedera.node.app.service.token.impl.test.handlers.util.TestStoreFactory.newReadableStoreWithTokens;
import static com.hedera.node.app.service.token.impl.test.handlers.util.TestStoreFactory.newWritableStoreWithAccounts;
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
import com.hedera.node.app.service.token.ReadableNftStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableAirdropStore;
import com.hedera.node.app.service.token.impl.handlers.TokenCancelAirdropHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.TokenHandlerTestBase;
import com.hedera.node.app.spi.store.StoreFactory;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
class TokenCancelAirdropHandlerTest extends TokenHandlerTestBase {

    private TokenCancelAirdropHandler subject;

    private TransactionBody transactionBody;

    @Mock
    private HandleContext handleContext;

    @Mock
    private StoreFactory storeFactory;

    @Mock
    private ExpiryValidator expiryValidator;

    private WritableAirdropStore airdropStore;
    private WritableAccountStore writableAccountStore;
    private ReadableNftStore readableNftStore;
    protected Configuration testConfig;
    private static final AccountID senderId = asAccount(1001);
    private static final AccountID receiverId = asAccount(1010);
    private static final AccountID invalidAccountId = asAccount(1002);
    private static final TokenID fungibleTokenId = asToken(333);
    private static final NftID nftId =
            NftID.newBuilder().tokenId(fungibleTokenId).build();

    @BeforeEach
    public void setup() {
        subject = new TokenCancelAirdropHandler();
        testConfig = HederaTestConfigBuilder.create()
                .withValue("tokens.airdrops.cancel.enabled", true)
                .getOrCreateConfig();
        airdropStore = newWritableStoreWithAirdrops();

        when(handleContext.configuration()).thenReturn(testConfig);
        when(handleContext.storeFactory()).thenReturn(storeFactory);
        writableAccountStore = newWritableStoreWithAccounts(
                Account.newBuilder()
                        .accountId(senderId)
                        .headPendingAirdropId(PendingAirdropId.DEFAULT)
                        .build(),
                Account.newBuilder().accountId(receiverId).build());
        when(storeFactory.writableStore(WritableAccountStore.class)).thenReturn(writableAccountStore);
        when(handleContext.payer()).thenReturn(senderId);
        when(handleContext.expiryValidator()).thenReturn(expiryValidator);
    }

    @Test
    void cancelAirdropHappyPath() {
        // Arrange
        when(expiryValidator.expirationStatus(any(), anyBoolean(), anyLong())).thenReturn(OK);

        transactionBody = TransactionBody.newBuilder()
                .tokenCancelAirdrop(tokenCancelAirdrops())
                .build();
        when(handleContext.body()).thenReturn(transactionBody);

        airdropStore = newWritableStoreWithAirdrops(getFungibleAirdrop(), getNftAirdrop());
        when(storeFactory.writableStore(WritableAirdropStore.class)).thenReturn(airdropStore);

        readableTokenStore = newReadableStoreWithTokens(
                Token.newBuilder().tokenId(fungibleTokenId).build());
        when(storeFactory.readableStore(ReadableTokenStore.class)).thenReturn(readableTokenStore);

        readableNftStore =
                newReadableStoreWithNfts(Nft.newBuilder().nftId(nftId).build());
        when(storeFactory.readableStore(ReadableNftStore.class)).thenReturn(readableNftStore);

        writableAccountStore = newWritableStoreWithAccounts(
                Account.newBuilder()
                        .headPendingAirdropId(getFungibleAirdrop())
                        .accountId(senderId)
                        .build(),
                Account.newBuilder().accountId(receiverId).build());
        when(storeFactory.writableStore(WritableAccountStore.class)).thenReturn(writableAccountStore);

        // Act
        assertDoesNotThrow(() -> subject.handle(handleContext));

        // Assert
        assertFalse(airdropStore.exists(getFungibleAirdrop()));
        assertFalse(airdropStore.exists(getNftAirdrop()));
    }

    @Test
    void cancelAirdropWithNoPendingAirdropsThrowsException() {
        // Arrange

        // setup common mocks
        when(expiryValidator.expirationStatus(any(), anyBoolean(), anyLong())).thenReturn(OK);
        readableTokenStore = newReadableStoreWithTokens(
                Token.newBuilder().tokenId(fungibleTokenId).build());
        readableNftStore =
                newReadableStoreWithNfts(Nft.newBuilder().nftId(nftId).build());
        when(storeFactory.readableStore(ReadableTokenStore.class)).thenReturn(readableTokenStore);

        // Transaction body with cancel airdrops that doesn't exist in the state
        transactionBody = TransactionBody.newBuilder()
                .tokenCancelAirdrop(tokenCancelAirdrops())
                .build();
        when(handleContext.body()).thenReturn(transactionBody);

        // Mocking the airdrop store to be empty
        when(storeFactory.writableStore(WritableAirdropStore.class)).thenReturn(airdropStore);

        // Act
        final var response = assertThrows(HandleException.class, () -> subject.handle(handleContext));

        // Assert
        assertEquals(INVALID_TRANSACTION_BODY, response.getStatus());
    }

    @Test
    void cancelAirdropWithInvalidTokenIdThrowsException() {
        // Arrange

        // setup common mocks
        readableTokenStore = newReadableStoreWithTokens();
        when(storeFactory.readableStore(ReadableTokenStore.class)).thenReturn(readableTokenStore);
        when(expiryValidator.expirationStatus(any(), anyBoolean(), anyLong())).thenReturn(OK);

        // Transaction body with token id that doesn't exist in the token store
        transactionBody = TransactionBody.newBuilder()
                .tokenCancelAirdrop(tokenCancelAirdrop(true))
                .build();
        when(handleContext.body()).thenReturn(transactionBody);

        // Act
        final var response = assertThrows(HandleException.class, () -> subject.handle(handleContext));

        // Assert
        assertEquals(INVALID_TOKEN_ID, response.getStatus());
    }

    @Test
    void cancelAirdropWithPayerDifferentFromSenderThrowsException() {
        // Arrange
        transactionBody = TransactionBody.newBuilder()
                .tokenCancelAirdrop(tokenCancelAirdrop(true))
                .build();
        when(handleContext.body()).thenReturn(transactionBody);
        when(handleContext.payer()).thenReturn(invalidAccountId);

        // Act
        final var response = assertThrows(HandleException.class, () -> subject.handle(handleContext));

        // Assert
        assertEquals(INVALID_ACCOUNT_ID, response.getStatus());
    }

    @Test
    void cancelAirdropWithInvalidReceiverAccountIdThrowsException() {
        // Arrange

        // setup common mocks
        airdropStore = newWritableStoreWithAirdrops(getFungibleAirdrop());
        when(storeFactory.writableStore(WritableAirdropStore.class)).thenReturn(airdropStore);

        // standard transactionBody
        transactionBody = TransactionBody.newBuilder()
                .tokenCancelAirdrop(tokenCancelAirdrop(true))
                .build();
        when(handleContext.body()).thenReturn(transactionBody);

        // payer with invalid account id
        when(handleContext.payer()).thenReturn(invalidAccountId);

        // Act
        final var response = assertThrows(HandleException.class, () -> subject.handle(handleContext));

        // Assert
        assertEquals(INVALID_ACCOUNT_ID, response.getStatus());
        assertTrue(airdropStore.exists(getFungibleAirdrop()));
    }

    @Test
    void cancelAirdropWithInvalidNftIdThrowsException() {
        // Arrange

        // setup common mocks
        readableTokenStore = newReadableStoreWithTokens();
        when(storeFactory.readableStore(ReadableTokenStore.class)).thenReturn(readableTokenStore);
        when(expiryValidator.expirationStatus(any(), anyBoolean(), anyLong())).thenReturn(OK);

        // Transaction body with nfId that doesn't exist in the nft store
        transactionBody = TransactionBody.newBuilder()
                .tokenCancelAirdrop(tokenCancelAirdrop(false))
                .build();
        when(handleContext.body()).thenReturn(transactionBody);

        // Empty nft store
        readableNftStore = newReadableStoreWithNfts();
        when(storeFactory.readableStore(ReadableNftStore.class)).thenReturn(readableNftStore);

        // Act
        final var response = assertThrows(HandleException.class, () -> subject.handle(handleContext));

        // Assert
        assertEquals(INVALID_NFT_ID, response.getStatus());
    }

    @Nested
    // Changing the strictness to lenient to avoid strictness problems with the setup method of the parent class
    @MockitoSettings(strictness = Strictness.LENIENT)
    class ConfigTests {

        @ParameterizedTest
        @MethodSource("maxAirdropsExceededArguments")
        void maxAirdropsExceeded(PendingAirdropId airdrop) {
            transactionBody = TransactionBody.newBuilder()
                    .tokenCancelAirdrop(tokenCancelRepeatedAirdrop(airdrop, 11))
                    .build();

            when(handleContext.body()).thenReturn(transactionBody);

            // Act
            final var response = assertThrows(HandleException.class, () -> subject.handle(handleContext));

            // Assert
            assertEquals(MAX_PENDING_AIRDROP_ID_EXCEEDED, response.getStatus());
        }

        private static Stream<Arguments> maxAirdropsExceededArguments() {
            return Stream.of(Arguments.of(getNftAirdrop()), Arguments.of(getFungibleAirdrop()));
        }

        @Test
        void cancelAirdropWhenDisabled() {
            // Arrange

            // Common Mocks
            transactionBody = TransactionBody.newBuilder()
                    .tokenCancelAirdrop(tokenCancelAirdrop(true))
                    .build();
            when(handleContext.body()).thenReturn(transactionBody);

            // Disabling the cancel airdrops
            testConfig = HederaTestConfigBuilder.create()
                    .withValue("tokens.airdrops.cancel.enabled", false)
                    .getOrCreateConfig();
            when(handleContext.configuration()).thenReturn(testConfig);

            // Act
            final var response = assertThrows(HandleException.class, () -> subject.handle(handleContext));

            // Assert
            assertEquals(NOT_SUPPORTED, response.getStatus());
        }

        private TokenCancelAirdropTransactionBody tokenCancelRepeatedAirdrop(PendingAirdropId airdrop, int n) {
            return TokenCancelAirdropTransactionBody.newBuilder()
                    .pendingAirdrops(repeatedAirdrops(airdrop, n))
                    .build();
        }

        private PendingAirdropId[] repeatedAirdrops(final PendingAirdropId airDrop, final int count) {
            return Collections.nCopies(count, airDrop).toArray(new PendingAirdropId[0]);
        }
    }

    private static TokenCancelAirdropTransactionBody tokenCancelAirdrop(final boolean isFungible) {
        return TokenCancelAirdropTransactionBody.newBuilder()
                .pendingAirdrops(isFungible ? getFungibleAirdrop() : getNftAirdrop())
                .build();
    }

    private static TokenCancelAirdropTransactionBody tokenCancelAirdrops() {
        return TokenCancelAirdropTransactionBody.newBuilder()
                .pendingAirdrops(getFungibleAirdrop(), getNftAirdrop())
                .build();
    }

    private static PendingAirdropId getFungibleAirdrop() {
        return PendingAirdropId.newBuilder()
                .senderId(senderId)
                .receiverId(receiverId)
                .fungibleTokenType(fungibleTokenId)
                .build();
    }

    private static PendingAirdropId getNftAirdrop() {
        return PendingAirdropId.newBuilder()
                .senderId(senderId)
                .receiverId(receiverId)
                .nonFungibleToken(nftId)
                .build();
    }
}
