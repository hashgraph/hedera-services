package com.hedera.services.state.blob;

/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;

/**
 * Blob Storage that stores each blob in a file using Java NIO. The file name is just the key base64
 * encoded.
 */
public class FileBlobStorage {

	private static final FileBlobStorage instance = new FileBlobStorage();
	// TODO get it from the config
	private File dbPath = new File("data/diskFs/blobs");

	volatile long seqNumber;

	// TODO make it similar to MerkleDiskFs?
	public static FileBlobStorage getInstance() {
		return instance;
	}

	public FileBlobStorage() {
		if (!dbPath.exists()) dbPath.mkdirs();
	}

	public void close() {}

	public byte[] get(long fileId) {
		File file = new File(dbPath, String.valueOf(fileId));
		if(file.exists()) {
			try {
				return Files.readAllBytes(file.toPath());
			} catch (IOException e) {
				throw new IllegalStateException("Failed to get value from file storage with key["+ fileId +"]",e);
			}
		} else {
			throw new IllegalStateException("Failed to find data with key [" + fileId + "]");
		}
	}

	public long put(byte[] bytes) {
		long id = getAndIncrementSeqNum();
		File file = new File(dbPath, String.valueOf(id));
		try {
			Files.write(file.toPath(), bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
		} catch (IOException e) {
			throw new IllegalStateException("Failed to write value to database with key[" + id + "] in file [" + file + "]",e);
		}

		return id;
	}

	public void modify(long id, byte[] bytes) {
		File file = new File(dbPath, String.valueOf(id));
		if(file.exists()) {
			try {
				Files.write(file.toPath(), bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
			} catch (IOException e) {
				throw new IllegalStateException("Failed to write value to database with key[" + id + "] in file [" + file + "]",e);
			}
		}
	}

	public void delete(long id) {
		File file = new File(dbPath, String.valueOf(id));
		if(file.exists()) {
			file.delete();
		}
	}

	private synchronized long getAndIncrementSeqNum() {
		return seqNumber++;
	}
}
