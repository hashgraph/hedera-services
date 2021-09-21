package com.hedera.services.state.merkle;

import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;

/**
 * A key-value store with {@link FileID} keys and {@code byte[]} values. Used to accumulate
 * the contents of special files used in a network software upgrade.
 *
 * Because this leaf will change only during very small and infrequent windows, we can get
 * away with a very naive implementation of the {@link MerkleNode#copy()} contract. Each copy
 * keeps its own map of file contents; and when a file's bytes change in the mutable copy, it
 * updates that map with a completely new {@code byte[]}.
 */
public class MerkleSpecialFiles extends AbstractMerkleLeaf {
	public static final long CLASS_ID = 0x1608d4b49c28983aL;
	public static final int CURRENT_VERSION = 1;

	private final Map<FileID, byte[]> hashCache;
	private final Map<FileID, byte[]> fileContents;

	public MerkleSpecialFiles() {
		this.hashCache = new HashMap<>();
		this.fileContents = new LinkedHashMap<>();
	}

	public MerkleSpecialFiles(MerkleSpecialFiles that) {
		hashCache = new HashMap<>(that.hashCache);
		fileContents = new LinkedHashMap<>(that.fileContents);
	}

	/**
	 * Checks if the current contents of the given file match the given SHA-384 hash.
	 *
	 * @param fid the id of the file to check
	 * @param sha384Hash the candidate hash
	 * @return if the given file's contents match the given hash
	 */
	public synchronized boolean hashMatches(FileID fid, byte[] sha384Hash) {
		if (!fileContents.containsKey(fid)) {
			return false;
		}
		return Arrays.equals(sha384Hash, hashOfKnown(fid));
	}

	/**
	 * Gets the contents of the given file.
	 *
	 * @param fid the id of the file to get
	 * @return the file's contents
	 */
	public synchronized byte[] get(FileID fid) {
		return fileContents.get(fid);
	}

	/**
	 * Checks if the given file exists.
	 *
	 * @param fid the id of a file to check existence of
	 * @return if the file exixts
	 */
	public synchronized boolean contains(FileID fid) {
		return fileContents.containsKey(fid);
	}

	/**
	 * Appends the given bytes to the contents of the requested file. (Or, if the file
	 * does not yet exist, creates it with the given contents.)
	 *
	 * @param fid the id of the file to append to
	 * @param extraContents the contents to append
	 */
	public synchronized void append(FileID fid, byte[] extraContents) {
		throwIfImmutable();
		final var oldContents = fileContents.get(fid);
		if (oldContents == null) {
			update(fid, extraContents);
			return;
		}
		final var newLen = oldContents.length + extraContents.length;
		final var newContents = Arrays.copyOf(oldContents, newLen);
		System.arraycopy(extraContents, 0, newContents, oldContents.length, extraContents.length);
		fileContents.put(fid, newContents);
		hashCache.remove(fid);
	}

	/**
	 * Sets the contents of the requested file to the given bytes.
	 *
	 * @param fid the id of the file to set contents of
	 * @param newContents the new contents
	 */
	public synchronized void update(FileID fid, byte[] newContents) {
		throwIfImmutable();
		fileContents.put(fid, newContents);
		hashCache.remove(fid);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized MerkleSpecialFiles copy() {
		setImmutable(true);
		return new MerkleSpecialFiles(this);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void deserialize(SerializableDataInputStream in, int i) throws IOException {
		var numFiles = in.readInt();
		while (numFiles-- > 0) {
			final var fidNum = in.readLong();
			final var contents = in.readByteArray(Integer.MAX_VALUE);
			fileContents.put(STATIC_PROPERTIES.scopedFileWith(fidNum), contents);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeInt(fileContents.size());
		for (final var entry : fileContents.entrySet()) {
			final var fid = entry.getKey();
			final var contents = entry.getValue();
			out.writeLong(fid.getFileNum());
			out.writeByteArray(contents);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}

	private byte[] hashOfKnown(FileID fid) {
		return hashCache.computeIfAbsent(fid, missingFid ->
				CryptoFactory.getInstance().digestSync(fileContents.get(missingFid)).getValue());
	}

	/* --- Only used by unit tests --- */
	Map<FileID, byte[]> getHashCache() {
		return hashCache;
	}

	Map<FileID, byte[]> getFileContents() {
		return fileContents;
	}
}