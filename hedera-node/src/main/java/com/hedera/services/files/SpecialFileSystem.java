package com.hedera.services.files;

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleNode;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;

import static com.hedera.services.legacy.logic.ApplicationConstants.SPECIAL_FILESYSTEM_DIR;
import static com.swirlds.common.CommonUtils.hex;

public class SpecialFileSystem extends AbstractMerkleNode implements MerkleLeaf, FastCopyable<SpecialFileSystem> {
	private final static int HASH_BYTES = 48;
	private final static int MERKLE_VERSION = 1;
	private static final long RUNTIME_CONSTRUCTABLE_ID = 0xd8a59882c746d0a3L;
	private static final Logger log = LogManager.getLogger(SpecialFileSystem.class);

	// Map of fileID and file hash bytes
	HashMap<FileID, byte[]> fileMap = new HashMap<>();
	private String fileSystemLocation;

	public SpecialFileSystem() {
		fileSystemLocation = "empty";
	}

	public SpecialFileSystem(AccountID nodeAccountID) {
		// Create empty file 0.0.150
		FileID fid150 = FileID.newBuilder()
				.setFileNum(ApplicationConstants.UPDATE_FEATURE_FILE_ACCOUNT_NUM)
				.setRealmNum(ApplicationConstants.DEFAULT_FILE_REALM)
				.setShardNum(ApplicationConstants.DEFAULT_FILE_SHARD).build();
		this.fileSystemLocation = EntityIdUtils.asLiteralString(nodeAccountID);

		createEmptyIfNotExist(fid150);
		invalidateHash();
	}

	public SpecialFileSystem(HashMap<FileID, byte[]> fileMap, String accountIDStr) {
		this.fileMap = (HashMap<FileID, byte[]>) fileMap.clone();
		this.fileSystemLocation = accountIDStr;
		invalidateHash();
	}

	public HashMap<FileID, byte[]> getFileMap() {
		return fileMap;
	}

	public byte[] getFileHash(FileID fileID) {
		byte[] fileBytes = getFileContent(fileID);
		try {
			return MessageDigest.getInstance("SHA-384").digest(fileBytes);
		} catch (NoSuchAlgorithmException e) {
			log.error("Error when get hash of file {}", fileID);
			return null;
		}
	}

	private void createEmptyIfNotExist(FileID fileID) {
		File testFile = new File(SPECIAL_FILESYSTEM_DIR + fileSystemLocation + File.separator + fileIDtoDotString(fileID));
		if (testFile.exists()) {
			try {
				byte[] hash = MessageDigest.getInstance("SHA-384").digest(getFileContent(fileID));
				fileMap.put(fileID, hash);
				log.info("File {} already exists, loading its hash {}", fileID, hex(hash));
			} catch (NoSuchAlgorithmException e) {
				log.error("Error when calculating hash of file {}", fileID);
			}
		} else {
			log.info("Creating empty File {}", fileID);
			put(fileID, new byte[0]);
		}
	}

	private static String fileIDtoDotString(FileID fileID) {
		return "File" + fileID.getShardNum() + "." + fileID.getRealmNum() + "." + fileID.getFileNum();
	}

	public synchronized byte[] getFileContent(FileID fileID) {
		try {
			return FileUtils.readFileToByteArray(
					new File(SPECIAL_FILESYSTEM_DIR + fileSystemLocation + File.separator + fileIDtoDotString(fileID)));
		} catch (IOException e) {
			log.error("{} Error when reading fileID {} from local filesystem", fileID, e);
			return new byte[0];
		}
	}

	public synchronized void put(FileID fileID, byte[] content) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-384").digest(content);
			log.info("New file size {} with hash {}", content.length, hex(hash));
			fileMap.put(fileID, hash);
			FileUtils.writeByteArrayToFile(
					new File(SPECIAL_FILESYSTEM_DIR + fileSystemLocation + File.separator + fileIDtoDotString(fileID)),
					content);
			invalidateHash();
		} catch (IOException e) {
			log.error("Error when writing fileID {} to local filesystem", fileID, e);
		} catch (NoSuchAlgorithmException e) {
			log.error("Error when calculating hash of file {}", fileID);
		}
	}

	public boolean isSpeicalFileID(FileID fileID) {
		return fileMap.containsKey(fileID);
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		int mapSize = in.readInt();
		for (int i = 0; i < mapSize; i++) {
			long realmID = in.readLong();
			long shardID = in.readLong();
			long fileNum = in.readLong();
			FileID fileID = FileID.newBuilder()
					.setFileNum(fileNum)
					.setRealmNum(realmID)
					.setShardNum(shardID).build();
			byte[] hash = in.readByteArray(HASH_BYTES);
			log.info("Read file {} with hash {}", fileID, hex(hash));
			fileMap.put(fileID, hash);
		}
		invalidateHash();
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		out.writeInt(fileMap.size());
		fileMap.keySet()
				.stream()
				.sorted(HederaLedger.FILE_ID_COMPARATOR)
				.forEach(fileID -> {
					try {
						out.writeLong(fileID.getShardNum());
						out.writeLong(fileID.getRealmNum());
						out.writeLong(fileID.getFileNum());
						out.writeByteArray(fileMap.get(fileID));
					} catch (IOException e) {
						log.error("Error when serialize {}", fileID);
					}
				});
	}

	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public SpecialFileSystem copy() {
		SpecialFileSystem newCopy = new SpecialFileSystem(fileMap, this.fileSystemLocation);
		return newCopy;
	}

	@Override
	public void delete() {

	}

	public void verifyHash() {
		for (FileID fileID : fileMap.keySet()) {
			//Verify if the file system has the same hash loaded form state
			byte[] hash = fileMap.get(fileID);
			byte[] fileSystemHash = getFileHash(fileID);
			if (!Arrays.equals(hash, fileSystemHash)) {
				log.error("Error: File hash from state does not match file system, from state: {}", hex(hash));
				log.error("Error: File hash from state does not match file system, from file : {}",
						hex(fileSystemHash));
			}
		}
	}
}