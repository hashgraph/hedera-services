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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.service.token.impl.test.handlers.util.TestStoreFactory.newReadableStoreWithTokens;
import static com.hedera.node.app.service.token.impl.test.handlers.util.TestStoreFactory.newWritableStoreWithTokenRels;
import static com.hedera.node.app.service.token.impl.test.handlers.util.TestStoreFactory.newWritableStoreWithTokens;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_SUPPLY_KT;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.token.TokenUpdateNftTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.config.HederaNumbers;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler;
import com.hedera.node.app.service.token.impl.handlers.BaseTokenHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUpdateNftsHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.service.token.impl.validators.TokenAttributesValidator;
import com.hedera.node.app.service.token.impl.validators.TokenUpdateNftValidator;
import com.hedera.node.app.service.token.records.TokenUpdateNftRecordBuilder;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.workflows.handle.validation.StandardizedAttributeValidator;
import com.hedera.node.app.workflows.handle.validation.StandardizedExpiryValidator;
import com.hedera.node.config.ConfigProvider;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenUpdateNftsHandlerTest extends CryptoTokenHandlerTestBase {
    @Mock(strictness = LENIENT)
    private HandleContext handleContext;

    @Mock(strictness = LENIENT)
    private ConfigProvider configProvider;

    @Mock(strictness = LENIENT)
    private PropertySource compositeProps;

    @Mock(strictness = LENIENT)
    private HederaNumbers hederaNumbers;

    @Mock(strictness = LENIENT)
    private GlobalDynamicProperties dynamicProperties;

    @Mock(strictness = LENIENT)
    private TokenUpdateNftRecordBuilder recordBuilder;

    private ExpiryValidator expiryValidator;
    private AttributeValidator attributeValidator;
    private TokenUpdateNftsHandler subject;
    private TransactionBody txn;
    private static final AccountID ACCOUNT_1339 = BaseCryptoHandler.asAccount(1339);
    private static final TokenID TOKEN_123 = BaseTokenHandler.asToken(123);

    @BeforeEach
    public void setUp() {
        super.setUp();
        refreshWritableStores();
        final TokenUpdateNftValidator validator = new TokenUpdateNftValidator(new TokenAttributesValidator());
        subject = new TokenUpdateNftsHandler(validator);
        given(handleContext.recordBuilder(any())).willReturn(recordBuilder);
        givenStoresAndConfig(handleContext);
        setUpTxnContext();
    }

    private void setUpTxnContext() {
        given(handleContext.writableStore(WritableAccountStore.class)).willReturn(writableAccountStore);
        given(handleContext.configuration()).willReturn(configuration);
        given(handleContext.consensusNow()).willReturn(consensusInstant);
        given(compositeProps.getLongProperty("entities.maxLifetime")).willReturn(7200000L);

        attributeValidator =
                new StandardizedAttributeValidator(consensusInstant::getEpochSecond, compositeProps, dynamicProperties);
        expiryValidator = new StandardizedExpiryValidator(
                id -> {
                    final var account = writableAccountStore.get(
                            AccountID.newBuilder().accountNum(id.num()).build());
                    validateTrue(account != null, INVALID_AUTORENEW_ACCOUNT);
                },
                attributeValidator,
                consensusInstant::getEpochSecond,
                hederaNumbers,
                configProvider);

        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(handleContext.attributeValidator()).willReturn(attributeValidator);
        given(dynamicProperties.maxMemoUtf8Bytes()).willReturn(50);
        given(dynamicProperties.maxAutoRenewDuration()).willReturn(3000000L);
        given(dynamicProperties.minAutoRenewDuration()).willReturn(10L);
        given(configProvider.getConfiguration()).willReturn(versionedConfig);
    }

    @Test
    void happyPathForNonFungibleTokenUpdate() {
        final List<Long> serialNumbers = new ArrayList<>(Arrays.asList(1L, 2L));
        txn = new TokenUpdateNftBuilder().build(serialNumbers);
        given(handleContext.body()).willReturn(txn);

        final var token = readableTokenStore.get(nonFungibleTokenId);
        assertThat(token.metadata()).isEqualTo(nonFungibleToken.metadata());
        assertThat(token.tokenType()).isEqualTo(NON_FUNGIBLE_UNIQUE);

        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));

        final var modifiedToken = writableTokenStore.get(fungibleTokenId);
        //        assertThat(modifiedToken.memo()).isEqualTo("test token1");
        assertThat(token.tokenType()).isEqualTo(NON_FUNGIBLE_UNIQUE);
    }

    @Test
    void nftSerialNotFound() {
        final List<Long> serialNumbers = new ArrayList<>(Arrays.asList(1L, 2L));
        readableTokenStore =
                newReadableStoreWithTokens(Token.newBuilder().tokenId(TOKEN_123).build());
        writableTokenStore = newWritableStoreWithTokens(Token.newBuilder()
                .tokenId(TOKEN_123)
                .tokenType(TokenType.NON_FUNGIBLE_UNIQUE)
                .treasuryAccountId(ACCOUNT_1339)
                .supplyKey(TOKEN_SUPPLY_KT.asPbjKey())
                .totalSupply(10)
                .build());
        writableTokenRelStore = newWritableStoreWithTokenRels(TokenRelation.newBuilder()
                .accountId(ACCOUNT_1339)
                .tokenId(TOKEN_123)
                .balance(10)
                .build());
        writableNftStore = new WritableNftStore(new MapWritableStates(
                Map.of("NFTS", MapWritableKVState.builder("NFTS").build())));

        final var txn = new TokenUpdateNftBuilder()
                .newNftUpdateTxn(TOKEN_123, Bytes.wrap("test metadata"), serialNumbers.get(1));
        final var context = mockContext(txn);

        Assertions.assertThatThrownBy(() -> subject.handle(context))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_NFT_ID));
    }

    private HandleContext mockContext(TransactionBody txn) {
        final var context = mock(HandleContext.class);

        given(context.body()).willReturn(txn);
        given(context.readableStore(ReadableTokenStore.class)).willReturn(readableTokenStore);
        given(context.writableStore(WritableTokenStore.class)).willReturn(writableTokenStore);
        given(context.writableStore(WritableNftStore.class)).willReturn(writableNftStore);
        given(context.configuration()).willReturn(configuration);

        return context;
    }

    private class TokenUpdateNftBuilder {
        private String metadata = "test metadata";
        TokenID tokenId = nonFungibleTokenId;
        private final AccountID payer = payerId;

        private TokenUpdateNftBuilder() {}

        private TransactionBody build(List<Long> serialNumbers) {
            final var transactionID =
                    TransactionID.newBuilder().accountID(payer).transactionValidStart(consensusTimestamp);
            final var createTxnBody = TokenUpdateNftTransactionBody.newBuilder()
                    .token(tokenId)
                    .metadata(Bytes.wrap(metadata))
                    .serialNumbers(serialNumbers);
            return TransactionBody.newBuilder()
                    .transactionID(transactionID)
                    .tokenUpdateNft(createTxnBody.build())
                    .build();
        }

        private TransactionBody newNftUpdateTxn(TokenID token, Bytes metadata, Long... nftSerialNums) {
            //            final var transactionID =
            //                    TransactionID.newBuilder().accountID(payer).transactionValidStart(consensusTimestamp);
            final var transactionID2 =
                    TransactionID.newBuilder().accountID(ACCOUNT_1339).build();
            TokenUpdateNftTransactionBody.Builder nftUpdateTxnBodyBuilder = TokenUpdateNftTransactionBody.newBuilder();
            if (token != null) nftUpdateTxnBodyBuilder.token(token);
            nftUpdateTxnBodyBuilder.metadata(metadata);
            nftUpdateTxnBodyBuilder.serialNumbers(nftSerialNums);
            return TransactionBody.newBuilder()
                    .transactionID(transactionID2)
                    .tokenUpdateNft(nftUpdateTxnBodyBuilder)
                    .build();
        }

        public TokenUpdateNftBuilder withMetadata(final String s) {
            this.metadata = s;
            return this;
        }
    }
}
