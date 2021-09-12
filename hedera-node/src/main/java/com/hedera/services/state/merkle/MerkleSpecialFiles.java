package com.hedera.services.state.merkle;

import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.crypto.CryptoFactory;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;

public class MerkleSpecialFiles extends AbstractMerkleLeaf {
	private static final long CLASS_ID = 0x1608d4b49c28983aL;
	private static final int CURRENT_VERSION = 1;

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

	public synchronized boolean hashMatches(FileID fid, byte[] sha384Hash) {
		if (!fileContents.containsKey(fid)) {
			return false;
		}
		return Arrays.equals(sha384Hash, hashOfKnown(fid));
	}

	private byte[] hashOfKnown(FileID fid) {
		return hashCache.computeIfAbsent(fid, missingFid ->
				CryptoFactory.getInstance().digestSync(fileContents.get(missingFid)).getValue());
	}

	public synchronized byte[] get(FileID fid) {
		return fileContents.get(fid);
	}

	public synchronized boolean contains(FileID fid) {
		return fileContents.containsKey(fid);
	}

	public synchronized void append(FileID fid, byte[] extraContents) {
		throwIfImmutable();
		/* To BB or not to BB? */
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

	public synchronized void update(FileID fid, byte[] newContents) {
		throwIfImmutable();
		fileContents.put(fid, newContents);
		hashCache.remove(fid);
	}

	@Override
	public synchronized AbstractMerkleLeaf copy() {
		setImmutable(true);
		return new MerkleSpecialFiles(this);
	}

	@Override
	public synchronized void deserialize(SerializableDataInputStream in, int i) throws IOException {
		var numFiles = in.readInt();
		while (numFiles-- > 0) {
			final var fidNum = in.readLong();
			final var contents = in.readByteArray(Integer.MAX_VALUE);
			fileContents.put(STATIC_PROPERTIES.scopedFileWith(fidNum), contents);
		}
	}

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

	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	@Override
	public int getVersion() {
		return CURRENT_VERSION;
	}

	/* --- Only used by unit tests --- */
	Map<FileID, byte[]> getHashCache() {
		return hashCache;
	}
}
