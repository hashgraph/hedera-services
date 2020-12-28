package com.hedera.services.legacy.stream;

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

import com.google.common.primitives.Ints;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.legacy.config.PropertiesLoader;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.Platform;
import com.swirlds.common.crypto.DigestType;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * This class is used for generating record stream v3 files
 */
@Deprecated
public class RecordStream implements Runnable {

	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	private static final Logger log = LogManager.getLogger(RecordStream.class);

	static final String EXCEPTION = "EXCEPTION";
	static final int STREAM_DELAY = 500;

	static final int HAPI_VERSION = 3;
	static final int RECORD_FORMAT_VERSION = 2;

	static final byte TYPE_PREV_HASH = 1;       // next 48 bytes are hash384 of previous files
	static final byte TYPE_RECORD = 2;          // next data type is transaction and its record
	public static final byte TYPE_SIGNATURE = 3;       // the file content signature, should not be hashed
	public static final byte TYPE_FILE_HASH = 4;       // next 48 bytes are hash384 of content of the file to be signed

	private String logDirectory;
	private String nodeAccountID;
	private FileOutputStream stream = null;
	private DataOutputStream dos = null;
	private String fileName;
	private File file;
	private LinkedBlockingQueue<Triple<Transaction, TransactionRecord, Instant>> recordBuffer;
	private Instant lastRecordConsensusTimeStamp = null;
	private byte[] prevFileHash;
	MessageDigest md;
	MessageDigest mdForContent;
	Platform platform;
	MiscRunningAvgs runningAvgs;
	boolean inFreeze;
	final String recordStreamsDirectory;

	private final PropertySource properties;

	public RecordStream(
			Platform platform,
			MiscRunningAvgs runningAvgs,
			AccountID nodeAccountID,
			String directory,
			PropertySource properties
	) {
		this.runningAvgs = runningAvgs;
		this.platform = platform;
		this.properties = properties;
		this.logDirectory = directory;
		this.nodeAccountID = EntityIdUtils.asLiteralString(nodeAccountID);
		this.recordBuffer = new LinkedBlockingQueue<>(PropertiesLoader.getRecordStreamQueueCapacity());

		if (!directory.endsWith(File.separator)) {
			directory += File.separator;
		}
		recordStreamsDirectory = directory + "record" + this.nodeAccountID;

		try {
			Files.createDirectories(Paths.get(recordStreamsDirectory));
		} catch (IOException e) {
			log.error("Record stream dir {} doesn't exist and cannot be created!", recordStreamsDirectory, e);
			throw new IllegalStateException(e);
		}

		// read the previous file hash from the fileSystem
		byte[] readPrevFileHash = readPrevFileHash(recordStreamsDirectory);

		if (readPrevFileHash == null) {
			this.prevFileHash = new byte[48];
		} else {
			this.prevFileHash = readPrevFileHash;
		}
		log.info("PrevFileHash at starting: {}", () -> Hex.encodeHexString(prevFileHash));

		try {
			md = MessageDigest.getInstance("SHA-384");
			mdForContent = MessageDigest.getInstance("SHA-384");
		} catch (NoSuchAlgorithmException e) {
			log.error("Exception {}", ExceptionUtils.getStackTrace(e));
		}
		this.inFreeze = false;
	}

	public void addRecord(Transaction transaction, TransactionRecord record, Instant consensusTimeStamp) {
		if (recordBuffer != null) {
			try {
				recordBuffer.put(Triple.of(transaction, record, consensusTimeStamp));
			} catch (InterruptedException e) {
				log.error(EXCEPTION, "thread interruption ignored in addRecord: {}", e);
			}
		}
	}

	/**
	 * set `inFreeze` to be given value
	 *
	 * @param inFreeze
	 */
	public void setInFreeze(boolean inFreeze) {
		this.inFreeze = inFreeze;
		log.info("RecordStream inFreeze is set to be {} ", inFreeze);
	}

	/** create a new file with time stamp as file name prefix */
	private void createFile(Instant timestamp) {
		if (stream == null) {
			if (!logDirectory.endsWith(File.separator)) {
				logDirectory += File.separator;
			}

			// replace ":" with "_" so that the file can also be created in Windows OS
			fileName = recordStreamsDirectory + File.separator + timestamp.toString().replace(":", "_") + ".rcd";
			try {
				file = new File(fileName);

				if (file.exists() && !file.isDirectory()) {
					//should not exist already
					log.error("ERROR: Record file {} already exists ", fileName);
					return;
				} else {
					stream = new FileOutputStream(file, false);
					dos = new DataOutputStream(new BufferedOutputStream(stream));
					if (log.isDebugEnabled()) {
						log.debug("Record file {} created ", fileName);
					}

					dos.writeInt(RECORD_FORMAT_VERSION);
					dos.writeInt(HAPI_VERSION);
					dos.write(TYPE_PREV_HASH);

					if (prevFileHash == null) {
						dos.write(new byte[48]);
					} else {
						dos.write(prevFileHash);
					}

					md.update(Ints.toByteArray(RECORD_FORMAT_VERSION));
					md.update(Ints.toByteArray(HAPI_VERSION));
					md.update(TYPE_PREV_HASH);

					if (prevFileHash == null) {
						md.update(new byte[48]);
					} else {
						md.update(prevFileHash);
					}
				}

			} catch (IOException e) {
				log.error("createFile - Exception {} - {}", ExceptionUtils.getStackTrace(e), e.getMessage());
			}
		}
	}

	private void fileHashCheck(String fileName) {
		byte[] array;
		try {
			array = Files.readAllBytes(Paths.get(fileName));
			mdForContent.reset();
			md.reset();

			// Check the hash calculation to do h[i] = hash(p[i-1] || h[i-1] || hash(c[i-1])) where
			// h[i] is the hash of the current file
			// p[i-1] is the contents in the file before the previousHash
			// h[i-1] is the previousHash
			// c[i-1] is the contents of the file after previousHash

			byte[] prevHashBytes = Arrays.copyOfRange(array, 0, 57);
			byte[] fileContentHash = mdForContent.digest(Arrays.copyOfRange(array, 57, array.length));
			byte[] fileHash = md.digest(ArrayUtils.addAll(prevHashBytes, fileContentHash));

			if (log.isDebugEnabled()) {
				log.debug("Hash from stream record file " + Hex.encodeHexString(prevFileHash));
				log.debug("Hash from read record file   " + Hex.encodeHexString(fileHash));
			}

			if (!Arrays.equals(prevFileHash, fileHash)) {
				log.error("Error Exception, hash does not match ");
			}
		} catch (IOException e) {
			log.error("Exception {}", ExceptionUtils.getStackTrace(e));
		}

	}


	/**
	 * Create a signature file for a RecordStream/AccountBalance file;
	 * This signature file contains the Hash of the file to be signed, and a signature signed by the node's Key
	 *
	 * @param fileName
	 * @param signature
	 * @param fileHash
	 */
	public static String generateSigFile(String fileName, byte[] signature, byte[] fileHash) {
		try {
			String newFileName = fileName + "_sig";

			// append signature
			try (FileOutputStream output = new FileOutputStream(newFileName, false)) {
				output.write(TYPE_FILE_HASH);
				output.write(fileHash);
				output.write(TYPE_SIGNATURE);
				output.write(Ints.toByteArray(signature.length));
				output.write(signature);
				return newFileName;
			}
		} catch (IOException e) {
			log.error("generateSigFile :: Fail to generate signature file for {}. Exception: {}", fileName,
					ExceptionUtils.getStackTrace(e));
			return null;
		}
	}


	private void closeFile() {
		try {
			dos.flush();
			stream.flush();

			stream.getChannel().force(true);
			stream.getFD().sync();

			dos.close();
			stream.close();

			// Update the hash calculation to do h[i] = hash(p[i-1] || h[i-1] || hash(c[i-1])) where
			// h[i] is the hash of the current file
			// p[i-1] is the contents in the file before the previousHash
			// h[i-1] is the previousHash
			// c[i-1] is the contents of the file after previousHash

			md.update(mdForContent.digest());
			prevFileHash = md.digest();
			log.info("Hash of current record stream file after closing {}", Hex.encodeHexString(prevFileHash));

			byte[] signature = platform.sign(prevFileHash);

			if (log.isDebugEnabled()) {
				log.debug("Signature: " + Hex.encodeHexString(signature));
			}
			mdForContent.reset();
			md.reset();

			fileHashCheck(fileName);

			generateSigFile(fileName, signature, prevFileHash);

			file = null;
			stream = null;
			dos = null;
		} catch (IOException e) {
			log.warn(EXCEPTION, "Exception in close file {}", e);
		}
	}


	private void close() {
		if (stream != null) {
			log.info("Start to close File {} at {}", fileName, Instant.now());
			closeFile();
			log.info("Finish closing File {} at {}", fileName, Instant.now());
		}
	}


	/**
	 * Main thread to write record to file
	 */
	@Override
	public void run() {

		while (true) {
			try {
				// when the platform is in freeze period, and recordBuffer is empty, and stream is not null, which
				// means the last record has been written into current RecordStream file, we should close and sign it.
				if (inFreeze && recordBuffer.isEmpty() && stream != null) {
					log.info("Finished writing the last record to file before restart.");
					close();
				}

				Triple<Transaction, TransactionRecord, Instant> record = recordBuffer.poll(STREAM_DELAY,
						TimeUnit.MILLISECONDS);
				runningAvgs.recordStreamQueueSize(getRecordStreamQueueSize());

				long recordLogPeriod = properties.getLongProperty("hedera.recordStream.logPeriod");
				if (record != null) {
					Instant currentCensusesTimeStamp = record.getRight();

					//check timestamp decide whether to create new file
					if (lastRecordConsensusTimeStamp != null) {
						long previousSeconds = lastRecordConsensusTimeStamp.getEpochSecond() / recordLogPeriod;
						long currentSeconds = currentCensusesTimeStamp.getEpochSecond() / recordLogPeriod;
						if (currentSeconds != previousSeconds) {
							// close old file
							close();
							// a new day start
							createFile(currentCensusesTimeStamp);
						}

					} else if (stream == null) {
						createFile(currentCensusesTimeStamp);
					} else {
						//NoOp
					}

					dos.write(TYPE_RECORD);

					// write to current file
					byte[] rawBytes = record.getLeft().toByteArray();
					dos.writeInt(rawBytes.length);
					dos.write(rawBytes);

					mdForContent.update(TYPE_RECORD);
					mdForContent.update(Ints.toByteArray(rawBytes.length));
					mdForContent.update(rawBytes);

					rawBytes = record.getMiddle().toByteArray();
					dos.writeInt(rawBytes.length);
					dos.write(rawBytes);

					mdForContent.update(Ints.toByteArray(rawBytes.length));
					mdForContent.update(rawBytes);

					dos.flush();

					lastRecordConsensusTimeStamp = currentCensusesTimeStamp;
				}
			} catch (InterruptedException e) {
				log.error("Exception {}", ExceptionUtils.getStackTrace(e));
				//close existing file to protect data
				close();
			} catch (Exception e) {
				log.error("Unexpected exception {} {}", this.fileName, ExceptionUtils.getStackTrace(e));
				//close existing file to protect data
				close();
			}
		}
	}

	public int getRecordStreamQueueSize() {
		if (recordBuffer == null) {
			return 0;
		}
		return recordBuffer.size();
	}

	public String getRecordStreamsDirectory() {
		return recordStreamsDirectory;
	}

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
			if (files != null) {
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

