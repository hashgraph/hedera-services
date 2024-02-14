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
import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.TokenUpdateNftTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.mono.config.HederaNumbers;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.TokenUpdateNftsHandler;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.service.token.impl.validators.TokenAttributesValidator;
import com.hedera.node.app.service.token.impl.validators.TokenUpdateNftValidator;
import com.hedera.node.app.service.token.records.TokenUpdateNftRecordBuilder;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.workflows.handle.validation.StandardizedAttributeValidator;
import com.hedera.node.app.workflows.handle.validation.StandardizedExpiryValidator;
import com.hedera.node.config.ConfigProvider;
import com.hedera.pbj.runtime.io.buffer.Bytes;
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
        txn = new TokenUpdateNftBuilder().build();
        given(handleContext.body()).willReturn(txn);

        final var token = readableTokenStore.get(nonFungibleTokenId);
        assertThat(token.metadata()).isEqualTo(nonFungibleToken.metadata());
        assertThat(token.tokenType()).isEqualTo(NON_FUNGIBLE_UNIQUE);

        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));

        final var modifiedToken = writableTokenStore.get(fungibleTokenId);
        //        assertThat(modifiedToken.memo()).isEqualTo("test token1");
        assertThat(token.tokenType()).isEqualTo(NON_FUNGIBLE_UNIQUE);
    }

    private class TokenUpdateNftBuilder {
        private String metadata = "test metadata";
        TokenID tokenId = fungibleTokenId;
        private AccountID payer = payerId;

        private TokenUpdateNftBuilder() {}

        public TransactionBody build() {
            final var transactionID =
                    TransactionID.newBuilder().accountID(payer).transactionValidStart(consensusTimestamp);
            final var createTxnBody =
                    TokenUpdateNftTransactionBody.newBuilder().token(tokenId).metadata(Bytes.wrap(metadata));
            return TransactionBody.newBuilder()
                    .transactionID(transactionID)
                    .tokenUpdateNft(createTxnBody.build())
                    .build();
        }

        public TokenUpdateNftBuilder withMetadata(final String s) {
            this.metadata = s;
            return this;
        }
    }
}
