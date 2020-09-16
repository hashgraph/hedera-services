package com.hedera.services.legacy.core.jproto;

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

import com.hederahashgraph.api.proto.java.FileGetInfoResponse.FileInfo;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hedera.services.legacy.exception.DeserializationException;
import com.hedera.services.legacy.exception.InvalidFileWACLException;
import com.hedera.services.legacy.exception.SerializationException;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.Serializable;

/**
 * Custom class for storing file metadata, as equivalent of FileInfo proto object.
 *
 * @author Hua Li Created on 2019-01-15
 */
public class JFileInfo implements Serializable {
	private static final long serialVersionUID = 1L;
	private boolean deleted = false; // flag indicating whether the file is deleted
	private JKey wacl = null; // file wacl as a JKey of type JKeyList
	private long expirationTimeSeconds;

	public long getExpirationTimeSeconds() {
		return expirationTimeSeconds;
	}

	public void setExpirationTimeSeconds(long expirationTimeSeconds) {
		this.expirationTimeSeconds = expirationTimeSeconds;
	}

	public JFileInfo(boolean deleted, JKey wacl, long expirationTimeInSec) {
		this.deleted = deleted;
		this.wacl = wacl;
		this.expirationTimeSeconds = expirationTimeInSec;
	}

	/**
	 * Serialize this JFileInfo object.
	 */
	public byte[] serialize() throws SerializationException {
		return JFileInfoSerializer.serialize(this);
	}

	/**
	 * Deserializes bytes into a JFileInfo object.
	 *
	 * @param bytes
	 * 		JFileInfo object serialized bytes
	 * @throws DeserializationException
	 */
	public static JFileInfo deserialize(byte[] bytes) throws DeserializationException {
		DataInputStream stream = new DataInputStream(new ByteArrayInputStream(bytes));
		return JFileInfoSerializer.deserialize(stream);
	}

	/**
	 * Converts this JFileInfo object to a FileInfo object.
	 *
	 * @param fid
	 * 		the file ID
	 * @param size
	 * 		the size of the file
	 * @return converted FileInfo object
	 * @throws Exception
	 */
	public FileInfo convert(FileID fid, long size) throws Exception {
		Timestamp exp = Timestamp.newBuilder().setSeconds(expirationTimeSeconds).setNanos(0).build();
		FileInfo fi = FileInfo.newBuilder().setFileID(fid).setSize(size).setExpirationTime(exp)
				.setDeleted(deleted).setKeys(JKey.mapJKey(wacl).getKeyList()).build();
		return fi;
	}

	/**
	 * Converts a FileInfo object to a JFileInfo object. Note that the file ID and expiration time
	 * will not be kept in the target object.
	 *
	 * @param fi
	 * 		a FileInfo object to be converted
	 * @return converted JFileInfo object
	 * @throws Exception
	 */
	public static JFileInfo convert(FileInfo fi) throws Exception {
		JFileInfo jfi = new JFileInfo(fi.getDeleted(),
				JKey.mapKey(Key.newBuilder().setKeyList(fi.getKeys()).build()), fi.getExpirationTime().getSeconds());
		return jfi;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public JKey getWacl() {
		return wacl;
	}

	public void setWacl(JKey wacl) {
		this.wacl = wacl;
	}

	/**
	 * Converts wacl from a proto KeyList object to a Jkey.
	 *
	 * @param waclAsKeyList
	 * @return converted JKey object
	 * @throws InvalidFileWACLException
	 */
	public static JKey convertWacl(KeyList waclAsKeyList) throws InvalidFileWACLException {
		try {
			return JKey.mapKey(Key.newBuilder().setKeyList(waclAsKeyList).build());
		} catch (Exception e) {
			throw new InvalidFileWACLException("input wacl=" + waclAsKeyList, e);
		}
	}

	@Override
	public String toString() {
		return "<JFileInfo: deleted=" + deleted + ", expirationTimeSeconds=" + expirationTimeSeconds + ", wacl=" + wacl + ">";
	}
}
