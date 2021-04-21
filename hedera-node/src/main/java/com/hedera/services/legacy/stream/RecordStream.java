package com.hedera.services.legacy.stream;

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

import com.swirlds.common.crypto.DigestType;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

/**
 * This class is used for generating record stream v3 files
 */
@Deprecated
public class RecordStream {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger(RecordStream.class);

	public static final byte TYPE_SIGNATURE = 3;       // the file content signature, should not be hashed
	public static final byte TYPE_FILE_HASH = 4;       // next 48 bytes are hash384 of content of the file to be signed

	/**
	 * Read the FileHash from the record stream signature v3 file
	 *
	 * @param file .rcd_sig v3 file
	 * @return file hash byte array
	 */
	public static byte[] getFileHashFromSigFile(File file) {
		Pair<byte[], byte[]> pair = parseSigFile(file);
		if (pair == null) {
			return null;
		}
		return pair.getLeft();
	}

	/**
	 * Check if a file is a RecordStream signature file
	 *
	 * @param file
	 * @return
	 */
	public static boolean isRecordSigFile(File file) {
		return file.getName().endsWith(".rcd_sig");
	}

	/**
	 * Read the FileHash and the signature byte array contained in the signature file;
	 * return a pair of FileHash and signature
	 *
	 * @param file
	 * @return
	 */
	public static Pair<byte[], byte[]> parseSigFile(File file) {
		if (!file.getName().endsWith("_sig")) {
			log.info("{} is not a signature file", file);
			return null;
		}
		try (DataInputStream dis = new DataInputStream(new FileInputStream(file))) {
			byte[] fileHash = null;
			byte[] sig = null;
			while (dis.available() != 0) {
				byte typeDelimiter = dis.readByte();
				switch (typeDelimiter) {
					case TYPE_FILE_HASH:
						fileHash = new byte[48];
						dis.readFully(fileHash);
						break;
					case TYPE_SIGNATURE:
						int sigLength = dis.readInt();
						sig = new byte[sigLength];
						dis.readFully(sig);
						break;
					default:
						log.error("parseSigFile :: Unknown file delimiter {}",
								typeDelimiter);
				}
			}
			return Pair.of(fileHash, sig);
		} catch (IOException e) {
			log.error("readHashFromSigFile :: Fail to read Hash from {}. Exception: {}",
					file.getName(),
					e.getMessage());
			return null;
		}
	}

	/**
	 * Read the previous file hash from the last .rcd_sig file in the given directory
	 *
	 * @param directory the directory where stores record stream v3 files
	 * @return record stream v3 previous file hash byte array
	 */

	public static byte[] readPrevFileHash(String directory) {
		if (directory != null) {
			File dir = new File(directory);
			File[] files = dir.listFiles();
			if (files != null && files.length > 0) {
				Optional<File> lastSigFileOptional = Arrays.stream(files).filter(file -> isRecordSigFile(file))
						.max(Comparator.comparing(File::getName));
				if (lastSigFileOptional.isPresent()) {
					File lastSigFile = lastSigFileOptional.get();
					return getFileHashFromSigFile(lastSigFile);
				}
			}
		}
		log.info("readPrevFileHash: fail to load record stream Hash from {}, will return empty Hash",
				() -> directory);
		return new byte[DigestType.SHA_384.digestLength()];
	}
}

