package com.hedera.services.ledger.accounts;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.state.merkle.MerkleBatchedUniqTokens;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.NftId;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.function.Supplier;

public class BackingNfts implements BackingStore<NftId, MerkleUniqueToken> {
	static final Logger log = LogManager.getLogger(BackingNfts.class);

	private final Supplier<FCMap<MerkleUniqueTokenId, MerkleBatchedUniqTokens>> delegate;

	public BackingNfts(Supplier<FCMap<MerkleUniqueTokenId, MerkleBatchedUniqTokens>> delegate) {
		this.delegate = delegate;
	}

	@Override
	public void rebuildFromSources() {
		/* No-op */
	}

	@Override
	public MerkleUniqueToken getRef(NftId id) {
		return fromBatch(extractModifiableBatch(delegate.get(), id), id.serialNo());
	}

	@Override
	public MerkleUniqueToken getImmutableRef(NftId id) {
		return fromBatch(extractUnsafeBatch(delegate.get(), id), id.serialNo());
	}

	private MerkleUniqueToken fromBatch(@Nullable MerkleBatchedUniqTokens batch, long serialNo) {
		if (batch == null) {
			return null;
		} else {
			return batch.get(asBatchLoc(serialNo));
		}
	}

	@Override
	public void put(NftId id, MerkleUniqueToken nft) {
		throw new AssertionError("Not implemented!");
	}

	@Override
	public void remove(NftId id) {
		throw new AssertionError("Not implemented!");
	}

	@Override
	public boolean contains(NftId id) {
		final var currentNfts = delegate.get();
		final var batchKey = batchKeyFor(id);
		if (!currentNfts.containsKey(batchKey))	{
			return false;
		}
		final var batch = currentNfts.get(batchKey);
		return batch.isMinted(asBatchLoc(id.serialNo()));
	}

	@Override
	public Set<NftId> idSet() {
		throw new UnsupportedOperationException();
	}

	public static MerkleUniqueTokenId batchKeyFor(NftId id) {
		final var startSerialNo = id.serialNo() / MerkleBatchedUniqTokens.TOKENS_IN_BATCH;
		return new MerkleUniqueTokenId(EntityId.fromGrpcTokenId(id.tokenId()), startSerialNo);
	}

	public static int asBatchLoc(long serialNo) {
		return (int)(serialNo % MerkleBatchedUniqTokens.TOKENS_IN_BATCH);
	}

	public static MerkleBatchedUniqTokens extractModifiableBatch(
			FCMap<MerkleUniqueTokenId, MerkleBatchedUniqTokens> batches,
			NftId id
	) {
		final var batch = batchKeyFor(id);
		if (batches.containsKey(batch)) {
			return batches.getForModify(batch);
		} else {
			return null;
		}
	}

	public static MerkleBatchedUniqTokens extractUnsafeBatch(
			FCMap<MerkleUniqueTokenId, MerkleBatchedUniqTokens> batches,
			NftId id
	) {
		final var batch = batchKeyFor(id);
		if (batches.containsKey(batch)) {
			return batches.get(batch);
		} else {
			return null;
		}
	}
}
