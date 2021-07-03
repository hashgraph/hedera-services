package com.hedera.services.state.merkle;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.IOException;
import java.util.Arrays;

import static java.util.Arrays.copyOfRange;

public class MerkleBatchedUniqTokens extends AbstractMerkleLeaf {
	public static final int MAX_METADATA_SIZE = 100;
	public static final int TOKENS_IN_BATCH = 1_000;

	static final int MERKLE_VERSION = 1;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x13bdd42fa7fa9751L;

	/* Immutable elements (once minted) */
	private byte[] metadata = new byte[TOKENS_IN_BATCH * MAX_METADATA_SIZE];
	private RichInstant[] creationTimes = new RichInstant[TOKENS_IN_BATCH];

	/* Mutable elements */
	private int mintedSoFar;
	private boolean[] burned = new boolean[TOKENS_IN_BATCH];
	private EntityId[] owners = new EntityId[TOKENS_IN_BATCH];

	private boolean preWriteCopyDone = false;

	public MerkleBatchedUniqTokens() {
		/* RuntimeConstructable */
	}

	public MerkleBatchedUniqTokens(
			byte[] metadata,
			RichInstant[] creationTimes,
			int mintedSoFar,
			boolean[] burned,
			EntityId[] owners
	) {
		this.metadata = metadata;
		this.creationTimes = creationTimes;
		this.mintedSoFar = mintedSoFar;
		this.burned = burned;
		this.owners = owners;
	}

	public void mintNextWith(EntityId owner, RichInstant mintTime, byte[] mintMeta) {
		throwIfImmutable();
		if (mintedSoFar == TOKENS_IN_BATCH) {
			throw new IllegalStateException("Batch is complete, cannot mint w/ meta " + new String(mintMeta));
		}

		ensurePreWriteCopy();
		owners[mintedSoFar] = owner;
		System.arraycopy(mintMeta, 0, metadata, mintedSoFar * MAX_METADATA_SIZE, MAX_METADATA_SIZE);
		creationTimes[mintedSoFar] = mintTime;
		mintedSoFar++;
	}

	public void burn(int inBatchLoc) {
		throwIfImmutable();
		ensurePreWriteCopy();
		burned[inBatchLoc] = true;
	}

	public void setOwner(int inBatchLoc, EntityId newOwner) {
		throwIfImmutable();
		ensurePreWriteCopy();
		owners[inBatchLoc] = newOwner;
	}

	public MerkleUniqueToken get(int inBatchLoc) {
		return new MerkleUniqueToken(
				owners[inBatchLoc],
				copyOfRange(metadata, inBatchLoc * MAX_METADATA_SIZE, (inBatchLoc + 1) * MAX_METADATA_SIZE),
				creationTimes[inBatchLoc]);
	}

	public boolean isMinted(int inBatchLoc) {
		return mintedSoFar > inBatchLoc && !burned[inBatchLoc];
	}

	public boolean isBurned(int inBatchLoc) {
		return burned[inBatchLoc];
	}

	public EntityId getOwner(int inBatchLoc) {
		return owners[inBatchLoc];
	}

	public int getMintedSoFar() {
		return mintedSoFar;
	}

	public boolean batchIsFullyBurned() {
		int numBurned = 0;
		for (int i = 0; i < mintedSoFar; i++) {
			if (burned[i]) {
				numBurned++;
			}
		}
		return numBurned == TOKENS_IN_BATCH;
	}

	@Override
	public MerkleBatchedUniqTokens copy() {
		setImmutable(true);
		return new MerkleBatchedUniqTokens(metadata, creationTimes, mintedSoFar, burned, owners);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		throw new AssertionError("Not implemented!");
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeInt(mintedSoFar);
		out.writeByteArray(metadata);
		out.writeSerializableArray(creationTimes, true, true);
		out.writeSerializableArray(owners, true, true);
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	private void ensurePreWriteCopy() {
		if (preWriteCopyDone) {
			return;
		}

		final byte[] newMetadata = Arrays.copyOf(metadata, TOKENS_IN_BATCH * MAX_METADATA_SIZE);
		final RichInstant[] newCreationTimes = Arrays.copyOf(creationTimes, TOKENS_IN_BATCH);
		final boolean[] newBurned = Arrays.copyOf(burned, TOKENS_IN_BATCH);
		final EntityId[] newOwners = Arrays.copyOf(owners, TOKENS_IN_BATCH);

		metadata = newMetadata;
		creationTimes = newCreationTimes;
		burned = newBurned;
		owners = newOwners;

		preWriteCopyDone = true;
	}
}
