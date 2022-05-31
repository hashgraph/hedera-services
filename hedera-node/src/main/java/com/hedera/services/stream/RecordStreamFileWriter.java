package com.hedera.services.stream;

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
import com.swirlds.common.stream.StreamType;
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

public class RecordStreamFileWriter<T extends RecordStreamObject>  implements LinkedObjectStream<T> {
	//TODO: lower log level to debug/trace for all log.infoe

	private static final Logger LOG = LogManager.getLogger(RecordStreamFileWriter.class);
	public static final int OBJECT_STREAM_SIG_VERSION = 1; //TODO: can probably remove this

	/**
	 * stream file type: record stream, or event stream
	 */
	private final StreamType streamType; //TODO: can probably be of type CurrentRecordStreamType

	/**
	 * a messageDigest object for digesting entire stream file and generating entire Hash
	 */
	private final MessageDigest streamDigest;

	/**
	 * a messageDigest object for digesting Metadata in the stream file and generating Metadata Hash
	 * Metadata contains: bytes before startRunningHash || startRunningHash || endRunningHash
	 * || denotes concatenation
	 */
	private final MessageDigest metadataStreamDigest;

	/** file stream and output stream for dump event bytes to file */
	private FileOutputStream stream = null;
	private SerializableDataOutputStream dos = null;
	/** output stream for digesting metaData */
	private SerializableDataOutputStream dosMeta = null;

	private String fileNameShort;
	private File file;
	/**
	 * the path to which we write object stream files and signature files
	 */
	private String dirPath;

	/**
	 * current runningHash before consuming the object added by calling {@link #addObject(T)} method
	 */
	private RunningHash runningHash;

	/**
	 * generate signature bytes for endRunningHash in corresponding file
	 */
	private Signer signer;

	/**
	 * period of generating object stream files in ms
	 */
	private long logPeriodMs;

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
	 * restart, so
	 * that no
	 * object would be missing in the nodes' stream files
	 */
	private boolean startWriteAtCompleteWindow;

	//TODO: add javaDoc
	private RecordStreamFile.Builder recordStreamFile;
	private int recordFileVersion;

	public RecordStreamFileWriter(
			final String dirPath,
			final long logPeriodMs,
			final Signer signer,
			final boolean startWriteAtCompleteWindow,
			final StreamType streamType
	) {
		this.dirPath = dirPath;
		this.logPeriodMs = logPeriodMs;
		this.signer = signer;
		this.startWriteAtCompleteWindow = startWriteAtCompleteWindow;
		this.streamType = streamType;

		try {
			this.streamDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
			this.metadataStreamDigest = MessageDigest.getInstance(DigestType.SHA_384.algorithmName());
		} catch (NoSuchAlgorithmException ex) {
			throw new RuntimeException(ex);
		}
	}

	@Override
	public void addObject(T object) {
		if (checkIfShouldWriteNewFile(object)) {
			// if we have a current file,
			// should write endRunningHash, close current file, and generate signature file
			closeCurrentAndSign();
			// start new file
			startNewFile(object);
			// write the beginning of new file
			begin();
		}

		// if recordStreamFile is null, it means startWriteAtCompleteWindow is true and we are still in the first
		// incomplete window, so we don't serialize this object;
		// so we only serialize the object when stream is not null
		if (recordStreamFile != null) {
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
		} else if (lastConsensusTimestamp == null && startWriteAtCompleteWindow) {
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
		if (recordStreamFile != null) {
			// write endRunningHash
			Hash endRunningHash;
			try {
				endRunningHash = runningHash.getFutureHash().get();
				recordStreamFile.setEndObjectRunningHash(toProto(endRunningHash));
				dosMeta.writeSerializable(endRunningHash, true);
				LOG.info(OBJECT_STREAM_FILE.getMarker(), "closeCurrentAndSign :: write endRunningHash {}",
						endRunningHash);
				// write block number to metadata
				dosMeta.writeLong(recordStreamFile.getBlockNumber());
			} catch (IOException e) {
				Thread.currentThread().interrupt();
				LOG.warn(EXCEPTION.getMarker(),
						"closeCurrentAndSign :: IOException when serializing endRunningHash", e);
				return;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				LOG.error(EXCEPTION.getMarker(),
						"closeCurrentAndSign :: Got interrupted when getting endRunningHash for writing {}",
						fileNameShort, e);
				return;
			}

			/* When sidecars are enabled we will need to add more logic at this point:
				1. We should have a map (?) of the TransactionSidecarRecords with key - the type of sidecar, updated
				everytime we consume a RSO.
				2. At this point, we will create a SidecarFile for each present type of sidecar record in the current
				 block
				3. We populate the SidecarMetadata of the RecordStreamFile message
			*/

			final var firstTxnTimestamp = recordStreamFile.getRecordStreamItems(0).getRecord().getConsensusTimestamp();
			this.file = new File(
					generateStreamFilePath(
							Instant.ofEpochSecond(firstTxnTimestamp.getSeconds(), firstTxnTimestamp.getNanos())));
			this.fileNameShort = file.getName();
			try {
				if (file.exists() && !file.isDirectory()) {
					LOG.info(OBJECT_STREAM.getMarker(), "Stream file already exists {}", fileNameShort);
				} else {
					// create record file
					stream = new FileOutputStream(file, false);
					dos = new SerializableDataOutputStream(
							new BufferedOutputStream(new HashingOutputStream(streamDigest, stream)));
					LOG.info(OBJECT_STREAM_FILE.getMarker(), "Stream file created {}", fileNameShort);

					// write contents of record file - record file version and serialized RecordFile protobuf
					dos.writeInt(recordFileVersion);
					dos.write(toBytes(recordStreamFile));
					dos.flush();

					// close file
					final var currentFile = file;
					closeFile();

					// get entire hash of this stream file
					final var entireHash = new Hash(streamDigest.digest(), DigestType.SHA_384);
					// get metaData hash of this stream file
					final var metaHash = new Hash(metadataStreamDigest.digest(), DigestType.SHA_384);

					// create proto messages for signature file
					final var fileSignature = generateSignatureObject(entireHash);
					final var metadataSignature = generateSignatureObject(metaHash);
					final var signatureFile = SignatureFile.newBuilder()
							.setFileSignature(fileSignature)
							.setMetadataSignature(metadataSignature);

					// create signature file
					final var sigFilePath = generateSigFilePath(currentFile);
					try (final var fos = new FileOutputStream(sigFilePath)) {
						signatureFile.build().writeTo(fos);
						LOG.info(OBJECT_STREAM_FILE.getMarker(),
								"closeCurrentAndSign :: signature file saved: {}", sigFilePath);
					} catch (IOException e) {
						LOG.error(EXCEPTION.getMarker(),
								"closeCurrentAndSign ::  :: Fail to generate signature file for {}",
								fileNameShort, e);
					}
				}
			} catch (FileNotFoundException e) {
				Thread.currentThread().interrupt();
				LOG.error(EXCEPTION.getMarker(), "closeCurrentAndSign :: FileNotFound: ", e);
			} catch (IOException e) {
				Thread.currentThread().interrupt();
				LOG.warn(EXCEPTION.getMarker(),
						"closeCurrentAndSign :: IOException when serializing {}", recordStreamFile, e);
			}
		}
	}

	private void startNewFile(T object) {
		this.recordStreamFile = RecordStreamFile.newBuilder()
				.setBlockNumber(object.getStreamAlignment()); // Do we need to populate block number for phase 1?
	}

	/**
	 * write the beginning part of the record stream file and metadata:
	 * hapiProtoVersion and initial runningHash.
	 * Save record file version for later use when record file is created.
	 */
	private void begin() {
		final var fileHeader = streamType.getFileHeader();
		recordFileVersion = fileHeader[0];
		recordStreamFile.setHapiProtoVersion(SemanticVersion.newBuilder()
				.setMajor(fileHeader[1])
				.setMinor(fileHeader[2])
				.setPatch(fileHeader[3])
		);
		dosMeta = new SerializableDataOutputStream(new HashingOutputStream(metadataStreamDigest));
		try {
			for (final var value : fileHeader) {
				dosMeta.writeInt(value);
			}
			// write startRunningHash
			final var startRunningHash = runningHash.getFutureHash().get();
			recordStreamFile.setStartObjectRunningHash(toProto(startRunningHash));
			dosMeta.writeSerializable(startRunningHash, true);
			LOG.info(OBJECT_STREAM_FILE.getMarker(), "begin :: write startRunningHash {}", startRunningHash);
		} catch (IOException e) {
			Thread.currentThread().interrupt();
			LOG.error(EXCEPTION.getMarker(), "begin :: Got IOException when writing startRunningHash to {}",
					fileNameShort, e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOG.error(EXCEPTION.getMarker(), "begin :: Got interrupted when getting startRunningHash for writing {}",
					fileNameShort, e);
		}
	}

	/**
	 * serialize given object with ClassId
	 *
	 * @param object
	 */
	private void consume(T object) {
		recordStreamFile.addRecordStreamItems(
				RecordStreamItem.newBuilder()
						.setTransaction(object.getTransaction())
						.setRecord(object.getTransactionRecord())
						.build()
		);

		// We will need to add logic here to save the sidecar records
		// of the current object but that has been planned for phase
	}

	/**
	 * if stream is not null, close current file and save to disk
	 */
	private void closeFile() {
		if (stream != null) {
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
				recordStreamFile = null;
			} catch (IOException e) {
				LOG.warn(EXCEPTION.getMarker(), "Exception in close file", e);
			}
			LOG.info(OBJECT_STREAM_FILE.getMarker(), "File {} is closed at {}",
					() -> fileNameShort, () -> Instant.now());
		}
	}

	/**
	 * generate full fileName from given Instant object
	 * @param consensusTimestamp
	 * 	the consensus timestamp of the first transaction in the record file
	 * @return
	 * 	the new record file name
	 */
	String generateStreamFilePath(Instant consensusTimestamp) {
		return dirPath + File.separator + generateStreamFileNameFromInstant(consensusTimestamp, streamType);
	}

	public void setRunningHash(Hash hash) {
		this.runningHash = new RunningHash(hash);
	}

	/**
	 * //TODO: fix JavaDoc
	 * this method is called when the node falls behind
	 * resets all populated up to this point fields (digests, dosMeta, recordStreamFile)
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
		recordStreamFile = null;
		LOG.info(OBJECT_STREAM.getMarker(), "RecordStreamFileWriter::clear executed.");
	}

	public void close() {
		this.closeCurrentAndSign();
		LOG.info(LogMarker.FREEZE.getMarker(), "RecordStreamFileWriter finished writing the last object, is stopped");
	}

	public void setStartWriteAtCompleteWindow(boolean startWriteAtCompleteWindow) {
		this.startWriteAtCompleteWindow = startWriteAtCompleteWindow;
		LOG.info(OBJECT_STREAM.getMarker(), "RecordStreamFileWriter::setStartWriteAtCompleteWindow: {}", startWriteAtCompleteWindow);
	}

	public boolean getStartWriteAtCompleteWindow() {
		return this.startWriteAtCompleteWindow;
	}

	private byte[] toBytes(final RecordStreamFile.Builder recordStreamFileProtoBuilder) throws IOException {
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
