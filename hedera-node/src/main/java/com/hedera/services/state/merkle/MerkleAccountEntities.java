package com.hedera.services.state.merkle;

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

import com.google.common.base.MoreObjects;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hedera.services.ledger.HederaLedger.NFT_ID_COMPARATOR;
import static com.hedera.services.ledger.HederaLedger.TOKEN_ID_COMPARATOR;
import static java.util.stream.Collectors.toList;

public class MerkleAccountEntities extends AbstractMerkleLeaf {
	private static final Logger log = LogManager.getLogger(MerkleAccountEntities.class);

	private static final int NUM_OFFSET = 0;
	private static final int REALM_OFFSET = 1;
	private static final int SHARD_OFFSET = 2;

	private static final int RELEASE_090_VERSION = 1;

	private static final long[] NO_ASSOCIATIONS = new long[0];

	static final int NUM_ID_PARTS = 3;
	static final int MAX_CONCEIVABLE_ENTITY_ID_PARTS = Integer.MAX_VALUE;

	static final int MERKLE_VERSION = RELEASE_090_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x4dd9cde14aae5f8eL;

	private long[] entityIds = NO_ASSOCIATIONS;

	public MerkleAccountEntities() {
	}

	public MerkleAccountEntities(long[] entityIds) {
		if (entityIds.length % NUM_ID_PARTS != 0) {
			throw new IllegalArgumentException(String.format(
					"Argument 'entityIds' has length=%d not divisible by %d", entityIds.length, NUM_ID_PARTS));
		}
		this.entityIds = entityIds;
	}

	/* --- MerkleLeaf --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		entityIds = in.readLongArray(MAX_CONCEIVABLE_ENTITY_ID_PARTS);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLongArray(entityIds);
	}

	/* --- Copyable --- */
	public MerkleAccountEntities copy() {
		return new MerkleAccountEntities(entityIds);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleAccountEntities.class != o.getClass()) {
			return false;
		}

		var that = (MerkleAccountEntities) o;

		return Arrays.equals(this.entityIds, that.entityIds);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(entityIds);
	}

	/* --- Bean --- */
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("entities", readableEntityIds())
				.toString();
	}

	public List<NftID> asNftIds() {
		return asIds(NFT_ID_BUILDER);
	}

	public List<TokenID> asTokenIds() {
		return asIds(TOKEN_ID_BUILDER);
	}

	long[] getEntityIds() {
		return entityIds;
	}

	/* --- Association Manipulation --- */
	public int numAssociations() {
		return entityIds.length / NUM_ID_PARTS;
	}

	public boolean includes(NftID id) {
		return logicalIndexOf(id.getShardNum(), id.getRealmNum(), id.getNftNum()) >= 0;
	}

	public boolean includes(TokenID id) {
		return logicalIndexOf(id.getShardNum(), id.getRealmNum(), id.getTokenNum()) >= 0;
	}

	public void associateAllNfts(Set<NftID> ids) {
		associateAll(ids, NFT_ID_BUILDER, NFT_ID_COMPARATOR, NFT_PARTS_FN);
	}

	public void associateAllTokens(Set<TokenID> ids) {
		associateAll(ids, TOKEN_ID_BUILDER, TOKEN_ID_COMPARATOR, TOKEN_PARTS_FN);
	}

	public void dissociateAllTokens(Set<TokenID> ids) {
		dissociateAll(ids, TOKEN_ID_BUILDER, TOKEN_PARTS_FN);
	}

	private <T> void dissociateAll(
			Set<T> ids,
			IdBuilder<T> builder,
			Function<T, long[]> partsFn
	) {
		int n = numAssociations(), newN = 0;
		for (int i = 0; i < n; i++) {
			if (!ids.contains(idAt(i, builder))) {
				newN++;
			}
		}
		if (newN != n) {
			long[] newEntityIds = new long[newN * NUM_ID_PARTS];
			for (int i = 0, j = 0; i < n; i++) {
				var id = idAt(i, builder);
				if (!ids.contains(id)) {
					set(newEntityIds, j++, partsFn.apply(id));
				}
			}
			entityIds = newEntityIds;
		}
	}

	private void set(long[] someEntityIds, int i, long[] entityId) {
		someEntityIds[shard(i)] = entityId[0];
		someEntityIds[realm(i)] = entityId[1];
		someEntityIds[num(i)] = entityId[2];
	}

	private <T> T idAt(int i, IdBuilder<T> builder) {
		return builder.build(entityIds[shard(i)], entityIds[realm(i)], entityIds[num(i)]);
	}

	/* --- Helpers --- */
	private int num(int i) {
		return i * NUM_ID_PARTS + NUM_OFFSET;
	}

	private int realm(int i) {
		return i * NUM_ID_PARTS + REALM_OFFSET;
	}

	private int shard(int i) {
		return i * NUM_ID_PARTS + SHARD_OFFSET;
	}

	private int logicalIndexOf(long shard, long realm, long num) {
		int lo = 0, hi = entityIds.length / NUM_ID_PARTS - 1;
		while (lo <= hi) {
			int mid = lo + (hi - lo) / 2;
			int comparison = compareImplied(mid, shard, realm, num);
			if (comparison == 0) {
				return mid;
			} else if (comparison < 0) {
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return -(lo + 1);
	}

	private int compareImplied(int at, long shard, long realm, long num) {
		long numA = entityIds[num(at)];
		if (numA == num) {
			long realmA = entityIds[realm(at)];
			if (realmA == realm) {
				return Long.compare(entityIds[shard(at)], shard);
			} else {
				return Long.compare(realmA, realm);
			}
		} else {
			return Long.compare(numA, num);
		}
	}

	String readableEntityIds() {
		var sb = new StringBuilder("[");
		for (int i = 0, n = numAssociations(); i < n; i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(String.format(
					"%d.%d.%d",
					entityIds[shard(i)],
					entityIds[realm(i)],
					entityIds[num(i)]));
		}
		sb.append("]");

		return sb.toString();
	}

	private <T> void associateAll(
			Set<T> ids,
			IdBuilder<T> builder,
			Comparator<T> cmp,
			Function<T, long[]> partsFn
	) {
		List<T> allTogether = Stream.concat(
				ids.stream(),
				IntStream.range(0, numAssociations()).mapToObj(i -> idAt(i, builder))
		)
				.sorted(cmp).collect(toList());
		int newN = numAssociations() + ids.size();
		long[] entityIds = new long[newN * NUM_ID_PARTS];
		for (int i = 0; i < newN; i++) {
			set(entityIds, i, partsFn.apply(allTogether.get(i)));
		}
		this.entityIds = entityIds;
	}

	private <T> List<T> asIds(IdBuilder<T> builder) {
		int n;
		if ((n = numAssociations()) == 0) {
			return Collections.emptyList();
		} else {
			List<T> ids = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				ids.add(idAt(i, builder));
			}
			return ids;
		}
	}

	interface IdBuilder<T> {
		T build(long shard, long realm, long num);
	}

	private static final IdBuilder<NftID> NFT_ID_BUILDER = (s, r, n) -> NftID.newBuilder()
			.setShardNum(s)
			.setRealmNum(r)
			.setNftNum(n)
			.build();
	private static final IdBuilder<TokenID> TOKEN_ID_BUILDER = (s, r, n) -> TokenID.newBuilder()
			.setShardNum(s)
			.setRealmNum(r)
			.setTokenNum(n)
			.build();
	private static final Function<NftID, long[]> NFT_PARTS_FN = nId -> new long[] {
			nId.getShardNum(), nId.getRealmNum(), nId.getNftNum()
	};
	private static final Function<TokenID, long[]> TOKEN_PARTS_FN = tId -> new long[] {
			tId.getShardNum(), tId.getRealmNum(), tId.getTokenNum()
	};
}
