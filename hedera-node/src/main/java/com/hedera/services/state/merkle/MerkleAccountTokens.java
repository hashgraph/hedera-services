package com.hedera.services.state.merkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hedera.services.ledger.HederaLedger.TOKEN_ID_COMPARATOR;
import static java.util.stream.Collectors.toList;

public class MerkleAccountTokens extends AbstractMerkleLeaf {
	private static final Logger log = LogManager.getLogger(MerkleAccountTokens.class);

	static final int MAX_CONCEIVABLE_TOKEN_ID_PARTS = Integer.MAX_VALUE;

	static final long[] NO_ASSOCIATIONS = new long[0];
	static final int NUM_ID_PARTS = 3;
	static final int NUM_OFFSET = 0;
	static final int REALM_OFFSET = 1;
	static final int SHARD_OFFSET = 2;

	static final int RELEASE_090_VERSION = 1;
	static final int MERKLE_VERSION = RELEASE_090_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x4dd9cde14aae5f8eL;

	long[] tokenIds = NO_ASSOCIATIONS;

	public MerkleAccountTokens() {
	}

	public MerkleAccountTokens(long[] tokenIds) {
		if (tokenIds.length % NUM_ID_PARTS != 0) {
			throw new IllegalArgumentException(
					"The token ids array length must be divisible by " + NUM_ID_PARTS + "!");
		}
		this.tokenIds = tokenIds;
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
		tokenIds = in.readLongArray(MAX_CONCEIVABLE_TOKEN_ID_PARTS);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLongArray(tokenIds);
	}

	/* --- Copyable --- */
	public MerkleAccountTokens copy() {
		return new MerkleAccountTokens(tokenIds);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleAccountTokens.class != o.getClass()) {
			return false;
		}

		var that = (MerkleAccountTokens) o;

		return Arrays.equals(this.tokenIds, that.tokenIds);
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(tokenIds);
	}

	/* --- Bean --- */
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("tokens", readableTokenIds())
				.toString();
	}

	public String readableTokenIds() {
		var sb = new StringBuilder("[");
		for (int i = 0, n = numAssociations(); i < n; i++) {
			if (i > 0) {
				sb.append(", ");
			}
			sb.append(String.format(
					"%d.%d.%d",
					tokenIds[shard(i)],
					tokenIds[realm(i)],
					tokenIds[num(i)]));
		}
		sb.append("]");

		return sb.toString();
	}

	public List<TokenID> asIds() {
		int n;
		if ((n = numAssociations()) == 0) {
			return Collections.emptyList();
		} else {
			List<TokenID> ids = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				ids.add(idAt(i));
			}
			return ids;
		}
	}

	long[] getTokenIds() {
		return tokenIds;
	}

	/* --- Association Manipulation --- */
	public int numAssociations() {
		return tokenIds.length / NUM_ID_PARTS;
	}

	public boolean includes(TokenID id) {
		return logicalIndexOf(id) >= 0;
	}

	public void associateAll(Set<TokenID> ids) {
		List<TokenID> allTogether = Stream.concat(
				ids.stream(),
				IntStream.range(0, numAssociations()).mapToObj(this::idAt)).sorted(TOKEN_ID_COMPARATOR).collect(toList());
		int newN = numAssociations() + ids.size();
		long[] newTokenIds = new long[newN * NUM_ID_PARTS];
		for (int i = 0; i < newN; i++) {
			set(newTokenIds, i, allTogether.get(i));
		}
		tokenIds = newTokenIds;
	}

	public void dissociateAll(Set<TokenID> ids) {
		int n = numAssociations(), newN = 0;
		for (int i = 0; i < n; i++) {
			if (!ids.contains(idAt(i))) {
				newN++;
			}
		}
		if (newN != n) {
			long[] newTokenIds = new long[newN * NUM_ID_PARTS];
			for (int i = 0, j = 0; i < n; i++) {
				var id = idAt(i);
				if (!ids.contains(id)) {
					set(newTokenIds, j++, id);
				}
			}
			tokenIds = newTokenIds;
		}
	}

	private void set(long[] someTokenIds, int i, TokenID id) {
		someTokenIds[shard(i)] = id.getShardNum();
		someTokenIds[realm(i)] = id.getRealmNum();
		someTokenIds[num(i)] = id.getTokenNum();
	}

	public int purge(Predicate<TokenID> isGone, Predicate<TokenID> isDeleted) {
		int effectiveAssociations = 0, meaningfulAssociations = 0, n = numAssociations();
		for (int i = 0; i < n; i++) {
			var id = idAt(i);
			if (isGone.test(id)) {
				continue;
			}
			meaningfulAssociations++;
			if (isDeleted.test(id)) {
				continue;
			}
			effectiveAssociations++;
		}

		if (meaningfulAssociations != n) {
			long[] newTokenIds = new long[meaningfulAssociations * NUM_ID_PARTS];
			for (int i = 0, j = 0; i < n; i++) {
				var id = idAt(i);
				if (isGone.test(id)) {
					continue;
				}
				System.arraycopy(tokenIds, i * NUM_ID_PARTS, newTokenIds, j * NUM_ID_PARTS, NUM_ID_PARTS);
				j++;
			}
			this.tokenIds = newTokenIds;
		}
		return effectiveAssociations;
	}

	private TokenID idAt(int i) {
		return TokenID.newBuilder()
				.setShardNum(tokenIds[shard(i)])
				.setRealmNum(tokenIds[realm(i)])
				.setTokenNum(tokenIds[num(i)])
				.build();
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

	int logicalIndexOf(TokenID token) {
		int lo = 0, hi = tokenIds.length / NUM_ID_PARTS - 1;
		while (lo <= hi) {
			int mid = (lo + (hi - lo) / NUM_ID_PARTS);
			int comparison = compareImplied(mid, token);
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

	private int compareImplied(int at, TokenID to) {
		long numA = tokenIds[num(at)], numB = to.getTokenNum();
		if (numA == numB) {
			long realmA = tokenIds[realm(at)], realmB = to.getRealmNum();
			if (realmA == realmB) {
				return Long.compare(tokenIds[shard(at)], to.getShardNum());
			} else {
				return Long.compare(realmA, realmB);
			}
		} else {
			return Long.compare(numA, numB);
		}
	}
}
