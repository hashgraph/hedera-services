package com.hedera.services.state.merkle;

import com.hedera.services.state.merkle.internals.BlobKey;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import com.swirlds.common.merkle.utility.Keyed;

import java.io.IOException;

public class MerkleBlob extends AbstractMerkleLeaf implements Keyed<BlobKey> {
	private static final BlobKey.BlobType[] CHOICES = BlobKey.BlobType.values();
	private static final long CLASS_ID = 0x20920cf4ffd2c64cL;

	private byte[] data;
	private BlobKey blobKey;

	public MerkleBlob() {
		/* RuntimeConstructable */
	}

	public MerkleBlob(byte[] data) {
		this.data = data;
	}

	private MerkleBlob(final MerkleBlob that) {
		this.blobKey = that.blobKey;
		this.data = that.data;
	}

	@Override
	public MerkleBlob copy() {
		return new MerkleBlob(this);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		blobKey = new BlobKey(CHOICES[in.readInt()], in.readLong());
		data = in.readByteArray(Integer.MAX_VALUE);
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeInt(blobKey.getType().ordinal());
		out.writeLong(blobKey.getEntityNum());
		out.writeByteArray(data);
	}

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return 1;
	}

	@Override
	public BlobKey getKey() {
		return blobKey;
	}

	@Override
	public void setKey(BlobKey blobKey) {
		this.blobKey = blobKey;
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}
}
