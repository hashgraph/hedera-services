/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.ledger.interceptors;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.node.app.service.mono.context.SideEffectsTracker;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.ledger.EntityChangeSet;
import com.hedera.node.app.service.mono.ledger.properties.TokenRelProperty;
import com.hedera.node.app.service.mono.state.merkle.MerkleTokenRelStatus;
import com.hedera.node.app.service.mono.state.migration.HederaTokenRel;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.state.virtual.entities.OnDiskTokenRel;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.mono.utils.EntityNumPair;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkAwareTokenRelsCommitInterceptorTest {
    @Mock
    private UsageLimits usageLimits;

    @Mock
    private TxnAccessor accessor;

    @Mock
    private TransactionContext txnCtx;

    @Mock
    private SideEffectsTracker sideEffectsTracker;

    @Mock
    private TokenRelsLinkManager relsLinkManager;

    @Mock
    private Supplier<HederaTokenRel> tokenRelSupplier;

    private LinkAwareTokenRelsCommitInterceptor subject;

    @BeforeEach
    void setUp() {
        subject = new LinkAwareTokenRelsCommitInterceptor(
                usageLimits, txnCtx, sideEffectsTracker, relsLinkManager, tokenRelSupplier);
    }

    @Test
    void doesPendingRemovals() {
        assertTrue(subject.completesPendingRemovals());
    }

    @Test
    void noChangesAreNoop() {
        final var changes = new EntityChangeSet<Pair<AccountID, TokenID>, HederaTokenRel, TokenRelProperty>();

        subject.preview(changes);

        verifyNoInteractions(sideEffectsTracker);
    }

    @Test
    void tracksNothingIfOpIsNotAutoAssociating() {
        given(accessor.getFunction()).willReturn(CryptoUpdate);
        given(txnCtx.accessor()).willReturn(accessor);
        given(tokenRelSupplier.get()).willReturn(new OnDiskTokenRel());

        final var changes = someChanges();

        subject.preview(changes);

        verifyNoInteractions(sideEffectsTracker);
    }

    @Test
    void tracksSideEffectsIfOpIsAutoAssociating() {
        given(accessor.getFunction()).willReturn(TokenCreate);
        given(txnCtx.accessor()).willReturn(accessor);
        given(tokenRelSupplier.get()).willReturn(new OnDiskTokenRel());

        final var changes = someChanges();

        subject.preview(changes);

        verify(sideEffectsTracker).trackAutoAssociation(newAssocTokenId, aAccountId);
    }

    @Test
    void addsAndRemovesRelsAsExpected() {
        given(accessor.getFunction()).willReturn(ContractCall);
        given(txnCtx.accessor()).willReturn(accessor);
        given(tokenRelSupplier.get()).willReturn(new OnDiskTokenRel());

        final var expectedNewRel = new OnDiskTokenRel();
        expectedNewRel.setKey(EntityNumPair.fromLongs(aAccountId.getAccountNum(), newAssocTokenId.getTokenNum()));
        final var changes = someChanges();

        subject.preview(changes);
        assertNotNull(changes.entity(1));

        verify(relsLinkManager).updateLinks(accountNum, List.of(tbdTokenNum), List.of(expectedNewRel));

        subject.postCommit();
        verify(usageLimits).refreshTokenRels();
    }

    private EntityChangeSet<Pair<AccountID, TokenID>, HederaTokenRel, TokenRelProperty> someChanges() {
        final var changes = new EntityChangeSet<Pair<AccountID, TokenID>, HederaTokenRel, TokenRelProperty>();
        changes.include(Pair.of(aAccountId, alreadyAssocTokenId), extantRel, Map.of());
        changes.include(Pair.of(aAccountId, newAssocTokenId), null, Map.of());
        tbdExtantRel.setKey(EntityNumPair.fromLongs(aAccountId.getAccountNum(), tbdAssocTokenId.getTokenNum()));
        changes.include(Pair.of(aAccountId, newAssocTokenId), tbdExtantRel, null);
        return changes;
    }

    final EntityNum accountNum = EntityNum.fromLong(1234);
    final AccountID aAccountId = accountNum.toGrpcAccountId();
    final TokenID alreadyAssocTokenId = TokenID.newBuilder().setTokenNum(1235).build();
    final TokenID newAssocTokenId = TokenID.newBuilder().setTokenNum(1236).build();
    final EntityNum tbdTokenNum = EntityNum.fromLong(1237);
    final TokenID tbdAssocTokenId = tbdTokenNum.toGrpcTokenId();
    final MerkleTokenRelStatus extantRel = new MerkleTokenRelStatus();
    final MerkleTokenRelStatus tbdExtantRel = new MerkleTokenRelStatus();
}
