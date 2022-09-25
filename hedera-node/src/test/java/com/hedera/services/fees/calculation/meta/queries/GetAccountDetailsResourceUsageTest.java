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
package com.hedera.services.fees.calculation.meta.queries;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;
import static com.hederahashgraph.api.proto.java.ResponseType.COST_ANSWER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.calculation.crypto.queries.GetAccountDetailsResourceUsage;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.crypto.ExtantCryptoContext;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.GetAccountDetailsQuery;
import com.hederahashgraph.api.proto.java.GetAccountDetailsResponse;
import com.hederahashgraph.api.proto.java.GrantedCryptoAllowance;
import com.hederahashgraph.api.proto.java.GrantedNftAllowance;
import com.hederahashgraph.api.proto.java.GrantedTokenAllowance;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.QueryHeader;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRelationship;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GetAccountDetailsResourceUsageTest {
    private static final Key aKey =
            Key.newBuilder().setEd25519(ByteString.copyFrom("NONSENSE".getBytes())).build();
    private static final ByteString ledgerId = ByteString.copyFromUtf8("0xff");
    private static final String a = "0.0.1234";
    private static final long expiry = 1_234_567L;
    private static final AccountID proxy = IdUtils.asAccount("0.0.75231");
    private static final TokenID aToken = asToken("0.0.1001");
    private static final TokenID bToken = asToken("0.0.1002");
    private static final TokenID cToken = asToken("0.0.1003");
    private static final String memo = "Hi there!";
    private static final int maxAutomaticAssociations = 123;
    private static final int maxTokensPerAccountInfo = 10;
    private static final AccountID queryTarget = IdUtils.asAccount(a);

    private GrantedCryptoAllowance cryptoAllowances =
            GrantedCryptoAllowance.newBuilder().setSpender(proxy).setAmount(10L).build();
    private GrantedTokenAllowance tokenAllowances =
            GrantedTokenAllowance.newBuilder()
                    .setSpender(proxy)
                    .setAmount(10L)
                    .setTokenId(IdUtils.asToken("0.0.1000"))
                    .build();
    private GrantedNftAllowance nftAllowances =
            GrantedNftAllowance.newBuilder()
                    .setSpender(proxy)
                    .setTokenId(IdUtils.asToken("0.0.1000"))
                    .build();

    @Mock private FeeData expected;
    @Mock private CryptoOpsUsage cryptoOpsUsage;
    @Mock private StateView view;
    @Mock private AliasManager aliasManager;
    @Mock private GlobalDynamicProperties dynamicProperties;

    private GetAccountDetailsResourceUsage subject;

    @BeforeEach
    void setup() {
        subject =
                new GetAccountDetailsResourceUsage(cryptoOpsUsage, aliasManager, dynamicProperties);
    }

    @Test
    void usesEstimator() {
        given(dynamicProperties.maxTokensRelsPerInfoQuery()).willReturn(maxTokensPerAccountInfo);
        final var captor = ArgumentCaptor.forClass(ExtantCryptoContext.class);
        final var details =
                GetAccountDetailsResponse.AccountDetails.newBuilder()
                        .setLedgerId(ledgerId)
                        .setExpirationTime(Timestamp.newBuilder().setSeconds(expiry))
                        .setMemo(memo)
                        .setProxyAccountId(proxy)
                        .setKey(aKey)
                        .addTokenRelationships(0, TokenRelationship.newBuilder().setTokenId(aToken))
                        .addTokenRelationships(1, TokenRelationship.newBuilder().setTokenId(bToken))
                        .addTokenRelationships(2, TokenRelationship.newBuilder().setTokenId(cToken))
                        .setMaxAutomaticTokenAssociations(maxAutomaticAssociations)
                        .addAllGrantedCryptoAllowances(List.of(cryptoAllowances))
                        .addAllGrantedTokenAllowances(List.of(tokenAllowances))
                        .addAllGrantedNftAllowances(List.of(nftAllowances))
                        .build();
        final var query = accountDetailsQuery(a, ANSWER_ONLY);
        given(view.accountDetails(queryTarget, aliasManager, maxTokensPerAccountInfo))
                .willReturn(Optional.of(details));
        given(cryptoOpsUsage.accountDetailsUsage(any(), any())).willReturn(expected);

        final var usage = subject.usageGiven(query, view);

        assertEquals(expected, usage);
        verify(cryptoOpsUsage).accountDetailsUsage(argThat(query::equals), captor.capture());

        final var ctx = captor.getValue();
        assertEquals(aKey, ctx.currentKey());
        assertEquals(expiry, ctx.currentExpiry());
        assertEquals(memo, ctx.currentMemo());
        assertEquals(3, ctx.currentNumTokenRels());
        assertEquals(1, ctx.currentCryptoAllowances().size());
        assertEquals(1, ctx.currentNftAllowances().size());
        assertEquals(1, ctx.currentTokenAllowances().size());
        assertTrue(ctx.currentlyHasProxy());
    }

    @Test
    void returnsDefaultIfNoSuchAccount() {
        given(dynamicProperties.maxTokensRelsPerInfoQuery()).willReturn(maxTokensPerAccountInfo);
        given(view.accountDetails(queryTarget, aliasManager, maxTokensPerAccountInfo))
                .willReturn(Optional.empty());

        final var usage = subject.usageGiven(accountDetailsQuery(a, ANSWER_ONLY), view);

        assertSame(FeeData.getDefaultInstance(), usage);
    }

    @Test
    void recognizesApplicableQuery() {
        final var accountDetailsQuery = accountDetailsQuery(a, COST_ANSWER);
        final var nonAccountDetailsQuery = Query.getDefaultInstance();

        assertTrue(subject.applicableTo(accountDetailsQuery));
        assertFalse(subject.applicableTo(nonAccountDetailsQuery));
    }

    private static final Query accountDetailsQuery(final String target, final ResponseType type) {
        final var id = asAccount(target);
        final var op =
                GetAccountDetailsQuery.newBuilder()
                        .setAccountId(id)
                        .setHeader(QueryHeader.newBuilder().setResponseType(type));
        return Query.newBuilder().setAccountDetails(op).build();
    }
}
