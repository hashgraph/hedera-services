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

import com.hedera.services.ledger.CommitInterceptor;
import com.hedera.services.ledger.EntityChangeSet;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.NftId;

/**
 * Placeholder for upcoming work.
 */
public class LinkAwareUniqueTokensCommitInterceptor implements CommitInterceptor<NftId, MerkleUniqueToken, NftProperty> {
	private final UniqueTokensLinkManager uniqueTokensLinkManager;

	public LinkAwareUniqueTokensCommitInterceptor(final UniqueTokensLinkManager uniqueTokensLinkManager) {
		this.uniqueTokensLinkManager = uniqueTokensLinkManager;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void preview(final EntityChangeSet<NftId, MerkleUniqueToken, NftProperty> pendingChanges) {
		final var n = pendingChanges.size();
		if (n == 0) {
			return;
		}
		for (int i = 0; i < n; i++) {
			final var entity = pendingChanges.entity(i);
			final var change = pendingChanges.changes(i);
			if (entity != null && change != null && change.containsKey(NftProperty.OWNER)) {
				// we are changing the owner of an uniqueToken. we have to update the links
				final var fromAccount = entity.getOwner();
				final var toAccount = (EntityId) change.get(NftProperty.OWNER);
				uniqueTokensLinkManager.updateLinks(
						fromAccount.asNum(),
						toAccount.asNum(),
						entity.getKey(),
						entity.isTreasuryOwned(),
						toAccount.equals(EntityId.MISSING_ENTITY_ID));
			}
		}
	}
}
