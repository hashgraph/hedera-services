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

package com.hedera.node.app.service.contract.impl.test.exec.scope;

import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.node.app.service.contract.impl.exec.processors.ProcessorModule.INITIAL_CONTRACT_NONCE;
import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.MISSING_ENTITY_NUMBER;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CANONICAL_ALIAS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.tuweniToPbjBytes;
import static com.hedera.node.app.service.contract.impl.utils.SynthTxnUtils.synthHollowAccountCreation;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaNativeOperations;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleHederaNativeOperationsTest {
    @Mock
    private HandleContext context;

    @Mock
    private ReadableTokenStore tokenStore;

    @Mock
    private CryptoCreateRecordBuilder cryptoCreateRecordBuilder;

    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private TokenServiceApi tokenServiceApi;

    private HandleHederaNativeOperations subject;

    @BeforeEach
    void setUp() {
        subject = new HandleHederaNativeOperations(context);
    }

    @Test
    void getAccountUsesContextReadableStore() {
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        given(accountStore.getAccountById(NON_SYSTEM_ACCOUNT_ID)).willReturn(Account.DEFAULT);
        assertSame(Account.DEFAULT, subject.getAccount(NON_SYSTEM_ACCOUNT_ID.accountNumOrThrow()));
    }

    @Test
    void resolveAliasReturnsMissingNumIfNotPresent() {
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        assertEquals(MISSING_ENTITY_NUMBER, subject.resolveAlias(tuweniToPbjBytes(EIP_1014_ADDRESS)));
    }

    @Test
    void resolveAliasReturnsNumIfPresent() {
        final var alias = tuweniToPbjBytes(EIP_1014_ADDRESS);
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        given(accountStore.getAccountIDByAlias(alias)).willReturn(NON_SYSTEM_ACCOUNT_ID);
        assertEquals(NON_SYSTEM_ACCOUNT_ID.accountNumOrThrow(), subject.resolveAlias(alias));
    }

    @Test
    void getTokenUsesStore() {
        given(context.readableStore(ReadableTokenStore.class)).willReturn(tokenStore);
        given(tokenStore.get(FUNGIBLE_TOKEN_ID)).willReturn(FUNGIBLE_TOKEN);
        assertSame(FUNGIBLE_TOKEN, subject.getToken(FUNGIBLE_TOKEN_ID.tokenNum()));
    }

    @Test
    void createsHollowAccountByDispatching() {
        final var synthTxn = TransactionBody.newBuilder()
                .cryptoCreateAccount(synthHollowAccountCreation(CANONICAL_ALIAS))
                .build();
        given(context.dispatchChildTransaction(eq(synthTxn), eq(CryptoCreateRecordBuilder.class), any(Predicate.class)))
                .willReturn(cryptoCreateRecordBuilder);
        given(cryptoCreateRecordBuilder.status()).willReturn(OK);

        final var status = subject.createHollowAccount(CANONICAL_ALIAS);

        assertEquals(OK, status);
    }

    @Test
    void createsHollowAccountByDispatchingDoesNotCatchErrors() {
        final var synthTxn = TransactionBody.newBuilder()
                .cryptoCreateAccount(synthHollowAccountCreation(CANONICAL_ALIAS))
                .build();
        given(context.dispatchChildTransaction(eq(synthTxn), eq(CryptoCreateRecordBuilder.class), any(Predicate.class)))
                .willReturn(cryptoCreateRecordBuilder);
        given(cryptoCreateRecordBuilder.status()).willReturn(MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);

        assertThrows(AssertionError.class, () -> subject.createHollowAccount(CANONICAL_ALIAS));
    }

    @Test
    void finalizeHollowAccountAsContractUsesApiAndStore() {
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);
        given(context.readableStore(ReadableAccountStore.class)).willReturn(accountStore);
        given(accountStore.getAccountIDByAlias(CANONICAL_ALIAS)).willReturn(A_NEW_ACCOUNT_ID);

        subject.finalizeHollowAccountAsContract(CANONICAL_ALIAS);

        verify(tokenServiceApi).finalizeHollowAccountAsContract(A_NEW_ACCOUNT_ID, INITIAL_CONTRACT_NONCE);
        verify(context).newEntityNum();
    }

    @Test
    void transferWithReceiverSigCheckUsesApi() {
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);

        final var result = subject.transferWithReceiverSigCheck(
                1L,
                NON_SYSTEM_ACCOUNT_ID.accountNumOrThrow(),
                NON_SYSTEM_CONTRACT_ID.contractNumOrThrow(),
                TestHelpers.MOCK_VERIFICATION_STRATEGY);
        assertEquals(OK, result);
        final var contractAccountId = AccountID.newBuilder()
                .accountNum(NON_SYSTEM_CONTRACT_ID.contractNumOrThrow())
                .build();
        verify(tokenServiceApi).transferFromTo(NON_SYSTEM_ACCOUNT_ID, contractAccountId, 1L);
    }

    @Test
    void trackDeletionIsTodo() {
        assertDoesNotThrow(() -> subject.trackDeletion(1L, 2L));
    }

    @Test
    void settingNonceUsesApi() {
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);

        subject.setNonce(123L, 456L);

        verify(tokenServiceApi).setNonce(AccountID.newBuilder().accountNum(123L).build(), 456L);
    }
}
