package com.hedera.services.ledger.interceptors;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.state.virtual.UniqueTokenKey;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LinkAwareUniqueTokensCommitInterceptorTest {

	@Mock
	private UniqueTokensLinkManager uniqueTokensLinkManager;

	private LinkAwareUniqueTokensCommitInterceptor subject;

	@BeforeEach
	void setUp() {
		subject = new LinkAwareUniqueTokensCommitInterceptor(uniqueTokensLinkManager);
	}

	@Test
	void noChangesAreNoOp() {
		final var changes = new EntityChangeSet<NftId, UniqueTokenValue, NftProperty>();

		subject.preview(changes);

		verifyNoInteractions(uniqueTokensLinkManager);
	}

	@Test
	void resultsInNoOpForNoOwnershipChanges() {
		var changes = (EntityChangeSet<NftId, UniqueTokenValue, NftProperty>) mock(EntityChangeSet.class);
		var nft = mock(UniqueTokenValue.class);
		var change = (HashMap<NftProperty, Object>) mock(HashMap.class);

		given(changes.size()).willReturn(1);
		given(changes.entity(0)).willReturn(nft);
		given(changes.changes(0)).willReturn(change);
		given(change.containsKey(NftProperty.OWNER)).willReturn(false);

		subject.preview(changes);

		verifyNoInteractions(uniqueTokensLinkManager);
	}

	@Test
	void triggersUpdateLinksAsExpected() {
		final var changes = (EntityChangeSet<NftId, UniqueTokenValue, NftProperty>) mock(EntityChangeSet.class);
		final var nft = mock(UniqueTokenValue.class);
		final var change = (HashMap<NftProperty, Object>) mock(HashMap.class);
		final long ownerNum = 1111L;
		final long newOwnerNum = 1234L;
		final long tokenNum = 2222L;
		final long serialNum = 2L;
		EntityNum owner = EntityNum.fromLong(ownerNum);
		EntityNum newOwner = EntityNum.fromLong(newOwnerNum);
		UniqueTokenKey nftKey = new UniqueTokenKey(tokenNum, serialNum);


		given(changes.size()).willReturn(1);
		given(changes.entity(0)).willReturn(nft);
		given(changes.changes(0)).willReturn(change);
		given(change.containsKey(NftProperty.OWNER)).willReturn(true);
		given(change.get(NftProperty.OWNER)).willReturn(newOwner.toEntityId());
		given(nft.getOwner()).willReturn(owner.toEntityId());

		subject.preview(changes);

		verify(uniqueTokensLinkManager).updateLinks(owner, newOwner, nftKey);
	}

	@Test
	void triggersUpdateLinksOnWipeAsExpected() {
		final var changes = (EntityChangeSet<NftId, UniqueTokenValue, NftProperty>) mock(EntityChangeSet.class);
		final var nft = mock(UniqueTokenValue.class);
		final long ownerNum = 1111L;
		final long tokenNum = 2222L;
		final long serialNum = 2L;
		EntityNum owner = EntityNum.fromLong(ownerNum);
		UniqueTokenKey nftKey = new UniqueTokenKey(tokenNum, serialNum);

		given(changes.size()).willReturn(1);
		given(changes.entity(0)).willReturn(nft);
		given(changes.changes(0)).willReturn(null);
		given(nft.getOwner()).willReturn(owner.toEntityId());

		subject.preview(changes);

		verify(uniqueTokensLinkManager).updateLinks(owner, null, nftKey);
	}
}
