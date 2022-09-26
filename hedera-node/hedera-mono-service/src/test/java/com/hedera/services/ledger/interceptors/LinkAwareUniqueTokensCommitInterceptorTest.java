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
package com.hedera.services.ledger.interceptors;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.state.migration.UniqueTokenAdapter;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LinkAwareUniqueTokensCommitInterceptorTest {
    @Mock private UsageLimits usageLimits;
    @Mock private UniqueTokensLinkManager uniqueTokensLinkManager;

    private LinkAwareUniqueTokensCommitInterceptor subject;

    @BeforeEach
    void setUp() {
        subject = new LinkAwareUniqueTokensCommitInterceptor(usageLimits, uniqueTokensLinkManager);
    }

    @Test
    void noChangesAreNoOp() {
        final var changes = new EntityChangeSet<NftId, UniqueTokenAdapter, NftProperty>();

        subject.preview(changes);

        verifyNoInteractions(uniqueTokensLinkManager);
    }

    @Test
    @SuppressWarnings("unchecked")
    void zombieCommitIsNoOp() {
        var changes =
                (EntityChangeSet<NftId, UniqueTokenAdapter, NftProperty>)
                        mock(EntityChangeSet.class);
        given(changes.size()).willReturn(1);
        given(changes.entity(0)).willReturn(null);
        given(changes.changes(0)).willReturn(null);

        subject.preview(changes);

        verifyNoInteractions(uniqueTokensLinkManager);
    }

    @Test
    @SuppressWarnings("unchecked")
    void resultsInNoOpForNoOwnershipChanges() {
        var changes =
                (EntityChangeSet<NftId, UniqueTokenAdapter, NftProperty>)
                        mock(EntityChangeSet.class);
        var nft = mock(UniqueTokenAdapter.class);

        given(changes.size()).willReturn(1);
        given(changes.entity(0)).willReturn(nft);
        given(changes.changes(0)).willReturn(Collections.emptyMap());

        subject.preview(changes);

        verifyNoInteractions(uniqueTokensLinkManager);
    }

    @Test
    @SuppressWarnings("unchecked")
    void resultsInNoOpForSameOwnershipChange() {
        var changes =
                (EntityChangeSet<NftId, UniqueTokenAdapter, NftProperty>)
                        mock(EntityChangeSet.class);
        var nft = mock(UniqueTokenAdapter.class);
        final long ownerNum = 1111L;
        final var owner = EntityNum.fromLong(ownerNum);

        given(changes.size()).willReturn(1);
        given(changes.entity(0)).willReturn(nft);
        given(changes.changes(0)).willReturn(Map.of(NftProperty.OWNER, owner.toEntityId()));
        given(nft.getOwner()).willReturn(owner.toEntityId());

        subject.preview(changes);

        verifyNoInteractions(uniqueTokensLinkManager);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonTreasuryExitTriggersUpdateLinksAsExpected() {
        final var changes =
                (EntityChangeSet<NftId, UniqueTokenAdapter, NftProperty>)
                        mock(EntityChangeSet.class);
        final var nft = mock(UniqueTokenAdapter.class);
        final var change = (HashMap<NftProperty, Object>) mock(HashMap.class);
        final long ownerNum = 1111L;
        final long newOwnerNum = 1234L;
        final long tokenNum = 2222L;
        final long serialNum = 2L;
        EntityNum owner = EntityNum.fromLong(ownerNum);
        EntityNum newOwner = EntityNum.fromLong(newOwnerNum);
        EntityNumPair nftKey = EntityNumPair.fromLongs(tokenNum, serialNum);

        given(changes.size()).willReturn(1);
        given(changes.entity(0)).willReturn(nft);
        given(changes.changes(0)).willReturn(change);
        given(changes.id(0)).willReturn(nftKey.asNftNumPair().nftId());
        given(change.containsKey(NftProperty.OWNER)).willReturn(true);
        given(change.get(NftProperty.OWNER)).willReturn(newOwner.toEntityId());
        given(nft.getOwner()).willReturn(owner.toEntityId());

        subject.preview(changes);

        verify(uniqueTokensLinkManager).updateLinks(owner, newOwner, nftKey);
    }

    @Test
    @SuppressWarnings("unchecked")
    void treasuryBurnDoesNotUpdateLinks() {
        final var changes =
                (EntityChangeSet<NftId, UniqueTokenAdapter, NftProperty>)
                        mock(EntityChangeSet.class);
        final var nft = mock(UniqueTokenAdapter.class);
        EntityNum owner = EntityNum.MISSING_NUM;

        given(changes.size()).willReturn(1);
        given(changes.entity(0)).willReturn(nft);
        given(changes.changes(0)).willReturn(null);
        given(nft.getOwner()).willReturn(owner.toEntityId());

        subject.preview(changes);

        verifyNoInteractions(uniqueTokensLinkManager);
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonOwnerUpdateDoesNotUpdateLinks() {
        final var changes =
                (EntityChangeSet<NftId, UniqueTokenAdapter, NftProperty>)
                        mock(EntityChangeSet.class);
        final var nft = mock(UniqueTokenAdapter.class);
        EntityNum owner = EntityNum.MISSING_NUM;
        final Map<NftProperty, Object> scopedChanges = new EnumMap<>(NftProperty.class);
        scopedChanges.put(NftProperty.SPENDER, new EntityId(0, 0, 123));

        given(changes.size()).willReturn(1);
        given(changes.entity(0)).willReturn(nft);
        given(changes.changes(0)).willReturn(scopedChanges);
        given(nft.getOwner()).willReturn(owner.toEntityId());

        subject.preview(changes);

        verifyNoInteractions(uniqueTokensLinkManager);
    }

    @Test
    @SuppressWarnings("unchecked")
    void triggersUpdateLinksOnWipeAsExpected() {
        final var changes =
                (EntityChangeSet<NftId, UniqueTokenAdapter, NftProperty>)
                        mock(EntityChangeSet.class);
        final var nft = mock(UniqueTokenAdapter.class);
        final long ownerNum = 1111L;
        final long tokenNum = 2222L;
        final long serialNum = 2L;
        EntityNum owner = EntityNum.fromLong(ownerNum);
        EntityNumPair nftKey = EntityNumPair.fromLongs(tokenNum, serialNum);

        given(changes.size()).willReturn(1);
        given(changes.id(0)).willReturn(nftKey.asNftNumPair().nftId());
        given(changes.entity(0)).willReturn(nft);
        given(changes.changes(0)).willReturn(null);
        given(nft.getOwner()).willReturn(owner.toEntityId());

        subject.preview(changes);

        verify(uniqueTokensLinkManager).updateLinks(owner, null, nftKey);
    }

    @Test
    @SuppressWarnings("unchecked")
    void triggersUpdateLinksOnMultiStageMintAndTransferAsExpected() {
        final var changes =
                (EntityChangeSet<NftId, UniqueTokenAdapter, NftProperty>)
                        mock(EntityChangeSet.class);
        final long ownerNum = 1111L;
        final long tokenNum = 2222L;
        final long serialNum = 2L;
        final Map<NftProperty, Object> scopedChanges = new EnumMap<>(NftProperty.class);
        EntityNum owner = EntityNum.fromLong(ownerNum);
        EntityNumPair nftKey = EntityNumPair.fromLongs(tokenNum, serialNum);
        final var mintedNft = UniqueTokenAdapter.newEmptyMerkleToken();

        given(changes.size()).willReturn(1);
        given(changes.id(0)).willReturn(nftKey.asNftNumPair().nftId());
        given(changes.entity(0)).willReturn(null);
        given(changes.changes(0)).willReturn(scopedChanges);
        scopedChanges.put(NftProperty.OWNER, owner.toEntityId());
        given(uniqueTokensLinkManager.updateLinks(null, owner, nftKey))
                .willReturn(mintedNft.merkleUniqueToken());

        subject.preview(changes);

        verify(uniqueTokensLinkManager).updateLinks(null, owner, nftKey);
        ArgumentCaptor<UniqueTokenAdapter> argumentCaptor =
                ArgumentCaptor.forClass(UniqueTokenAdapter.class);
        verify(changes).cacheEntity(eq(0), argumentCaptor.capture());
        assertThat(argumentCaptor.getValue()).isEqualTo(mintedNft);
    }

    @Test
    void postCommitIsNoopIfNothingMintedOrBurned() {
        subject.preview(pendingChanges(false, false));
        subject.postCommit();
        verifyNoInteractions(usageLimits);
    }

    @Test
    void postCommitRefreshesCountOnMint() {
        subject.preview(pendingChanges(true, false));
        subject.postCommit();
        verify(usageLimits).refreshNfts();
    }

    @Test
    void postCommitRefreshesCountOnBurn() {
        subject.preview(pendingChanges(false, true));
        subject.postCommit();
        verify(usageLimits).refreshNfts();
    }

    @Test
    @SuppressWarnings("unchecked")
    void doesntTriggerUpdateLinkOnNormalTreasuryMint() {
        final var changes =
                (EntityChangeSet<NftId, UniqueTokenAdapter, NftProperty>)
                        mock(EntityChangeSet.class);
        final long tokenNum = 2222L;
        final long serialNum = 2L;
        final Map<NftProperty, Object> scopedChanges = new EnumMap<>(NftProperty.class);

        given(changes.size()).willReturn(1);
        given(changes.entity(0)).willReturn(null);
        given(changes.changes(0)).willReturn(scopedChanges);
        scopedChanges.put(NftProperty.OWNER, EntityId.MISSING_ENTITY_ID);

        subject.preview(changes);

        verifyNoInteractions(uniqueTokensLinkManager);
    }

    private EntityChangeSet<NftId, UniqueTokenAdapter, NftProperty> pendingChanges(
            final boolean includeMint, final boolean includeBurn) {
        final EntityChangeSet<NftId, UniqueTokenAdapter, NftProperty> pendingChanges =
                new EntityChangeSet<>();
        if (includeBurn) {
            pendingChanges.include(
                    new NftId(0, 0, 1234, 5678), UniqueTokenAdapter.newEmptyMerkleToken(), null);
        }
        if (includeMint) {
            pendingChanges.include(
                    new NftId(0, 0, 1234, 5679),
                    null,
                    Map.of(NftProperty.OWNER, EntityId.MISSING_ENTITY_ID));
        }
        pendingChanges.include(
                new NftId(0, 0, 1234, 5680), UniqueTokenAdapter.newEmptyMerkleToken(), Map.of());
        return pendingChanges;
    }
}
