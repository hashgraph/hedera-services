package com.hedera.services.legacy.handler;

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

import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.files.HederaFs;
import com.hedera.services.utils.UnzipUtility;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.builder.RequestBuilder;
import com.swirlds.common.Platform;
import com.swirlds.common.SwirldDualState;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.function.Supplier;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static com.hederahashgraph.builder.RequestBuilder.getTransactionReceipt;
import static com.swirlds.common.CommonUtils.hex;
import static java.util.Calendar.HOUR_OF_DAY;
import static java.util.Calendar.MINUTE;

/**
 * 		FreezeHandler is used in the HGCApp handleTransaction for performing Freeze
 * 		transactions. Documentation available at index.html#proto.FreezeTransactionBody
 */
@Singleton
public class FreezeHandler {
	private static final Logger log = LogManager.getLogger(FreezeHandler.class);
	private static final String TARGET_DIR = "./";
	private static final String TEMP_DIR = "./temp";
	private static final String DELETE_FILE = TEMP_DIR + File.separator + "delete.txt";
	private static final String CMD_SCRIPT = "exec.sh";
	private static final String FULL_SCRIPT_PATH = TEMP_DIR + File.separator + CMD_SCRIPT;
	private static final String ABORT_UDPATE_MESSAGE = "ABORT UPDATE PROCRESS";
	private final Platform platform;
	private final HederaFs hfs;
	private final HbarCentExchange exchange;
	private final Supplier<SwirldDualState> dualState;
	private String LOG_PREFIX;

	private FileID updateFeatureFile;
	private byte[] updateFileHash;

	@Inject
	public FreezeHandler(
			final HederaFs hfs,
			final Platform platform,
			final HbarCentExchange exchange,
			final Supplier<SwirldDualState> dualState
	) {
		this.hfs = hfs;
		this.exchange = exchange;
		this.platform = platform;
		this.dualState = dualState;
		LOG_PREFIX = "NETWORK_UPDATE Node " + platform.getSelfId();
	}

	public TransactionRecord freeze(final TransactionBody transactionBody, final Instant consensusTime) {
		log.debug("FreezeHandler - Handling FreezeTransaction: {}", transactionBody);
		saveUpdateFileForMaintenance(transactionBody);
		final var naturalFreezeStart = deriveFreezeStartTime(transactionBody.getFreeze(), consensusTime);
		TransactionReceipt receipt;
		try {
			final var dual = dualState.get();
			dual.setFreezeTime(naturalFreezeStart);
			receipt = getTransactionReceipt(ResponseCodeEnum.SUCCESS, exchange.activeRates());
			log.info("Dual state freeze time set to {} (now is {})", naturalFreezeStart, consensusTime);
		} catch (Exception e) {
			log.warn("Could not set dual state freeze time to {} (now is {})", naturalFreezeStart, consensusTime, e);
			throw new InvalidTransactionException(INVALID_FREEZE_TRANSACTION_BODY);
		}
		final var builder = RequestBuilder.getTransactionRecord(
				transactionBody.getTransactionFee(),
				transactionBody.getMemo(),
				transactionBody.getTransactionID(),
				RequestBuilder.getTimestamp(consensusTime),
				receipt);
		return builder.build();
	}

	private void saveUpdateFileForMaintenance(final TransactionBody transactionBody) {
		if (transactionBody.getFreeze().hasUpdateFile()) {
			updateFeatureFile = transactionBody.getFreeze().getUpdateFile();
			updateFileHash = transactionBody.getFreeze().getFileHash().toByteArray();
		}
	}

	private Instant deriveFreezeStartTime(final FreezeTransactionBody op, final Instant consensusTime) {
		if (op.hasStartTime()) {
			final var ts = op.getStartTime();
			return Instant.ofEpochSecond(ts.getSeconds(), ts.getNanos());
		}
		return nextNaturalInstant(consensusTime, op.getStartHour(), op.getStartMin());
	}

	private Instant nextNaturalInstant(final Instant now, final int nominalHour, final int nominalMin) {
		final var minsSinceMidnightNow = minutesSinceMidnight(now);
		final var nominalMinsSinceMidnight = nominalHour * 60 + nominalMin;
		int diffMins = nominalMinsSinceMidnight - minsSinceMidnightNow;
		if (diffMins < 0) {
			/* Can't go back in time, so add a day's worth of minutes to hit the nominal time tomorrow */
			diffMins += 24 * 60;
		}
		return now.plusSeconds(diffMins * 60L);
	}

	public int minutesSinceMidnight(final Instant now) {
		final var calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
		calendar.setTimeInMillis(now.getEpochSecond() * 1_000);
		return calendar.get(HOUR_OF_DAY) * 60 + calendar.get(MINUTE);
	}

	public void handleUpdateFeature() {
		if (updateFeatureFile == null) {
			log.info("{} Update file id is not defined, no update will be conducted", LOG_PREFIX);
			return;
		}
		log.info("{} Running update with FileID {}", LOG_PREFIX, updateFeatureFile);

		final var fileIDtoUse = updateFeatureFile;
		updateFeatureFile = null; // reset to null since next freeze may not need file update
		if (hfs.exists(fileIDtoUse)) {
			log.info("{} ready to read file content, FileID = {}", LOG_PREFIX, fileIDtoUse);
			final var fileBytes = hfs.cat(fileIDtoUse);
			if (fileBytes == null || fileBytes.length == 0) {
				logOnEmptyUpdateFile();
				return;
			}
			try {
				final var readFileHash = MessageDigest.getInstance("SHA-384").digest(fileBytes);
				if (Arrays.equals(readFileHash, updateFileHash)) {
					updateFeatureWithFileContents(fileBytes);
				} else {
					logOnFileHashMismatch(readFileHash);
				}
			} catch (NoSuchAlgorithmException e) {
				log.error("{} Exception {}", LOG_PREFIX, e);
			}
		} else {
			log.error("{} File ID {} not found in file system", LOG_PREFIX, fileIDtoUse);
		}
	}

	private void logOnEmptyUpdateFile() {
		log.error("{} Update file is empty", LOG_PREFIX);
		log.error("{} {}", LOG_PREFIX, ABORT_UDPATE_MESSAGE);
	}

	private void logOnFileHashMismatch(final byte[] readFileHash) {
		log.error("{} File hash mismatch", LOG_PREFIX);
		log.error("{} Hash from transaction body {}", LOG_PREFIX, hex(updateFileHash));
		log.error("{} Hash from file system {}", LOG_PREFIX, hex(readFileHash));
		log.error("{} {}", LOG_PREFIX, ABORT_UDPATE_MESSAGE);
	}

	private void updateFeatureWithFileContents(final byte[] fileBytes) {
		log.info("{} current directory {}", LOG_PREFIX, System.getProperty("user.dir"));
		try {
			prepareEmptyTempDir();
			log.info("{} has read file content {} bytes", LOG_PREFIX, fileBytes.length);
			log.info("{} unzipping file to directory {} ", LOG_PREFIX, TEMP_DIR);
			UnzipUtility.unzip(fileBytes, TEMP_DIR);
			deleteFiles();
			executeScriptFile();
		} catch (SecurityException | IOException e) {
			log.error("Exception during handleUpdateFeature ", e);
		}
	}

	private void prepareEmptyTempDir() throws IOException {
		final var directory = new File(TEMP_DIR);
		log.info("{} prepare empty directory {}", LOG_PREFIX, directory);
		if (directory.exists()) {
			FileUtils.deleteDirectory(directory);
		}
		directory.mkdir();
	}

	private void deleteFiles() {
		final var deleteTxt = new File(DELETE_FILE);
		if (deleteTxt.exists()) {
			log.info("{} executing delete file list {}", LOG_PREFIX, DELETE_FILE);
			deleteFilesFromList(DELETE_FILE);

			log.info("{} deleting files from {}", LOG_PREFIX, DELETE_FILE);
			try {
				Files.delete(Paths.get(deleteTxt.getAbsolutePath()));
			} catch (IOException ex) {
				log.warn("{} File could not be deleted {}", DELETE_FILE, ex);
			}
		}
	}

	private void executeScriptFile() {
		final var script = new File(FULL_SCRIPT_PATH);
		if (script.exists()) {
			if (script.setExecutable(true)) {
				log.info("{} ready to execute script {}", LOG_PREFIX, FULL_SCRIPT_PATH);
				runScript(FULL_SCRIPT_PATH);
			} else {
				log.error("{} could not change to executable permission for file {}", LOG_PREFIX, FULL_SCRIPT_PATH);
			}
		}
	}

	private void deleteFilesFromList(final String deleteFileListName) {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(
				new FileInputStream(deleteFileListName), StandardCharsets.UTF_8));) {
			String line;
			// each line of the input stream is a file name to be deleted
			while ((line = br.readLine()) != null) {

				if (line.contains("..")) {
					log.warn("{} skip delete file {} located in parent directory ", LOG_PREFIX, line);
				} else {
					String fullPath = TARGET_DIR + File.separator + line;
					File file = new File(fullPath);
					log.info("{} deleting file  {}", LOG_PREFIX, fullPath);
					if (file.exists()) {
						if (file.delete()) {
							log.info("{} successfully deleted file {}", LOG_PREFIX, fullPath);
						} else {
							log.error("{} could not delete file {}", LOG_PREFIX, fullPath);
						}
					}
				}
			}
		} catch (SecurityException | IOException e) {
			log.error("Delete file exception ", e);
		}
	}

	private void runScript(final String scriptFullPath) {
		try {
			log.info("{} start running script: {}", LOG_PREFIX, scriptFullPath);
			Runtime.getRuntime().exec(" nohup " + scriptFullPath + " " + platform.getSelfId().getId());
		} catch (SecurityException | NullPointerException | IllegalArgumentException | IOException e) {
			log.error("{} run script exception ", LOG_PREFIX, e);
		}
	}
}
