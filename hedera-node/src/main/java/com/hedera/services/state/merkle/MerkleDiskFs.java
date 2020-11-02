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
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleExternalLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleLeaf;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
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
 */
public class MerkleDiskFs extends AbstractMerkleLeaf implements MerkleExternalLeaf {
	static Logger log = LogManager.getLogger(MerkleDiskFs.class);

	private static final long RUNTIME_CONSTRUCTABLE_ID = 0xd8a59882c746d0a3L;

	private static final String UNKNOWN_PATH_SEGMENT = null;
	static final byte[] MISSING_CONTENT = new byte[0];

	static final int HASH_BYTES = 48;
	static final int MAX_FILE_BYTES = 1_024 * 1_024 * 1_024;
	static final int MERKLE_VERSION = 1;

	static ThrowingBytesGetter bytesHelper = Files::readAllBytes;

	private String fsBaseDir = UNKNOWN_PATH_SEGMENT;
	private String fsNodeScopedDir = UNKNOWN_PATH_SEGMENT;
	private Map<FileID, byte[]> fileHashes = new HashMap<>();

	/* --- RuntimeConstructable --- */
	public MerkleDiskFs() {
		setHashFromContents();
	}

	public MerkleDiskFs(String fsBaseDir, String fsNodeScopedDir) {
		this.fsBaseDir = fsBaseDir;
		this.fsNodeScopedDir = fsNodeScopedDir;
		setHashFromContents();
	}

	public MerkleDiskFs(Map<FileID, byte[]> fileHashes, String fsBaseDir, String fsNodeScopedDir) {
		this.fsBaseDir = fsBaseDir;
		this.fileHashes = fileHashes;
		this.fsNodeScopedDir = fsNodeScopedDir;
		setHashFromContents();
	}

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
						"State hash doesn't match disk hash for content of '{}'!\n  State :: {}\n  Disk  :: {}",
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
			return Files.readAllBytes(Paths.get(pathToContentsOf(fid)));
		} catch (IOException e) {
			log.error("Error reading '{}' @ {}!", asLiteralString(fid), pathToContentsOf(fid), e);
			return MISSING_CONTENT;
		}
	}

	public synchronized void put(FileID fid, byte[] contents) {
		try {
			byte[] hash = noThrowSha384HashOf(contents);
			Files.write(Paths.get(pathToContentsOf(fid)), contents);
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
			Files.write(Paths.get(pathToContentsOf(fid)), contents);
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
			try {
				return bytesHelper.allBytesFrom(Paths.get(pathToContentsOf(fid)));
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
		sb.append(orderedFids()
				.map(fid -> asLiteralString(fid) + " :: " + hex(fileHashes.get(fid)))
				.collect(Collectors.joining(", ")));
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
		}
		return false;
	}

	String pathToContentsOf(FileID fid) {
		return String.format(
				"%s%sFile%s",
				separatorSuffixed(fsBaseDir),
				separatorSuffixed(fsNodeScopedDir),
				asLiteralString(fid));
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
}
