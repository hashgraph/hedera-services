/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.node.app.hapi.utils.exports;

import static com.hedera.services.stream.proto.SignatureType.SHA_384_WITH_RSA;
import static com.swirlds.common.io.utility.FileUtils.getAbsolutePath;
import static com.swirlds.common.utility.CommonUtils.hex;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
import com.hedera.services.stream.proto.HashAlgorithm;
import com.hedera.services.stream.proto.HashObject;
import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.SignatureFile;
import com.hedera.services.stream.proto.SignatureObject;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.HashingOutputStream;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.stream.EventStreamType;
import com.swirlds.common.stream.StreamType;
import com.swirlds.common.stream.internal.StreamTypeFromJson;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.logging.log4j.core.LoggerContext;

/**
 * This is a standalone utility tool to read record stream file and check if block number is increasing as expected
 *
 */
public class RecordBlockNumberTool {
	/** next bytes are signature */
	private static final byte TYPE_SIGNATURE = 3;
	/** next 48 bytes are hash384 of content of the file to be signed */
	private static final byte TYPE_FILE_HASH = 4;

	private static final int DEFAULT_RECORD_STREAM_VERSION = 6;

	private static final String LOG_CONFIG_PROPERTY = "logConfig";
	private static final String FILE_NAME_PROPERTY = "fileName";
	private static final String DEST_DIR_PROPERTY = "destDir";
	private static final String DIR_PROPERTY = "dir";
	private static final String HAPI_PROTOBUF_VERSION = "hapiProtoVersion";

	private static final Logger LOGGER = LogManager.getLogger(FileSignTool.class);
	private static final Marker MARKER = MarkerManager.getMarker("BLOCK_NUMBER");
	private static final int BYTES_COUNT_IN_INT = 4;
	/** default log4j2 file name */
	private static final String DEFAULT_LOG_CONFIG = "log4j2.xml";
	/** name of RecordStreamType */
	private static final String RECORD_STREAM_EXTENSION = "rcd";

	private static long prevBlockNumber = 0;

	public static void prepare() throws ConstructableRegistryException {
		final ConstructableRegistry registry = ConstructableRegistry.getInstance();
		registry.registerConstructables("com.swirlds.common");

		LOGGER.info(MARKER, "registering Constructables for parsing record stream files");
		// if we are parsing new record stream files,
		// we need to add HederaNode.jar and hedera-protobuf-java-*.jar into class path,
		// so that we can register for parsing RecordStreamObject
		registry.registerConstructables("com.hedera.services.stream");
	}

	private static Pair<Integer, Optional<RecordStreamFile>> readUncompressedRecordStreamFile(
			final String fileLoc) throws IOException {
		try (final FileInputStream fin = new FileInputStream(fileLoc)) {
			final int recordFileVersion = ByteBuffer.wrap(fin.readNBytes(4)).getInt();
			final RecordStreamFile recordStreamFile = RecordStreamFile.parseFrom(fin);
			return Pair.of(recordFileVersion, Optional.ofNullable(recordStreamFile));
		}
	}

	private static void trackBlockNumber(final long currentBlockNumber) {
		if (currentBlockNumber <= prevBlockNumber) {
			LOGGER.error(
					MARKER,
					"Found new block number is equal or less than the prevous one, current {} vs"
							+ " prev {}",
					currentBlockNumber,
					prevBlockNumber);
		}

		if (prevBlockNumber !=0 && (currentBlockNumber - prevBlockNumber) > 1) {
			LOGGER.error(
					MARKER,
					"Found a gap between block numbers: current {} vs"
							+ " prev {}",
					currentBlockNumber,
					prevBlockNumber);
		}
		LOGGER.error(MARKER, "Block number = {}", currentBlockNumber);
		prevBlockNumber = currentBlockNumber;
	}

	private static void readRecordFile(
			final String recordFile) {

		// extract latest app version from system property if available
		final String appVersionString = System.getProperty(HAPI_PROTOBUF_VERSION);
		if (appVersionString != null) {
			final String[] versions =
					appVersionString.replace("-SNAPSHOT", "").split(Pattern.quote("."));
			if (versions.length >= 3) {
				try {
					fileHeader =
							new int[] {
									DEFAULT_RECORD_STREAM_VERSION,
									Integer.parseInt(versions[0]),
									Integer.parseInt(versions[1]),
									Integer.parseInt(versions[2]),
							};
				} catch (final NumberFormatException e) {
					LOGGER.error(
							MARKER,
							"Error when parsing app version string {}",
							appVersionString,
							e);
				}
			}
		}
		LOGGER.info(MARKER, "Record stream file header is {}", Arrays.toString(fileHeader));

		try {
			// parse record file
			final Pair<Integer, Optional<RecordStreamFile>> recordResult =
					readUncompressedRecordStreamFile(recordFile);
			final long blockNumber = recordResult.getValue().get().getBlockNumber();

			trackBlockNumber(blockNumber);
		} catch (final IOException e) {
			Thread.currentThread().interrupt();
			LOGGER.error(MARKER, "Got IOException when reading record file {}", recordFile, e);
		}
	}

	public static void main(final String[] args) {

		// register constructables and set settings
		try {
			prepare();
		} catch (final ConstructableRegistryException e) {
			LOGGER.error(MARKER, "fail to register constructables.", e);
			return;
		}

		final String logConfigPath = System.getProperty(LOG_CONFIG_PROPERTY);
		final File logConfigFile =
				logConfigPath == null
						? getAbsolutePath().resolve(DEFAULT_LOG_CONFIG).toFile()
						: new File(logConfigPath);
		if (logConfigFile.exists()) {
			final LoggerContext context = (LoggerContext) LogManager.getContext(false);
			context.setConfigLocation(logConfigFile.toURI());

			final String fileName = System.getProperty(FILE_NAME_PROPERTY);
			final String fileDirName = System.getProperty(DIR_PROPERTY);

			try {
				// create directory if necessary
				final File destDir =
						new File(Files.createDirectories(Paths.get(destDirName)).toUri());

				if (fileDirName != null) {
					readAllFiles(fileDirName, destDirName);
				} else {
					readRecordFile(new File(fileName), destDir);
				}
			} catch (final IOException e) {
				LOGGER.error(MARKER, "Got IOException", e);
			}
		}
	}

	/**
	 * Sign all files in the provided directory
	 *
	 * @param sourceDir the directory where the files to read are located
	 */
	public static void readAllFiles(
			final String sourceDir)
			throws IOException {
		// create directory if necessary
		final File destDirFile = new File(Files.createDirectories(Paths.get(destDir)).toUri());

		final File folder = new File(sourceDir);
		final File[] streamFiles = folder.listFiles((dir, name) -> streamType.isStreamFile(name));
		Arrays.sort(streamFiles); // sort by file names and timestamps

		final List<File> totalList = new ArrayList<>();
		totalList.addAll(Arrays.asList(Optional.ofNullable(streamFiles).orElse(new File[0])));
		for (final File item : totalList) {
			readRecordFile(sigKeyPair, item, destDirFile, streamType);
		}
	}
}
