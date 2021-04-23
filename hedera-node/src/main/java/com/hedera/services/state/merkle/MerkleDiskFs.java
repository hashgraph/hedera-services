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
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleExternalLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hedera.services.ledger.HederaLedger.FILE_ID_COMPARATOR;
import static com.hedera.services.legacy.proto.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.services.utils.EntityIdUtils.asLiteralString;
import static com.swirlds.common.CommonUtils.hex;

/**
 * Save some special system files on the local file system instead of database to improve access efficiency.
 *
 * All that is kept in memory is a map from {@code FileID} to the SHA-384 hash of the current contents.
 *
 * <b>IMPORTANT:</b> If running multiple nodes in a process, all of their disk-based file systems will use
 * the same path for a given file! There is no simple way to "scope" the path by node id without creating
 * problems during reconnect.
 *
 * So if testing the update feature locally, please use a single node; or use multiple nodes in a
 * Docker Compose network. Otherwise, updates to file 0.0.150 will be hopelessly interleaved and failure
 * is essentially guaranteed.
 */
public class MerkleDiskFs extends AbstractMerkleLeaf implements MerkleExternalLeaf {
	private static final Logger log = LogManager.getLogger(MerkleDiskFs.class);

	private static final long RUNTIME_CONSTRUCTABLE_ID = 0xd8a59882c746d0a3L;

	static final byte[] MISSING_CONTENT = new byte[0];

	static final int HASH_BYTES = 48;
	static final int MAX_FILE_BYTES = 1_024 * 1_024 * 1_024;
	static final int MERKLE_VERSION = 1;

	private Map<FileID, byte[]> fileHashes = new HashMap<>();
	private ThrowingBytesGetter bytesHelper = p -> FileUtils.readFileToByteArray(p.toFile());
	private ThrowingBytesWriter writeHelper = (p, c) -> FileUtils.writeByteArrayToFile(p.toFile(), c);

	public static final String DISK_FS_ROOT_DIR = "data/diskFs/";

	/* --- RuntimeConstructable --- */
	public MerkleDiskFs() {
		setHashFromContents();
	}

	public MerkleDiskFs(Map<FileID, byte[]> fileHashes) {
		this.fileHashes = fileHashes;
		setHashFromContents();
	}

	public MerkleDiskFs copy() {
		Map<FileID, byte[]> fileHashesCopy = fileHashes.entrySet()
				.stream()
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, HashMap::new));
		return new MerkleDiskFs(fileHashesCopy);
	}

	public void checkHashesAgainstDiskContents() {
		for (var entry : fileHashes.entrySet()) {
			var fid = entry.getKey();
			byte[] expectedHash = entry.getValue();
			byte[] actualHash = diskContentHash(fid);
			if (!Arrays.equals(expectedHash, actualHash)) {
				log.error(
						"State hash doesn't match disk hash for content of '{}'!\n  State :: {}\n  Disk  :: {}",
						asLiteralString(fid),
						hex(expectedHash),
						hex(actualHash));
			}
		}
	}

	public void migrateLegacyDiskFsFromV13LocFor(String fsBaseDir, String fsNodeScopedDir) {
		for (var fid : fileHashes.keySet()) {
			try {
				var legacyPath = pre0130PathToContentsOf(fid, fsBaseDir, fsNodeScopedDir);
				var f = legacyPath.toFile();
				byte[] contents = bytesHelper.allBytesFrom(legacyPath);
				writeHelper.allBytesTo(pathToContentsOf(fid), contents);
				f.delete();
			} catch (IOException e) {
				//TODO : Mark this log as an error in 0.15.0 if MerkleDiskFs is used for Update
				log.warn("Failed to migrate from legacy disk-based file system!", e);
				throw new UncheckedIOException(e);
			}
		}
		var nowEmptyLegacyDir = fsBaseDir + File.separator + fsNodeScopedDir;
		new File(nowEmptyLegacyDir).delete();
	}

	public byte[] diskContentHash(FileID fid) {
		return noThrowSha384HashOf(contentsOf(fid));
	}

	public synchronized byte[] contentsOf(FileID fid) {
		try {
			return bytesHelper.allBytesFrom(pathToContentsOf(fid));
		} catch (IOException e) {
			/* This is almost certainly not a recoverable failure; a file is probably missing from disk. */
			log.error("Not able to read '{}' @ {}!", asLiteralString(fid), pathToContentsOf(fid));
			throw new UncheckedIOException(e);
		}
	}

	public synchronized void put(FileID fid, byte[] contents) {
		try {
			byte[] hash = noThrowSha384HashOf(contents);
			writeHelper.allBytesTo(pathToContentsOf(fid), contents);
			log.info("Updated '{}' with {} bytes; new hash :: {}", asLiteralString(fid), contents.length, hex(hash));
			fileHashes.put(fid, hash);
			setHashFromContents();
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

	/* --- MerkleExternalLeaf --- */
	@Override
	public void serializeAbbreviated(SerializableDataOutputStream out) throws IOException {
		out.writeInt(fileHashes.size());
		serializeFidInfo(out, fileHashes::get);
	}

	@Override
	public void deserializeAbbreviated(SerializableDataInputStream in, Hash hash, int version) throws IOException {
		int numSavedHashes = in.readInt();
		for (int i = 0; i < numSavedHashes; i++) {
			var fid = FileID.newBuilder()
					.setShardNum(in.readLong())
					.setRealmNum(in.readLong())
					.setFileNum(in.readLong())
					.build();
			byte[] fileHash = in.readByteArray(HASH_BYTES);
			fileHashes.put(fid, fileHash);
			log.info("Recovered file '{}' with hash :: {}", asLiteralString(fid), hex(fileHash));
		}
		super.setHash(hash);
	}

	/* -- Hashable -- */
	@Override
	public void setHash(Hash hash) {
		/* No-op, hash is managed internally. */
	}

	private void setHashFromContents() {
		var baos = new ByteArrayOutputStream();
		try (SerializableDataOutputStream out = new SerializableDataOutputStream(baos)) {
			serializeFidInfo(out, fileHashes::get);
		} catch (IOException improbable) {
			throw new IllegalStateException(improbable);
		}
		try {
			baos.close();
			baos.flush();
		} catch (IOException improbable) {
			throw new IllegalStateException(improbable);
		}
		super.setHash(new Hash(noThrowSha384HashOf(baos.toByteArray())));
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
			byte[] contents = in.readByteArray(MAX_FILE_BYTES);
			writeHelper.allBytesTo(pathToContentsOf(fid), contents);
			byte[] fileHash = noThrowSha384HashOf(contents);
			fileHashes.put(fid, fileHash);
			log.info("Restored file '{}' with hash :: {}", asLiteralString(fid), hex(fileHash));
		}
		setHashFromContents();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeInt(fileHashes.size());
		serializeFidInfo(out, fid -> {
			return contentsOf(fid);
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
		return allFileHashesMatch(this.fileHashes, that.fileHashes);
	}

	@Override
	public int hashCode() {
		return Objects.hash(fileHashes);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(MerkleDiskFs.class)
				.add("fileHashes", readableFileHashes())
				.toString();
	}

	private String readableFileHashes() {
		var sb = new StringBuilder("[");
		sb.append(orderedFids()
				.map(fid -> asLiteralString(fid) + " :: " + hex(fileHashes.get(fid)))
				.collect(Collectors.joining(", ")));
		return sb.append("]").toString();
	}

	private boolean allFileHashesMatch(Map<FileID, byte[]> a, Map<FileID, byte[]> b) {
		if (a.keySet().equals(b.keySet())) {
			for (var aEntry : a.entrySet()) {
				if (!Arrays.equals(aEntry.getValue(), b.get(aEntry.getKey()))) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	Path pathToContentsOf(FileID fid) {
		return Paths.get(String.format("%sFile%s", separatorSuffixed(DISK_FS_ROOT_DIR), asLiteralString(fid)));
	}

	Path pre0130PathToContentsOf(FileID fid, String fsBaseDir, String fsNodeScopedDir) {
		return Paths.get(String.format(
				"%s%sFile%s",
				separatorSuffixed(fsBaseDir),
				separatorSuffixed(fsNodeScopedDir),
				asLiteralString(fid)));
	}

	private String separatorSuffixed(String dir) {
		return dir.endsWith(File.separator) ? dir : (dir + File.separator);
	}

	private void serializeFidInfo(
			SerializableDataOutputStream out,
			Function<FileID, byte[]> infoReprFn
	) {
		orderedFids().forEach(fid -> {
			try {
				out.writeLong(fid.getShardNum());
				out.writeLong(fid.getRealmNum());
				out.writeLong(fid.getFileNum());
				out.writeByteArray(infoReprFn.apply(fid));
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}

	private Stream<FileID> orderedFids() {
		return fileHashes.keySet().stream().sorted(FILE_ID_COMPARATOR);
	}

	@FunctionalInterface
	interface ThrowingBytesGetter {
		byte[] allBytesFrom(Path loc) throws IOException;
	}

	@FunctionalInterface
	interface ThrowingBytesWriter {
		void allBytesTo(Path loc, byte[] contents) throws IOException;
	}

	void setBytesHelper(ThrowingBytesGetter bytesHelper) {
		this.bytesHelper = bytesHelper;
	}

	void setWriteHelper(ThrowingBytesWriter writeHelper) {
		this.writeHelper = writeHelper;
	}

	ThrowingBytesGetter getBytesHelper() {
		return bytesHelper;
	}

	ThrowingBytesWriter getWriteHelper() {
		return writeHelper;
	}
}
