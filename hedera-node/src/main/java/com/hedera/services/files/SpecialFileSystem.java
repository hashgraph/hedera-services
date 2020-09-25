package com.hedera.services.files;

import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hederahashgraph.api.proto.java.FileID;

import java.util.HashMap;
import java.util.Map;

public class SpecialFileSystem
{
	Map<FileID, byte[]> fileMap = new HashMap<>();

	public SpecialFileSystem()
	{
		// Create empty file 0.0.150
		FileID fid = FileID.newBuilder().setFileNum(ApplicationConstants.UPDATE_FEATURE_FILE_ACCOUNT_NUM)
				.setRealmNum(ApplicationConstants.DEFAULT_FILE_REALM)
				.setShardNum(ApplicationConstants.DEFAULT_FILE_SHARD).build();
		fileMap.put(fid, new byte[0]);
	}

	public void fileAppend(FileID fileID) {

	}

	public byte[] get(FileID fileID) {
		return fileMap.get(fileID);
	}

	public void put(FileID fileID, byte[] content) {
		fileMap.put(fileID, content);
	}

	public void remove(FileID fileID) {
		//Don't delete only clear the content
		fileMap.put(fileID, new byte[0]);
	}
	public boolean isSpeicalFileID(FileID fileID) {
		return fileMap.containsKey(fileID);
	}
}