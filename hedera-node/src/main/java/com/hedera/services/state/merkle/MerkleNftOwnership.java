package com.hedera.services.state.merkle;

import com.google.common.base.MoreObjects;
import com.hedera.services.state.submerkle.EntityId;
import com.swirlds.common.FCMValue;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;

public class MerkleNftOwnership extends AbstractMerkleLeaf implements FCMValue {
	private static final byte[] MISSING_SERIAL_NO = new byte[0];

	static final int RELEASE_0140_VERSION = 1;

	static final int MERKLE_VERSION = RELEASE_0140_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x1954c68f44cb9498L;

	public static final int NUM_NFT_ID_BYTES = 32;

	private byte[] serialNo = MISSING_SERIAL_NO;
	private EntityId nftType = MISSING_ENTITY_ID;

	public MerkleNftOwnership() {
	}

	public MerkleNftOwnership(EntityId nftType, byte[] serialNo) {
		this.nftType = nftType;
		this.serialNo = serialNo;
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
		nftType = in.readSerializable();
		serialNo = in.readByteArray(NUM_NFT_ID_BYTES);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeSerializable(nftType, true);
		out.writeByteArray(serialNo);
	}

	/* --- Object --- */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleNftOwnership.class != o.getClass()) {
			return false;
		}

		var that = (MerkleNftOwnership) o;
		return Objects.equals(this.nftType, that.nftType) &&
				Arrays.equals(this.serialNo, that.serialNo);
	}

	@Override
	public int hashCode() {
		return Objects.hash(nftType, serialNo);
	}

	/* --- FastCopyable --- */
	@Override
	public MerkleNftOwnership copy() {
		return this;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("serialNo", new String(serialNo))
				.add("nftType", Optional.ofNullable(nftType).map(EntityId::toAbbrevString).orElse("<N/A>"))
				.toString();
	}
}
