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

package com.hedera.node.app.service.token.impl.test.util;

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.TOKEN_RELS_KEY;
import static com.hedera.node.app.service.token.impl.test.handlers.util.AdapterUtils.mockStates;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.impl.ReadableTokenRelationStoreImpl;
import com.hedera.node.app.service.token.impl.util.TokenRelListCalculator;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenRelListCalculatorTest {
    private TokenRelListCalculator subject;

    @BeforeEach
    void setup() {
        subject = new TokenRelListCalculator(localTokenRelsStore());
    }

    // This null token number will represent a null pointer to a token relation's prev or next token number, i.e. if
    // tokenRel.prevToken() == -1L, then there is no previous token in the token rel list. If tokenRel.nextToken() ==
    // -1L, there is no next token in the token rel list.
    private static final long NULL_TOKEN_NUMBER = -1L;
    private static final long TOKEN_NUMBER_1 = 1L;
    private static final long TOKEN_NUMBER_2 = 2L;
    private static final long TOKEN_NUMBER_3 = 3L;
    private static final long TOKEN_NUMBER_4 = 4L;
    private static final long TOKEN_NUMBER_5 = 5L;

    private static final AccountID ACCT_2300_ID =
            AccountID.newBuilder().accountNum(2300L).build();
    private static final Account ACCT_2300 = Account.newBuilder()
            .accountNumber(ACCT_2300_ID.accountNumOrThrow())
            .headTokenNumber(TOKEN_NUMBER_1)
            .numberAssociations(5)
            .build();

    // We construct a test dataset of "local" token relations for the test account 2300, where token relation (account
    // 2300, token 1) <-> (account 2300, token 2) <-> ... (account 2300, token N). In other words, (account 2300, token
    // 1).prevToken() = -1, (account 2300, token 1).nextToken() = 2, (account 2300, token 2).prevToken() = 1, (account
    // 2300, token 2).nextToken() = 3, etc.
    private static final TokenRelation LOCAL_TOKEN_REL_1 = TokenRelation.newBuilder()
            .accountNumber(ACCT_2300_ID.accountNumOrThrow())
            .tokenNumber(TOKEN_NUMBER_1)
            .previousToken(NULL_TOKEN_NUMBER)
            .nextToken(TOKEN_NUMBER_2)
            .build();
    private static final TokenRelation LOCAL_TOKEN_REL_2 = TokenRelation.newBuilder()
            .accountNumber(ACCT_2300_ID.accountNumOrThrow())
            .tokenNumber(TOKEN_NUMBER_2)
            .previousToken(TOKEN_NUMBER_1)
            .nextToken(TOKEN_NUMBER_3)
            .build();
    private static final TokenRelation LOCAL_TOKEN_REL_3 = TokenRelation.newBuilder()
            .accountNumber(ACCT_2300_ID.accountNumOrThrow())
            .tokenNumber(TOKEN_NUMBER_3)
            .previousToken(TOKEN_NUMBER_2)
            .nextToken(TOKEN_NUMBER_4)
            .build();
    private static final TokenRelation LOCAL_TOKEN_REL_4 = TokenRelation.newBuilder()
            .accountNumber(ACCT_2300_ID.accountNumOrThrow())
            .tokenNumber(TOKEN_NUMBER_4)
            .previousToken(TOKEN_NUMBER_3)
            .nextToken(TOKEN_NUMBER_5)
            .build();
    private static final TokenRelation LOCAL_TOKEN_REL_5 = TokenRelation.newBuilder()
            .accountNumber(ACCT_2300_ID.accountNumOrThrow())
            .tokenNumber(TOKEN_NUMBER_5)
            .previousToken(TOKEN_NUMBER_4)
            .nextToken(NULL_TOKEN_NUMBER)
            .build();

    @SuppressWarnings("DataFlowIssue")
    @Test
    void removeTokenRels_nullArgs() {
        final var nonNullTokenRelList = List.of(LOCAL_TOKEN_REL_1);

        Assertions.assertThatThrownBy(() -> subject.removeTokenRels(null, nonNullTokenRelList))
                .isInstanceOf(NullPointerException.class);

        Assertions.assertThatThrownBy(() -> subject.removeTokenRels(mock(Account.class), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void removeTokenRels_emptyTokenRels() {
        final var result = subject.removeTokenRels(ACCT_2300, Collections.emptyList());
        Assertions.assertThat(result.updatedHeadTokenId()).isEqualTo(TOKEN_NUMBER_1);
        Assertions.assertThat(result.updatedTokenRelsStillInChain()).isEmpty();
    }

    @Test
    void removeTokenRels_tokenRelsFromDifferentAccountPresent() {
        final var tokenRelFromDifferentAccount = TokenRelation.newBuilder()
                .accountNumber(2301L)
                .tokenNumber(TOKEN_NUMBER_1)
                .build();
        final var tokenRelsToRemove = List.of(LOCAL_TOKEN_REL_1, LOCAL_TOKEN_REL_2, tokenRelFromDifferentAccount);

        Assertions.assertThatThrownBy(() -> subject.removeTokenRels(ACCT_2300, tokenRelsToRemove))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removeTokenRels_removesAllTokenRels() {
        final var allLocalTokenRels =
                List.of(LOCAL_TOKEN_REL_1, LOCAL_TOKEN_REL_2, LOCAL_TOKEN_REL_3, LOCAL_TOKEN_REL_4, LOCAL_TOKEN_REL_5);
        final var result = subject.removeTokenRels(ACCT_2300, allLocalTokenRels);
        Assertions.assertThat(result.updatedHeadTokenId()).isEqualTo(NULL_TOKEN_NUMBER);
        Assertions.assertThat(result.updatedTokenRelsStillInChain()).isEmpty();
    }

    @Test
    void removeTokenRels_removesHeadTokenRel() {
        final var onlyLocalHeadTokenRel = List.of(LOCAL_TOKEN_REL_1);
        final var result = subject.removeTokenRels(ACCT_2300, onlyLocalHeadTokenRel);
        Assertions.assertThat(result.updatedHeadTokenId()).isEqualTo(TOKEN_NUMBER_2);
        // Note: we don't need to update LOCAL_TOKEN_REL_3, _4, or _5 because their positions in the token rel list are
        // unchanged
        Assertions.assertThat(result.updatedTokenRelsStillInChain())
                .containsExactly(LOCAL_TOKEN_REL_2
                        .copyBuilder()
                        .previousToken(NULL_TOKEN_NUMBER)
                        .build());
    }

    @Test
    void removeTokenRels_removesEvenTokenRels() {
        final var evenLocalTokenRels = List.of(LOCAL_TOKEN_REL_2, LOCAL_TOKEN_REL_4);
        final var result = subject.removeTokenRels(ACCT_2300, evenLocalTokenRels);
        // The account's head token number shouldn't have changed because it's an odd-numbered token
        Assertions.assertThat(result.updatedHeadTokenId()).isEqualTo(ACCT_2300.headTokenNumber());
        Assertions.assertThat(result.updatedTokenRelsStillInChain())
                .containsExactlyInAnyOrder(
                        LOCAL_TOKEN_REL_1
                                .copyBuilder()
                                .nextToken(TOKEN_NUMBER_3)
                                .build(),
                        LOCAL_TOKEN_REL_3
                                .copyBuilder()
                                .previousToken(TOKEN_NUMBER_1)
                                .nextToken(TOKEN_NUMBER_5)
                                .build(),
                        LOCAL_TOKEN_REL_5
                                .copyBuilder()
                                .previousToken(TOKEN_NUMBER_3)
                                .nextToken(NULL_TOKEN_NUMBER)
                                .build());
    }

    @Test
    void removeTokenRels_removesOddTokenRels() {
        final var oddHeadTokenRels = List.of(LOCAL_TOKEN_REL_1, LOCAL_TOKEN_REL_3, LOCAL_TOKEN_REL_5);
        final var result = subject.removeTokenRels(ACCT_2300, oddHeadTokenRels);
        Assertions.assertThat(result.updatedHeadTokenId()).isEqualTo(TOKEN_NUMBER_2);
        Assertions.assertThat(result.updatedTokenRelsStillInChain())
                .containsExactlyInAnyOrder(
                        LOCAL_TOKEN_REL_2
                                .copyBuilder()
                                .previousToken(NULL_TOKEN_NUMBER)
                                .nextToken(TOKEN_NUMBER_4)
                                .build(),
                        LOCAL_TOKEN_REL_4
                                .copyBuilder()
                                .previousToken(TOKEN_NUMBER_2)
                                .nextToken(NULL_TOKEN_NUMBER)
                                .build());
    }

    @Test
    void removeTokenRels_removesConsecutiveTokenRels() {
        final var consecutiveLocalTokenRels = List.of(LOCAL_TOKEN_REL_2, LOCAL_TOKEN_REL_3, LOCAL_TOKEN_REL_4);
        final var result = subject.removeTokenRels(ACCT_2300, consecutiveLocalTokenRels);
        Assertions.assertThat(result.updatedHeadTokenId()).isEqualTo(ACCT_2300.headTokenNumber());
        Assertions.assertThat(result.updatedTokenRelsStillInChain())
                .containsExactlyInAnyOrder(
                        LOCAL_TOKEN_REL_1
                                .copyBuilder()
                                .nextToken(TOKEN_NUMBER_5)
                                .build(),
                        LOCAL_TOKEN_REL_5
                                .copyBuilder()
                                .previousToken(TOKEN_NUMBER_1)
                                .build());
    }

    @Test
    void removeTokenRels_removesConsecutiveAndSeparatedTokenRels() {
        // Token rel 1 and token rel 2 are in consecutive order; token rel 4 is separated from the consecutive token
        // rels by token rel 3, which token rel 3 will remain in the list
        final var localTokenRels = List.of(LOCAL_TOKEN_REL_1, LOCAL_TOKEN_REL_2, LOCAL_TOKEN_REL_4);
        final var result = subject.removeTokenRels(ACCT_2300, localTokenRels);
        Assertions.assertThat(result.updatedHeadTokenId()).isEqualTo(TOKEN_NUMBER_3);
        Assertions.assertThat(result.updatedTokenRelsStillInChain())
                .containsExactlyInAnyOrder(
                        LOCAL_TOKEN_REL_3
                                .copyBuilder()
                                .previousToken(NULL_TOKEN_NUMBER)
                                .nextToken(TOKEN_NUMBER_5)
                                .build(),
                        LOCAL_TOKEN_REL_5
                                .copyBuilder()
                                .previousToken(TOKEN_NUMBER_3)
                                .build());
    }

    @Test
    void removeTokenRels_nullAndDuplicateTokenRelsRemoved() {
        final var nullsAndDuplicatesTokenRels = new ArrayList<TokenRelation>();
        nullsAndDuplicatesTokenRels.add(null);
        nullsAndDuplicatesTokenRels.add(null);
        nullsAndDuplicatesTokenRels.add(LOCAL_TOKEN_REL_2);
        nullsAndDuplicatesTokenRels.add(LOCAL_TOKEN_REL_2);
        nullsAndDuplicatesTokenRels.add(LOCAL_TOKEN_REL_4);
        nullsAndDuplicatesTokenRels.add(null);
        nullsAndDuplicatesTokenRels.add(LOCAL_TOKEN_REL_4);
        nullsAndDuplicatesTokenRels.add(null);
        nullsAndDuplicatesTokenRels.add(LOCAL_TOKEN_REL_2);
        final var result = subject.removeTokenRels(ACCT_2300, nullsAndDuplicatesTokenRels);

        // Results should be identical to the _removesEvenTokenRels case

        // The account's head token number shouldn't have changed because it's an odd-numbered token
        Assertions.assertThat(result.updatedHeadTokenId()).isEqualTo(ACCT_2300.headTokenNumber());
        Assertions.assertThat(result.updatedTokenRelsStillInChain())
                .containsExactlyInAnyOrder(
                        LOCAL_TOKEN_REL_1
                                .copyBuilder()
                                .nextToken(TOKEN_NUMBER_3)
                                .build(),
                        LOCAL_TOKEN_REL_3
                                .copyBuilder()
                                .previousToken(TOKEN_NUMBER_1)
                                .nextToken(TOKEN_NUMBER_5)
                                .build(),
                        LOCAL_TOKEN_REL_5
                                .copyBuilder()
                                .previousToken(TOKEN_NUMBER_3)
                                .nextToken(NULL_TOKEN_NUMBER)
                                .build());
    }

    @Test
    void removeTokenRels_selfPointingTokenRel() {
        final var selfPointingTokenRel = LOCAL_TOKEN_REL_1
                .copyBuilder()
                .previousToken(TOKEN_NUMBER_1)
                .nextToken(TOKEN_NUMBER_1)
                .build();
        final var result = subject.removeTokenRels(ACCT_2300, List.of(selfPointingTokenRel));
        // Since the token rel points to itself, the calculation of the account's new head token number should loop
        // until it maxes out at a safety boundary, at which point we should default to a head token number of -1
        Assertions.assertThat(result.updatedHeadTokenId()).isEqualTo(-1);
    }

    private static ReadableTokenRelationStore localTokenRelsStore() {
        final long acct2300 = ACCT_2300_ID.accountNumOrThrow();
        final var tokenRels = new HashMap<EntityNumPair, TokenRelation>();
        tokenRels.put(EntityNumPair.fromLongs(acct2300, TOKEN_NUMBER_1), LOCAL_TOKEN_REL_1);
        tokenRels.put(EntityNumPair.fromLongs(acct2300, TOKEN_NUMBER_2), LOCAL_TOKEN_REL_2);
        tokenRels.put(EntityNumPair.fromLongs(acct2300, TOKEN_NUMBER_3), LOCAL_TOKEN_REL_3);
        tokenRels.put(EntityNumPair.fromLongs(acct2300, TOKEN_NUMBER_4), LOCAL_TOKEN_REL_4);
        tokenRels.put(EntityNumPair.fromLongs(acct2300, TOKEN_NUMBER_5), LOCAL_TOKEN_REL_5);

        final var wrappedState = new MapReadableKVState<>(TOKEN_RELS_KEY, tokenRels);
        return new ReadableTokenRelationStoreImpl(mockStates(Map.of(TOKEN_RELS_KEY, wrappedState)));
    }
}
