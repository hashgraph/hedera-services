package com.hedera.services.state.merkle;

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.common.base.MoreObjects;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.MiscUtils;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.IOException;

import static com.hedera.services.state.merkle.internals.IdentityCodeUtils.assertValid;
import static com.hedera.services.state.merkle.internals.IdentityCodeUtils.codeFromNum;

/**
 * Represents the ID of {@link MerkleUniqueTokenId}
 */
public class MerkleUniqueTokenId extends AbstractMerkleLeaf {
	private static final long IDENTITY_CODE_SERIAL_NUM_MASK = (1L << 32) - 1;
	private static final long IDENTITY_CODE_TOKEN_NUM_MASK = IDENTITY_CODE_SERIAL_NUM_MASK << 32;

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x52dd6afda193e8bcL;

	private long nftCode;

	public MerkleUniqueTokenId() {
		/* No-op. */
	}

	/**
	 * @param tokenId
	 * 		The underlying token id.
	 * @param serialNumber
	 * 		Represents the serial num of the token.
	 */
	public MerkleUniqueTokenId(
			EntityId tokenId,
			long serialNumber
	) {
		this.nftCode = nftCodeFrom(tokenId.num(), serialNumber);
	}

	public static MerkleUniqueTokenId fromNftId(NftId id) {
		return new MerkleUniqueTokenId(nftCodeFrom(id.num(), id.serialNo()));
	}

	/**
	 * Gives a "compressed" code to identify this unique token id.
	 *
	 * @return the code for this unique token id
	 */
	public long identityCode() {
		return nftCode;
	}

	public static MerkleUniqueTokenId fromIdentityCode(long code) {
		return new MerkleUniqueTokenId(code);
	}

	/* --- Object --- */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleUniqueTokenId.class != o.getClass()) {
			return false;
		}

		var that = (MerkleUniqueTokenId) o;

		return this.nftCode == that.nftCode;
	}

	@Override
	public int hashCode() {
		return (int) MiscUtils.perm64(nftCode);
	}


	/* --- Bean --- */
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(MerkleUniqueTokenId.class)
				.add("tokenId", tokenId().toAbbrevString())
				.add("serialNumber", serialNumber())
				.toString();
	}

	public EntityId tokenId() {
		return EntityId.fromIdentityCode(codeFromNum((nftCode & IDENTITY_CODE_TOKEN_NUM_MASK) >>> 32));
	}

	public long serialNumber() {
		return nftCode & IDENTITY_CODE_SERIAL_NUM_MASK;
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
	public void deserialize(SerializableDataInputStream in, int i) throws IOException {
		nftCode = in.readLong();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeLong(nftCode);
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleUniqueTokenId copy() {
		return new MerkleUniqueTokenId(this.nftCode);
	}

	private MerkleUniqueTokenId(long nftCode) {
		this.nftCode = nftCode;
	}

	static long nftCodeFrom(long num, long serialNo) {
		assertValid(num);
		assertValid(serialNo);
		return (num << 32) | serialNo;
	}
}
