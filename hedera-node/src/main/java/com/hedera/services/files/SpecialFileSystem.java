package com.hedera.services.files;

import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hederahashgraph.api.proto.java.FileID;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class SpecialFileSystem {
	private static final Logger log = LogManager.getLogger(SpecialFileSystem.class);
	private static String fileSystemLocation = "specialFiles/";
	Set<FileID> fileSet = new HashSet<>();

	public SpecialFileSystem() {
		// Create empty file 0.0.150
		FileID fid150 = FileID.newBuilder()
				.setFileNum(ApplicationConstants.UPDATE_FEATURE_FILE_ACCOUNT_NUM)
				.setRealmNum(ApplicationConstants.DEFAULT_FILE_REALM)
				.setShardNum(ApplicationConstants.DEFAULT_FILE_SHARD).build();

		createEmptyIfNotExist(fid150);
	}

	private void createEmptyIfNotExist(FileID fileID) {
		File testFile = new File(fileSystemLocation + fileIDtoDotString(fileID));
		if (testFile.exists()) {
			log.info("File {} already exists", fileID);
		} else {
			log.info("Creating empty File {}", fileID);
			put(fileID, new byte[0]);
		}
	}

	private static String fileIDtoDotString(FileID fileID) {
		return "File" + fileID.getShardNum() + "." + fileID.getRealmNum() + "." + fileID.getFileNum();
	}
	public byte[] get(FileID fileID) {
		try {
			return FileUtils.readFileToByteArray(new File(fileSystemLocation + fileIDtoDotString(fileID)));
		} catch (IOException e) {
			log.error("{} Error when reading fileID {} from local filesystem", fileID, e);
			return new byte[0];
		}
	}

	public void put(FileID fileID, byte[] content) {
		fileSet.add(fileID);
		try {
			FileUtils.writeByteArrayToFile(new File(fileSystemLocation + fileIDtoDotString(fileID)), content);
		} catch (IOException e) {
			log.error("{} Error when writing fileID {} to local filesystem", fileID, e);
		}
	}

	public boolean isSpeicalFileID(FileID fileID) {
		return fileSet.contains(fileID);
	}
}