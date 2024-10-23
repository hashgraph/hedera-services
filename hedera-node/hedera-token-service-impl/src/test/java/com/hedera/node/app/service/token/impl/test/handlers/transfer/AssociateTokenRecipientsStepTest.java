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

package com.hedera.node.app.service.token.impl.test.handlers.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.impl.handlers.transfer.AssociateTokenRecipientsStep;
import com.hedera.node.app.service.token.impl.handlers.transfer.TransferContextImpl;
import com.hedera.node.app.service.token.records.CryptoTransferStreamBuilder;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AssociateTokenRecipientsStep}.
 */
@ExtendWith(MockitoExtension.class)
public class AssociateTokenRecipientsStepTest extends StepsBase {
    @Mock(strictness = Mock.Strictness.LENIENT)
    private HandleContext handleContext;

    @Mock
    private ExpiryValidator expiryValidator;

    @Mock
    private HandleContext.SavepointStack stack;

    @Mock
    private CryptoTransferStreamBuilder builder;

    private AssociateTokenRecipientsStep subject;
    private CryptoTransferTransactionBody txn;
    private TransferContextImpl transferContext;

    @BeforeEach
    public void setUp() {
        super.setUp();
        givenValidTxn();
        refreshWritableStores();
        givenStoresAndConfig(handleContext);
        subject = new AssociateTokenRecipientsStep(txn);
        transferContext = new TransferContextImpl(handleContext);
        writableTokenStore.put(givenValidFungibleToken(ownerId, false, false, false, false, false));
        writableTokenStore.put(givenValidNonFungibleToken(false));
    }

    @Test
    void associatesTokenRecepients() {
        assertThat(writableTokenRelStore.get(ownerId, fungibleTokenId)).isNotNull();
        assertThat(writableTokenRelStore.get(ownerId, nonFungibleTokenId)).isNotNull();
        assertThat(writableTokenRelStore.get(spenderId, fungibleTokenId)).isNull();
        assertThat(writableTokenRelStore.get(spenderId, nonFungibleTokenId)).isNull();

        final var modifiedConfiguration = HederaTestConfigBuilder.create()
                .withValue("entities.unlimitedAutoAssociationsEnabled", false)
                .getOrCreateConfig();
        given(handleContext.configuration()).willReturn(modifiedConfiguration);
        given(handleContext.savepointStack()).willReturn(stack);
        given(stack.getBaseBuilder(any())).willReturn(builder);

        subject.doIn(transferContext);

        assertThat(writableTokenRelStore.get(ownerId, fungibleTokenId)).isNotNull();
        assertThat(writableTokenRelStore.get(ownerId, nonFungibleTokenId)).isNotNull();
        assertThat(writableTokenRelStore.get(spenderId, fungibleTokenId)).isNotNull();
        assertThat(writableTokenRelStore.get(spenderId, nonFungibleTokenId)).isNotNull();
    }

    void givenValidTxn() {
        txn = CryptoTransferTransactionBody.newBuilder()
                .transfers(TransferList.newBuilder()
                        .accountAmounts(adjustFrom(ownerId, -1_000))
                        .accountAmounts(adjustFrom(spenderId, +1_000))
                        .build())
                .tokenTransfers(
                        TokenTransferList.newBuilder()
                                .token(fungibleTokenId)
                                .transfers(List.of(adjustFrom(ownerId, -1_000), adjustFrom(spenderId, +1_000)))
                                .build(),
                        TokenTransferList.newBuilder()
                                .token(nonFungibleTokenId)
                                .nftTransfers(nftTransferWith(ownerId, spenderId, 1))
                                .build())
                .build();
        given(handleContext.configuration()).willReturn(configuration);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        lenient()
                .when(expiryValidator.expirationStatus(any(), anyBoolean(), anyLong()))
                .thenReturn(ResponseCodeEnum.OK);
        given(handleContext.savepointStack()).willReturn(stack);
    }

    private AccountAmount adjustFrom(AccountID account, long amount) {
        return AccountAmount.newBuilder().accountID(account).amount(amount).build();
    }

    private NftTransfer nftTransferWith(AccountID from, AccountID to, long serialNo) {
        return NftTransfer.newBuilder()
                .senderAccountID(from)
                .receiverAccountID(to)
                .serialNumber(serialNo)
                .build();
    }
}
