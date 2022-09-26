/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.token;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.token.process.Dissociation;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DissociateLogicTest {
    private final AccountID targetAccount = IdUtils.asAccount("1.2.3");
    private final TokenID firstTargetToken = IdUtils.asToken("2.3.4");
    private final Id accountId = new Id(1, 2, 3);
    private final Id tokenId = new Id(2, 3, 4);

    @Mock private SignedTxnAccessor accessor;
    @Mock private TransactionContext txnCtx;
    @Mock private AccountStore accountStore;
    @Mock private Account account;
    @Mock private DissociationFactory relsFactory;
    @Mock private TypedTokenStore tokenStore;
    @Mock private Dissociation dissociation;
    @Mock private TokenRelationship tokenRelationship;
    @Mock private OptionValidator validator;

    private DissociateLogic subject;

    @BeforeEach
    void setup() {
        subject = new DissociateLogic(validator, tokenStore, accountStore, relsFactory);
    }

    @Test
    @SuppressWarnings("unchecked")
    void performsExpectedLogic() {
        given(accessor.getTxn()).willReturn(validDissociateTxn());
        given(txnCtx.accessor()).willReturn(accessor);
        given(accountStore.loadAccount(accountId)).willReturn(account);
        // and:
        given(relsFactory.loadFrom(tokenStore, account, tokenId)).willReturn(dissociation);
        willAnswer(
                        invocationOnMock -> {
                            ((List<TokenRelationship>) invocationOnMock.getArgument(0))
                                    .add(tokenRelationship);
                            return null;
                        })
                .given(dissociation)
                .addUpdatedModelRelsTo(anyList());

        // when:
        subject.dissociate(
                accountId, txnCtx.accessor().getTxn().getTokenDissociate().getTokensList());

        // then:
        verify(account).dissociateUsing(List.of(dissociation), validator);
        // and:
        verify(accountStore).commitAccount(account);
        verify(tokenStore).commitTokenRelationships(List.of(tokenRelationship));
    }

    private TransactionBody validDissociateTxn() {
        return TransactionBody.newBuilder().setTokenDissociate(validOp()).build();
    }

    private TokenDissociateTransactionBody validOp() {
        return TokenDissociateTransactionBody.newBuilder()
                .setAccount(targetAccount)
                .addTokens(firstTargetToken)
                .build();
    }
}
