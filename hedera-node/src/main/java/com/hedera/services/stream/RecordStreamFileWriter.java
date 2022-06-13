package com.hedera.services.stream;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.google.protobuf.CodedOutputStream;
import com.hedera.services.stream.proto.HashAlgorithm;
import com.hedera.services.stream.proto.HashObject;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hedera.services.stream.proto.SignatureFile;
import com.hedera.services.stream.proto.SignatureObject;
import com.hedera.services.stream.proto.SignatureType;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.LinkedObjectStream;
import com.swirlds.common.stream.Signer;
import com.swirlds.logging.LogMarker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import static com.swirlds.common.stream.LinkedObjectStreamUtilities.generateSigFilePath;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.generateStreamFileNameFromInstant;
import static com.swirlds.common.stream.LinkedObjectStreamUtilities.getPeriod;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.OBJECT_STREAM;
import static com.swirlds.logging.LogMarker.OBJECT_STREAM_FILE;

class RecordStreamFileWriter<T extends RecordStreamObject> implements LinkedObjectStream<T> {
	private static final Logger LOG = LogManager.getLogger(RecordStreamFileWriter.class);

	/**
<	 * the current record stream type;
	 * used to obtain file extensions and versioning
	 */
	private final RecordStreamType streamType;

	/**
	 * a messageDigest object for digesting entire stream file and generating entire record stream file hash
	 */
	private final MessageDigest streamDigest;

	/**
	 * a messageDigest object for digesting metaData in the stream file and generating metaData hash.
	 * Metadata contains:
	 * record stream version || HAPI proto version || startRunningHash || endRunningHash || blockNumber, where
	 * || denotes concatenation
	 */
	private final MessageDigest metadataStreamDigest;

	/**
	 * file stream and output stream for writing record stream data to file
	 * */
	private FileOutputStream stream = null;
	private SerializableDataOutputStream dos = null;

	/**
	 * output stream for digesting metaData
	 */
	private SerializableDataOutputStream dosMeta = null;

	/**
	 * current runningHash before consuming the object added by calling {@link #addObject(T)} method
	 */
	private RunningHash runningHash;

	/**
	 * signer for generating signatures
	 */
	private final Signer signer;

	/**
	 * period of generating record stream files in ms
	 */
	private final long logPeriodMs;

	/**
	 * initially, set to be consensus timestamp of the first object;
	 * start to write a new file when the next object's consensus timestamp is in different periods with
	 * lastConsensusTimestamp;
	 * update its value each time receives a new object
	 */
	private Instant lastConsensusTimestamp;

	/**
	 * if it is true, we don't write object stream file until the first complete window. This is suitable for streaming
	 * after reconnect, so that reconnect node can generate the same stream files as other nodes after reconnect.
	 *
	 * if it is false, we start to write object stream file immediately. This is suitable for streaming after
	 * restart, so that no object would be missing in the nodes' stream files
	 */
	private boolean startWriteAtCompleteWindow;

	/**
	 * The current record stream file that is being written.
	 */
	private File file;

	/**
	 * The file name of the current record stream file. Used for logs.
	 */
	private String fileNameShort;

	/**
	 * the path to which we write record stream files and signature files
	 */
	private final String dirPath;
	private RecordStreamFile.Builder recordStreamFileBuilder;
	private int recordFileVersion;

	public RecordStreamFileWriter(
			final String dirPath,
			final long logPeriodMs,
			final Signer signer,
			final boolean startWriteAtCompleteWindow,
			final RecordStreamType streamType
	) throws NoSuchAlgorithmException {
		this.dirPath = dirPath;
		this.logPeriodMs = logPeriodMs;
		this.signer = signer;
		this.startWriteAtCompleteWindow = startWriteAtCompleteWindow;
		this.streamType = streamType;
		this.streamDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
		this.metadataStreamDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
	}

	@Override
	public void addObject(T object) {
		if (checkIfShouldWriteNewFile(object)) {
			// if we are currently writing a file,
			// finish current file and generate signature file
			closeCurrentAndSign();
			// write the beginning of the new file
			beginNew(object);
		}

		// if recordStreamFile is null, it means startWriteAtCompleteWindow is true,
		// and we are still in the first incomplete window, so we don't serialize this object;
		// so we only serialize the object when stream is not null
		if (recordStreamFileBuilder != null) {
			consume(object);
		}

		// update runningHash
		this.runningHash = object.getRunningHash();
	}

	/**
	 * check whether need to start a new file
	 *
	 * @param object
	 * 		the object to be written into file
	 * @return whether we should start a new File for writing this object
	 */
	public boolean checkIfShouldWriteNewFile(T object) {
		Instant currentConsensusTimestamp = object.getTimestamp();
		boolean result;
		if (lastConsensusTimestamp == null && !startWriteAtCompleteWindow) {
			// this is the first object, we should start writing it to new File
			result = true;
		} else if (lastConsensusTimestamp == null) {
			// this is the first object, we should wait for the first complete window
			result = false;
		} else {
			// if lastConsensusTimestamp and currentConsensusTimestamp are in different periods,
			// we should start a new file
			result =
					getPeriod(lastConsensusTimestamp, logPeriodMs) != getPeriod(currentConsensusTimestamp, logPeriodMs);
		}
		// update lastConsensusTimestamp
		lastConsensusTimestamp = currentConsensusTimestamp;
		return result;
	}

	/**
	 * if recordStreamFile is not null:
	 * write last runningHash to current file;
	 * close current file;
	 * and generate a corresponding signature file
	 */
	public void closeCurrentAndSign() {
		if (recordStreamFileBuilder != null) {
			// generate file name
			final var firstTxnTimestamp =
					recordStreamFileBuilder.getRecordStreamItems(0).getRecord().getConsensusTimestamp();
			this.file = new File(
					generateStreamFilePath(
							Instant.ofEpochSecond(firstTxnTimestamp.getSeconds(), firstTxnTimestamp.getNanos())));
			this.fileNameShort = file.getName();
			if (file.exists() && !file.isDirectory()) {
				LOG.debug(OBJECT_STREAM.getMarker(), "Stream file already exists {}", fileNameShort);
			} else {
				try {
					// write endRunningHash
					final var endRunningHash = runningHash.getFutureHash().get();
					recordStreamFileBuilder.setEndObjectRunningHash(toProto(endRunningHash));
					dosMeta.write(endRunningHash.getValue());
					LOG.debug(OBJECT_STREAM_FILE.getMarker(), "closeCurrentAndSign :: write endRunningHash {}",
							endRunningHash);

					// write block number to metadata
					dosMeta.writeLong(recordStreamFileBuilder.getBlockNumber());
					LOG.debug(OBJECT_STREAM_FILE.getMarker(), "closeCurrentAndSign :: write block number {}",
							recordStreamFileBuilder.getBlockNumber());
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					LOG.error(EXCEPTION.getMarker(),
							"closeCurrentAndSign :: Got interrupted when getting endRunningHash for writing {}",
							fileNameShort, e);
					return;
				} catch (IOException e) {
					Thread.currentThread().interrupt();
					LOG.warn(EXCEPTION.getMarker(),
							"closeCurrentAndSign :: IOException when serializing endRunningHash and block number into "
									+ "metadata", e);
					return;
				}

				/* When sidecars are enabled we will need to add more logic at this point:
					1. We should have a map (?) of the TransactionSidecarRecords with key - the type of sidecar, updated
					everytime we consume a RSO.
					2. At this point, we will create a SidecarFile for each present type of sidecar record in the current
					 block
					3. We populate the SidecarMetadata of the RecordStreamFile message
				*/

				try {
					// create record file
					stream = new FileOutputStream(file, false);
					dos = new SerializableDataOutputStream(
							new BufferedOutputStream(new HashingOutputStream(streamDigest, stream)));
					LOG.debug(OBJECT_STREAM_FILE.getMarker(), "Stream file created {}", fileNameShort);

					// write contents of record file - record file version and serialized RecordFile protobuf
					dos.writeInt(recordFileVersion);
					dos.write(serialize(recordStreamFileBuilder));
					dos.flush();
					LOG.debug(OBJECT_STREAM_FILE.getMarker(), "Stream file written successfully {}", fileNameShort);

					// close record file
					final var currentFile = file;
					closeFile();

					// create signature file
					createSignatureFile(currentFile);
				} catch (FileNotFoundException e) {
					Thread.currentThread().interrupt();
					LOG.error(EXCEPTION.getMarker(), "closeCurrentAndSign :: FileNotFound: {}", e.getMessage());
				} catch (IOException e) {
					Thread.currentThread().interrupt();
					LOG.warn(EXCEPTION.getMarker(),
							"closeCurrentAndSign :: IOException when serializing {}", recordStreamFileBuilder, e);
				}
			}
		}
	}

	/**
	 * write the beginning part of the record stream file and metadata:
	 * record stream version, HAPI proto version and initial runningHash.
	 */
	private void beginNew(T object) {
		final var fileHeader = streamType.getFileHeader();
		// instead of creating the record file here and writing
		// the record file version in it, save the version and
		// perform the whole file creation in {@link #closeCurrentAndSign()} method
		recordFileVersion = fileHeader[0];
		// add known values to recordStreamFile proto
		recordStreamFileBuilder = RecordStreamFile.newBuilder().setBlockNumber(object.getStreamAlignment());
		recordStreamFileBuilder.setHapiProtoVersion(SemanticVersion.newBuilder()
				.setMajor(fileHeader[1])
				.setMinor(fileHeader[2])
				.setPatch(fileHeader[3])
		);
		dosMeta = new SerializableDataOutputStream(new HashingOutputStream(metadataStreamDigest));
		try {
			// write record stream version and HAPI version to metadata
			for (final var value : fileHeader) {
				dosMeta.writeInt(value);
			}
			// write startRunningHash
			final var startRunningHash = runningHash.getFutureHash().get();
			recordStreamFileBuilder.setStartObjectRunningHash(toProto(startRunningHash));
			dosMeta.write(startRunningHash.getValue());
			LOG.debug(
					OBJECT_STREAM_FILE.getMarker(),
					"beginNew :: write startRunningHash to metadata {}", startRunningHash);
		} catch (IOException e) {
			Thread.currentThread().interrupt();
			LOG.error(
					EXCEPTION.getMarker(),
					"beginNew :: Got IOException when writing startRunningHash to metadata stream", e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOG.error(
					EXCEPTION.getMarker(),
					"beginNew :: Got interrupted when getting startRunningHash for writing to metadata stream.", e);
		}
	}

	/**
	 * add given object to the current record stream file
	 *
	 * @param object
	 * 	object to be added to the record stream file
	 */
	private void consume(T object) {
		recordStreamFileBuilder.addRecordStreamItems(
				RecordStreamItem.newBuilder()
						.setTransaction(object.getTransaction())
						.setRecord(object.getTransactionRecord())
						.build()
		);

		// In phase 2 we will need to add logic here to also
		// save the sidecar records of the current object
	}

	/**
	 * if stream is not null, close current file and save to disk
	 */
	private void closeFile() {
		try {
			dos.flush();
			stream.flush();

			stream.getChannel().force(true);
			stream.getFD().sync();

			dos.close();
			stream.close();
			dosMeta.close();

			file = null;
			stream = null;
			dos = null;
			dosMeta = null;
			recordStreamFileBuilder = null;
		} catch (IOException e) {
			LOG.warn(EXCEPTION.getMarker(), "Exception in close file", e);
		}
		LOG.debug(OBJECT_STREAM_FILE.getMarker(),
				"File {} is closed at {}", () -> fileNameShort, Instant::now);
	}

	/**
	 * generate full fileName from given Instant object
	 * @param consensusTimestamp
	 * 	the consensus timestamp of the first transaction in the record file
	 * @return
	 * 	the new record file name
	 */
	String generateStreamFilePath(final Instant consensusTimestamp) {
		return dirPath + File.separator + generateStreamFileNameFromInstant(consensusTimestamp, streamType);
	}

	public void setRunningHash(final Hash hash) {
		this.runningHash = new RunningHash(hash);
	}

	/**
	 * this method is called when the node falls behind
	 * resets all populated up to this point fields (metadataDigest, dosMeta, recordStreamFile)
	 */
	@Override
	public void clear() {
		if (dosMeta != null) {
			try {
				dosMeta.close();
				metadataStreamDigest.reset();
				dosMeta = null;
			} catch (IOException e) {
				LOG.warn(EXCEPTION.getMarker(), "RecordStreamFileWriter::clear Exception in closing dosMeta", e);
			}
		}
		recordStreamFileBuilder = null;
		LOG.debug(OBJECT_STREAM.getMarker(), "RecordStreamFileWriter::clear executed.");
	}

	public void close() {
		this.closeCurrentAndSign();
		LOG.debug(LogMarker.FREEZE.getMarker(), "RecordStreamFileWriter finished writing the last object, is stopped");
	}

	public void setStartWriteAtCompleteWindow(final boolean startWriteAtCompleteWindow) {
		this.startWriteAtCompleteWindow = startWriteAtCompleteWindow;
		LOG.debug(OBJECT_STREAM.getMarker(),
				"RecordStreamFileWriter::setStartWriteAtCompleteWindow: {}", startWriteAtCompleteWindow);
	}

	public boolean getStartWriteAtCompleteWindow() {
		return this.startWriteAtCompleteWindow;
	}

	/**
	 * Helper method that serializes a RecordStreamFile.
	 * Uses deterministic serialization to ensure multiple invocations on the same
	 * object lead to identical serialization.
	 * @param recordStreamFileProtoBuilder
	 * 		the RecordStreamFile object that needs to be serialized
	 * @return
	 * 		the serialized bytes
	 */
	private byte[] serialize(final RecordStreamFile.Builder recordStreamFileProtoBuilder) throws IOException {
		final var recordStreamFileProto = recordStreamFileProtoBuilder.build();
		final var result = new byte[recordStreamFileProto.getSerializedSize()];
		final var output = CodedOutputStream.newInstance(result);
		output.useDeterministicSerialization();
		recordStreamFileProto.writeTo(output);
		output.checkNoSpaceLeft();
		return result;
	}

	private HashObject toProto(final Hash hash) {
		return HashObject.newBuilder()
				.setAlgorithm(HashAlgorithm.SHA_384)
				.setLength(hash.getDigestType().digestLength())
				.setHash(ByteString.copyFrom(hash.getValue()))
				.build();
	}

	private void createSignatureFile(final File relatedRecordStreamFile) {
		// get entire hash of current record stream file
		final var entireHash = new Hash(streamDigest.digest(), DigestType.SHA_384);
		// get metaData hash of current record stream file
		final var metaHash = new Hash(metadataStreamDigest.digest(), DigestType.SHA_384);

		// create proto messages for signature file
		final var fileSignature = generateSignatureObject(entireHash);
		final var metadataSignature = generateSignatureObject(metaHash);
		final var signatureFile = SignatureFile.newBuilder()
				.setFileSignature(fileSignature)
				.setMetadataSignature(metadataSignature);

		// create signature file
		final var sigFilePath = generateSigFilePath(relatedRecordStreamFile);
		try (final var fos = new FileOutputStream(sigFilePath)) {
			signatureFile.build().writeTo(fos);
			LOG.debug(OBJECT_STREAM_FILE.getMarker(),
					"closeCurrentAndSign :: signature file saved: {}", sigFilePath);
		} catch (IOException e) {
			LOG.error(EXCEPTION.getMarker(),
					"closeCurrentAndSign ::  :: Fail to generate signature file for {}",
					fileNameShort, e);
		}
	}

	private SignatureObject generateSignatureObject(final Hash hash) {
		final var signature = signer.sign(hash.getValue());
		return SignatureObject.newBuilder()
				.setType(SignatureType.SHA_384_WITH_RSA)
				.setLength(signature.length)
				.setChecksum(101 - signature.length) // simple checksum to detect if at wrong place in the stream
				.setSignature(ByteString.copyFrom(signature))
				.setHashObject(toProto(hash))
				.build();
	}
}
