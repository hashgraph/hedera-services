package com.hedera.services.state.merkle;

import com.google.common.base.MoreObjects;
import com.hedera.services.ledger.HederaLedger;
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.hedera.services.legacy.proto.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.services.utils.EntityIdUtils.asLiteralString;
import static com.swirlds.common.CommonUtils.hex;
import static org.apache.commons.io.FileUtils.readFileToByteArray;
import static org.apache.commons.io.FileUtils.writeByteArrayToFile;

/**
 * Save some special system files in local file system instead of database to improve access efficiency
 * File contents locate on local file system, but file hashes save in memory using a hash map
 */
public class MerkleDiskFs extends AbstractMerkleLeaf {
	static Logger log = LogManager.getLogger(MerkleDiskFs.class);

	private static final long RUNTIME_CONSTRUCTABLE_ID = 0xd8a59882c746d0a3L;
	private static final String UNKNOWN_PATH_SEGMENT = null;

	static final byte[] MISSING_CONTENT = new byte[0];

	static final int HASH_BYTES = 48;
	static final int MERKLE_VERSION = 1;

	private String fsBaseDir = UNKNOWN_PATH_SEGMENT;
	private String fsNodeScopedDir = UNKNOWN_PATH_SEGMENT;
	private Map<FileID, byte[]> fileHashes = new HashMap<>();

	/* --- RuntimeConstructable --- */
	public MerkleDiskFs() {
	}

	public MerkleDiskFs(String fsBaseDir, String fsNodeScopedDir) {
		this.fsBaseDir = fsBaseDir;
		this.fsNodeScopedDir = fsNodeScopedDir;
	}

	public MerkleDiskFs(Map<FileID, byte[]> fileHashes, String fsBaseDir, String fsNodeScopedDir) {
		this.fsBaseDir = fsBaseDir;
		this.fileHashes = fileHashes;
		this.fsNodeScopedDir = fsNodeScopedDir;
	}

	@SuppressWarnings("unchecked")
	public MerkleDiskFs copy() {
		Map<FileID, byte[]> fileHashesCopy = fileHashes.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, HashMap::new));
		return new MerkleDiskFs(fileHashesCopy, fsBaseDir, fsNodeScopedDir);
	}

	public void setFsBaseDir(String fsBaseDir) {
		this.fsBaseDir = fsBaseDir;
	}

	public void setFsNodeScopedDir(String fsNodeScopedDir) {
		this.fsNodeScopedDir = fsNodeScopedDir;
	}

	public void checkHashesAgainstDiskContents() {
		for (FileID fid : fileHashes.keySet()) {
			byte[] expectedHash = fileHashes.get(fid);
			byte[] actualHash = diskContentHash(fid);
			if (!Arrays.equals(expectedHash, actualHash)) {
				log.error(
						"State hash doesn't disk hash for content of '{}'!\n  State :: {}\n  Disk  :: {}",
						asLiteralString(fid),
						hex(expectedHash),
						hex(actualHash));
			}
		}
	}

	public byte[] diskContentHash(FileID fid) {
		return noThrowSha384HashOf(contentsOf(fid));
	}

	public synchronized byte[] contentsOf(FileID fid) {
		try {
			return readFileToByteArray(new File(pathToContentsOf(fid)));
		} catch (IOException e) {
			log.error("Error reading '%s' @ %s!", asLiteralString(fid), pathToContentsOf(fid), e);
			return MISSING_CONTENT;
		}
	}

	public synchronized void put(FileID fid, byte[] content) {
		try {
			byte[] hash = noThrowSha384HashOf(content);
			fileHashes.put(fid, hash);
			writeByteArrayToFile(new File(pathToContentsOf(fid)), content);
			log.info("Updated '{}' with {} bytes; new hash :: {}", asLiteralString(fid), content.length, hex(hash));
			invalidateHash();
		} catch (IOException e) {
			log.error(
					"Error writing new contents for '{}' to disk @ {}!",
					asLiteralString(fid),
					pathToContentsOf(fid),
					e);
		}
	}

	public boolean contains(FileID fileID) {
		return fileHashes.containsKey(fileID);
	}

	/* --- SelfSerializable --- */
	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		int numSavedHashes = in.readInt();
		for (int i = 0; i < numSavedHashes; i++) {
			var fid = FileID.newBuilder()
					.setShardNum(in.readLong())
					.setRealmNum(in.readLong())
					.setFileNum(in.readLong())
					.build();
			byte[] hash = in.readByteArray(HASH_BYTES);
			fileHashes.put(fid, hash);
			log.info("Recovered file '{}' with hash :: {}", asLiteralString(fid), hex(hash));
		}
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeInt(fileHashes.size());
		fileHashes.keySet()
				.stream()
				.sorted(HederaLedger.FILE_ID_COMPARATOR)
				.forEach(fid -> {
					try {
						out.writeLong(fid.getShardNum());
						out.writeLong(fid.getRealmNum());
						out.writeLong(fid.getFileNum());
						out.writeByteArray(fileHashes.get(fid));
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				});
	}

	/* --- MerkleNode --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	/* --- Object --- */
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleDiskFs.class != o.getClass()) {
			return false;
		}
		MerkleDiskFs that = (MerkleDiskFs) o;
		return Objects.equals(this.fsBaseDir, that.fsBaseDir) &&
				Objects.equals(this.fsNodeScopedDir, that.fsNodeScopedDir) &&
				allFileHashesMatch(this.fileHashes, that.fileHashes);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileHashes, fsBaseDir, fsNodeScopedDir);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(MerkleDiskFs.class)
				.add("baseDir", fsBaseDir)
				.add("nodeScopedDir", fsNodeScopedDir)
				.add("fileHashes", readableFileHashes())
				.toString();
	}

	private String readableFileHashes() {
		var sb = new StringBuilder("[");
		var content = fileHashes.keySet()
				.stream()
				.sorted(HederaLedger.FILE_ID_COMPARATOR)
				.map(fid -> asLiteralString(fid) + " :: " + hex(fileHashes.get(fid)))
				.collect(Collectors.joining(", "));
		sb.append(content);
		return sb.append("]").toString();
	}

	private boolean allFileHashesMatch(Map<FileID, byte[]> a, Map<FileID, byte[]> b) {
		if (a.keySet().equals(b.keySet())) {
			for (FileID fid : a.keySet()) {
				if (!Arrays.equals(a.get(fid), b.get(fid))) {
					return false;
				}
			}
			return true;
		} else {
			return false;
		}
	}

	private String pathToContentsOf(FileID fid) {
		return String.format(
				"%s%sFile%s",
				separatorSuffixed(fsBaseDir),
				separatorSuffixed(fsNodeScopedDir),
				asLiteralString(fid));
	}

	private String separatorSuffixed(String dir) {
		return dir.endsWith(File.separator)	? dir : (dir + File.separator);
	}

}