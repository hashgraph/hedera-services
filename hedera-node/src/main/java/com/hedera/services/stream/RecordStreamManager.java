package com.hedera.services.stream;

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

import com.hedera.services.stats.MiscRunningAvgs;
import com.swirlds.common.Platform;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.stream.HashCalculatorForStream;
import com.swirlds.common.stream.MultiStream;
import com.swirlds.common.stream.QueueThread;
import com.swirlds.common.stream.RunningHashCalculatorForStream;
import com.swirlds.common.stream.TimestampStreamFileWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static com.swirlds.common.Constants.SEC_TO_MS;

/**
 * This class is used for generating record stream files when record streaming is enabled,
 * and for calculating runningHash for {@link RecordStreamObject}s
 */
public class RecordStreamManager {
	/** use this for all logging, as controlled by the optional data/log4j2.xml file */
	static Logger LOGGER = LogManager.getLogger(RecordStreamManager.class);

	/**
	 * receives {@link RecordStreamObject}s from {@link com.hedera.services.legacy.services.state.AwareProcessLogic}
	 * .addForStreaming,
	 * then passes to hashQueueThread and writeQueueThread
	 */
	private MultiStream<RecordStreamObject> multiStream;

	/** receives {@link RecordStreamObject}s from multiStream, then passes to hashCalculator */
	private QueueThread<RecordStreamObject> hashQueueThread;
	/**
	 * receives {@link RecordStreamObject}s from hashQueueThread, calculates this object's Hash, then passes to
	 * runningHashCalculator
	 */
	private HashCalculatorForStream<RecordStreamObject> hashCalculator;
	/** receives {@link RecordStreamObject}s from hashCalculator, calculates and set runningHash for this object */
	private RunningHashCalculatorForStream<RecordStreamObject> runningHashCalculator;

	/** receives {@link RecordStreamObject}s from multiStream, then passes to streamFileWriter */
	private QueueThread<RecordStreamObject> writeQueueThread;
	/**
	 * receives {@link RecordStreamObject}s from writeQueueThread, serializes {@link RecordStreamObject}s to record
	 * stream files
	 */
	private TimestampStreamFileWriter<RecordStreamObject> streamFileWriter;

	/** initial running Hash of records */
	private Hash initialHash = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);

	/**
	 * when record streaming is started after reconnect, or at state recovering, startWriteAtCompleteWindow should be
	 * set
	 * to be true;
	 * when record streaming is started after restart, it should be set to be false
	 */
	private boolean startWriteAtCompleteWindow = false;

	/**
	 * whether the platform is in freeze period
	 */
	private volatile boolean inFreeze = false;

	/**
	 * an instance for recording the average value of recordStream queue size
	 */
	private MiscRunningAvgs runningAvgs;

	/**
	 * @param platform
	 * 		the platform which initializes this RecordStreamManager instance
	 * @param runningAvgs
	 * 		an instance for recording the average value of recordStream queue size
	 * @param enableRecordStreaming
	 * 		whether write record stream files or not
	 * @param recordStreamDir
	 * 		the directory to which record stream files are written
	 * @param recordsLogPeriod
	 * 		period of generating recordStream file
	 * @param recordStreamQueueCapacity
	 * 		capacity of the blockingQueue from which we take records and write to RecordStream files
	 * @throws NoSuchAlgorithmException
	 * 		is thrown when fails to get required MessageDigest instance
	 * @throws IOException
	 * 		is thrown when fails to create directory for record streaming
	 */
	public RecordStreamManager(final Platform platform,
			final MiscRunningAvgs runningAvgs,
			final boolean enableRecordStreaming,
			final String recordStreamDir,
			final long recordsLogPeriod,
			final int recordStreamQueueCapacity,
			final Hash initialHash) throws NoSuchAlgorithmException, IOException {
		if (enableRecordStreaming) {
			// the directory to which record stream files are written
			Files.createDirectories(Paths.get(recordStreamDir));

			streamFileWriter = new TimestampStreamFileWriter<>(
					recordStreamDir,
					recordsLogPeriod * SEC_TO_MS,
					platform,
					startWriteAtCompleteWindow,
					RecordStreamType.RECORD);
			writeQueueThread = new QueueThread<>("writeQueueThread", platform.getSelfId(), streamFileWriter,
					recordStreamQueueCapacity);
		}

		this.runningAvgs = runningAvgs;

		runningHashCalculator = new RunningHashCalculatorForStream<>();
		hashCalculator = new HashCalculatorForStream<>(runningHashCalculator);
		hashQueueThread = new QueueThread<>("hashQueueThread", platform.getSelfId(), hashCalculator,
				recordStreamQueueCapacity);

		multiStream = new MultiStream<>(
				enableRecordStreaming ? List.of(hashQueueThread, writeQueueThread) : List.of(hashQueueThread));
		this.initialHash = initialHash;
		multiStream.setRunningHash(initialHash);

		LOGGER.info("Finish initializing RecordStreamManager with: enableRecordStreaming: {}, recordStreamDir: {}," +
						"recordsLogPeriod: {} secs, recordStreamQueueCapacity: {}, initialHash: {}",
				() -> enableRecordStreaming,
				() -> recordStreamDir,
				() -> recordsLogPeriod,
				() -> recordStreamQueueCapacity,
				() -> initialHash);
	}

	/**
	 * Is used for unit testing
	 *
	 * @param multiStream
	 * 		the instance which receives {@link RecordStreamObject}s then passes to nextStreams
	 * @param writeQueueThread
	 * 		receives {@link RecordStreamObject}s from multiStream, then passes to streamFileWriter
	 * @param runningAvgs
	 * 		an instance for recording the average value of recordStream queue size
	 */
	RecordStreamManager(final MultiStream<RecordStreamObject> multiStream,
			final QueueThread<RecordStreamObject> writeQueueThread,
			final MiscRunningAvgs runningAvgs) {
		this.multiStream = multiStream;
		this.writeQueueThread = writeQueueThread;
		multiStream.setRunningHash(initialHash);
		this.runningAvgs = runningAvgs;
	}

	/**
	 * receives a consensus record from {@link com.hedera.services.legacy.services.state.AwareProcessLogic} each time,
	 * sends it to multiStream which then sends to two queueThread for calculating runningHash and writing to file
	 *
	 * @param recordStreamObject
	 * 		the {@link RecordStreamObject} object to be added
	 */
	public void addRecordStreamObject(final RecordStreamObject recordStreamObject) {
		if (!inFreeze) {
			try {
				multiStream.add(recordStreamObject);
			} catch (InterruptedException ex) {
				LOGGER.error("thread interruption ignored in addRecordStreamObject: {}", ex);
			}
		}
		runningAvgs.recordStreamQueueSize(getRecordStreamingQueueSize());
	}

	/**
	 * set `inFreeze` to be the given value
	 *
	 * @param inFreeze
	 */
	public void setInFreeze(boolean inFreeze) {
		this.inFreeze = inFreeze;
		LOGGER.info("RecordStream inFreeze is set to be {} ", inFreeze);
		if (inFreeze) {
			multiStream.close();
		}
	}

	/**
	 * sets initialHash after loading from signed state
	 *
	 * @param initialHash
	 * 		current runningHash of all {@link RecordStreamObject}s
	 */
	public void setInitialHash(final Hash initialHash) {
		this.initialHash = initialHash;
		LOGGER.info("RecordStreamManager::setInitialHash: {}", () -> initialHash);
		multiStream.setRunningHash(initialHash);
	}

	/**
	 * sets startWriteAtCompleteWindow:
	 * it should be set to be true after reconnect;
	 * it should be set to be false at restart
	 *
	 * @param startWriteAtCompleteWindow
	 * 		whether the writer should not write until the first complete window
	 */
	public void setStartWriteAtCompleteWindow(boolean startWriteAtCompleteWindow) {
		if (streamFileWriter != null) {
			streamFileWriter.setStartWriteAtCompleteWindow(startWriteAtCompleteWindow);
		}
	}

	/**
	 * returns current size of working queue for calculating hash and runningHash
	 *
	 * @return current size of working queue for calculating hash and runningHash
	 */
	int getHashQueueSize() {
		return hashQueueThread.getQueueSize();
	}

	/**
	 * returns current size of working queue for writing to record stream files
	 *
	 * @return current size of working queue for writing to record stream files
	 */
	int getRecordStreamingQueueSize() {
		return writeQueueThread == null ? 0 : writeQueueThread.getQueueSize();
	}

	/**
	 * for unit testing
	 *
	 * @return current multiStream instance
	 */
	MultiStream<RecordStreamObject> getMultiStream() {
		return multiStream;
	}

	/**
	 * for unit testing
	 *
	 * @return current TimestampStreamFileWriter instance
	 */
	TimestampStreamFileWriter<RecordStreamObject> getStreamFileWriter() {
		return streamFileWriter;
	}

	/**
	 * for unit testing
	 *
	 * @return current HashCalculatorForStream instance
	 */
	HashCalculatorForStream<RecordStreamObject> getHashCalculator() {
		return hashCalculator;
	}

	/**
	 * for unit testing
	 *
	 * @return whether freeze period has started
	 */
	boolean getInFreeze() {
		return inFreeze;
	}

	/**
	 * for unit testing
	 *
	 * @return a copy of initialHash
	 */
	public Hash getInitialHash() {
		return new Hash(initialHash);
	}
}
