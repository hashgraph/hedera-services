/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.crypto;

import static com.hedera.services.store.models.Id.fromGrpcAccount;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoDeleteAllowanceTransitionLogicTest {
    @Mock private TransactionContext txnCtx;
    @Mock private AccountStore accountStore;
    @Mock private DeleteAllowanceChecks deleteAllowanceChecks;
    @Mock private StateView view;
    @Mock private DeleteAllowanceLogic deleteAllowanceLogic;
    @Mock private PlatformTxnAccessor accessor;

    private TransactionBody cryptoDeleteAllowanceTxn;
    private CryptoDeleteAllowanceTransactionBody op;

    CryptoDeleteAllowanceTransitionLogic subject;

    @BeforeEach
    void setup() {
        subject =
                new CryptoDeleteAllowanceTransitionLogic(
                        txnCtx, accountStore, deleteAllowanceChecks, view, deleteAllowanceLogic);
    }

    @Test
    void callsDeleteAllowanceLogic() {
        givenValidTxnCtx();

        given(accessor.getTxn()).willReturn(cryptoDeleteAllowanceTxn);
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.activePayer()).willReturn(ourAccount());

        subject.doStateTransition();

        verify(deleteAllowanceLogic)
                .deleteAllowance(
                        op.getNftAllowancesList(), fromGrpcAccount(payerId).asGrpcAccount());
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();

        assertTrue(subject.applicability().test(cryptoDeleteAllowanceTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void semanticCheckDelegatesWorks() {
        givenValidTxnCtx();
        given(
                        deleteAllowanceChecks.deleteAllowancesValidation(
                                op.getNftAllowancesList(), payerAccount, view))
                .willReturn(OK);

        given(accountStore.loadAccount(Id.fromGrpcAccount(payerId))).willReturn(payerAccount);

        assertEquals(OK, subject.semanticCheck().apply(cryptoDeleteAllowanceTxn));
    }

    private void givenValidTxnCtx() {
        token1Model.setMaxSupply(5000L);
        token1Model.setType(TokenType.FUNGIBLE_COMMON);
        token2Model.setMaxSupply(5000L);
        token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

        nftAllowances.add(nftAllowance1);

        cryptoDeleteAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoDeleteAllowance(
                                CryptoDeleteAllowanceTransactionBody.newBuilder()
                                        .addAllNftAllowances(nftAllowances))
                        .build();
        op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();

        ownerAccount.setApproveForAllNfts(new TreeSet<>());
    }

    private TransactionID ourTxnId() {
        return TransactionID.newBuilder()
                .setAccountID(payerId)
                .setTransactionValidStart(
                        Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
                .build();
    }

    private AccountID ourAccount() {
        return payerId;
    }

    private static final TokenID token1 = asToken("0.0.100");
    private static final TokenID token2 = asToken("0.0.200");
    private static final AccountID ownerId = asAccount("0.0.5000");
    private static final AccountID payerId = asAccount("0.0.5001");
    private static final Instant consensusTime = Instant.now();
    private final Token token1Model = new Token(Id.fromGrpcToken(token1));
    private final Token token2Model = new Token(Id.fromGrpcToken(token2));
    private final NftRemoveAllowance nftAllowance1 =
            NftRemoveAllowance.newBuilder()
                    .setOwner(ownerId)
                    .setTokenId(token2)
                    .addAllSerialNumbers(List.of(12L, 10L))
                    .build();
    private List<NftRemoveAllowance> nftAllowances = new ArrayList<>();
    private final Account ownerAccount = new Account(Id.fromGrpcAccount(ownerId));
    private final Account payerAccount = new Account(Id.fromGrpcAccount(payerId));
}
