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

import com.google.protobuf.BoolValue;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.crypto.validators.ApproveAllowanceChecks;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoApproveAllowanceTransitionLogicTest {
    @Mock private TransactionContext txnCtx;
    @Mock private AccountStore accountStore;
    @Mock private ApproveAllowanceChecks allowanceChecks;
    @Mock private StateView view;
    @Mock private ApproveAllowanceLogic approveAllowanceLogic;
    @Mock private PlatformTxnAccessor accessor;

    private TransactionBody cryptoApproveAllowanceTxn;
    private CryptoApproveAllowanceTransactionBody op;

    CryptoApproveAllowanceTransitionLogic subject;

    @BeforeEach
    void setup() {
        subject =
                new CryptoApproveAllowanceTransitionLogic(
                        txnCtx, accountStore, allowanceChecks, approveAllowanceLogic, view);
        nft1.setOwner(fromGrpcAccount(ownerId));
        nft2.setOwner(fromGrpcAccount(ownerId));
    }

    @Test
    void callsApproveAllowanceLogic() {
        givenValidTxnCtx();

        given(accessor.getTxn()).willReturn(cryptoApproveAllowanceTxn);
        given(txnCtx.activePayer()).willReturn(ourAccount());
        given(txnCtx.accessor()).willReturn(accessor);

        subject.doStateTransition();

        verify(approveAllowanceLogic)
                .approveAllowance(
                        op.getCryptoAllowancesList(),
                        op.getTokenAllowancesList(),
                        op.getNftAllowancesList(),
                        fromGrpcAccount(payerId).asGrpcAccount());
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();

        assertTrue(subject.applicability().test(cryptoApproveAllowanceTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void semanticCheckDelegatesWorks() {
        givenValidTxnCtx();
        given(
                        allowanceChecks.allowancesValidation(
                                op.getCryptoAllowancesList(),
                                op.getTokenAllowancesList(),
                                op.getNftAllowancesList(),
                                payerAcccount,
                                view))
                .willReturn(OK);
        given(accountStore.loadAccount(payerAcccount.getId())).willReturn(payerAcccount);
        assertEquals(OK, subject.semanticCheck().apply(cryptoApproveAllowanceTxn));
    }

    private void givenValidTxnCtx() {
        token1Model.setMaxSupply(5000L);
        token1Model.setType(TokenType.FUNGIBLE_COMMON);
        token2Model.setMaxSupply(5000L);
        token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

        cryptoAllowances.add(cryptoAllowance1);
        tokenAllowances.add(tokenAllowance1);
        nftAllowances.add(nftAllowance1);

        cryptoApproveAllowanceTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoApproveAllowance(
                                CryptoApproveAllowanceTransactionBody.newBuilder()
                                        .addAllCryptoAllowances(cryptoAllowances)
                                        .addAllTokenAllowances(tokenAllowances)
                                        .addAllNftAllowances(nftAllowances))
                        .build();
        op = cryptoApproveAllowanceTxn.getCryptoApproveAllowance();

        ownerAccount.setApproveForAllNfts(new TreeSet<>());
        ownerAccount.setCryptoAllowances(new HashMap<>());
        ownerAccount.setFungibleTokenAllowances(new HashMap<>());
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

    private static final long serial1 = 1L;
    private static final long serial2 = 10L;
    private static final AccountID spender1 = asAccount("0.0.123");
    private static final TokenID token1 = asToken("0.0.100");
    private static final TokenID token2 = asToken("0.0.200");
    private static final AccountID payerId = asAccount("0.0.5000");
    private static final AccountID ownerId = asAccount("0.0.6000");
    private static final Id tokenId1 = Id.fromGrpcToken(token1);
    private static final Id tokenId2 = Id.fromGrpcToken(token2);
    private static final Instant consensusTime = Instant.now();
    private final Token token1Model = new Token(Id.fromGrpcToken(token1));
    private final Token token2Model = new Token(Id.fromGrpcToken(token2));
    private final CryptoAllowance cryptoAllowance1 =
            CryptoAllowance.newBuilder()
                    .setSpender(spender1)
                    .setOwner(ownerId)
                    .setAmount(10L)
                    .build();
    private final TokenAllowance tokenAllowance1 =
            TokenAllowance.newBuilder()
                    .setSpender(spender1)
                    .setAmount(10L)
                    .setTokenId(token1)
                    .setOwner(ownerId)
                    .build();
    private final NftAllowance nftAllowance1 =
            NftAllowance.newBuilder()
                    .setSpender(spender1)
                    .setOwner(ownerId)
                    .setTokenId(token2)
                    .setApprovedForAll(BoolValue.of(true))
                    .addAllSerialNumbers(List.of(serial1, serial2))
                    .build();
    private List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
    private List<TokenAllowance> tokenAllowances = new ArrayList<>();
    private List<NftAllowance> nftAllowances = new ArrayList<>();
    private final Account payerAcccount = new Account(fromGrpcAccount(payerId));
    private final Account ownerAccount = new Account(fromGrpcAccount(ownerId));
    private final UniqueToken nft1 = new UniqueToken(tokenId1, serial1);
    private final UniqueToken nft2 = new UniqueToken(tokenId2, serial2);
}
