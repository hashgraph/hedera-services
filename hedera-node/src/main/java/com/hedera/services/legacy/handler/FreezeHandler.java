package com.hedera.services.legacy.handler;

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
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Arrays;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY;
import static com.hederahashgraph.builder.RequestBuilder.getTransactionReceipt;
import static com.swirlds.common.CommonUtils.hex;

/**
 * @author Qian
 * 		FreezeHandler is used in the HGCApp handleTransaction for performing Freeze
 * 		transactions. Documentation available at index.html#proto.FreezeTransactionBody
 */
public class FreezeHandler {
	public static Logger log = LogManager.getLogger(FreezeHandler.class);

	private final Platform platform;
	private final HederaFs hfs;
	private final HbarCentExchange exchange;

	private static String TARGET_DIR = "./";
	private static String TEMP_DIR = "./temp";
	private static String TEMP_SDK_DIR = TEMP_DIR + File.separator + "sdk";
	private static String DELETE_FILE = TEMP_DIR + File.separator + "delete.txt";
	private static String CMD_SCRIPT = "exec.sh";
	private static String FULL_SCRIPT_PATH = TEMP_DIR + File.separator + CMD_SCRIPT;
	private String LOG_PREFIX;
	private static String ABORT_UDPATE_MESSAGE = "ABORT UPDATE PROCRESS";

	private FileID updateFeatureFile;
	private byte[] updateFileHash;

	public FreezeHandler(
			HederaFs hfs,
			Platform platform,
			HbarCentExchange exchange
	) {

		this.exchange = exchange;
		this.platform = platform;
		this.hfs = hfs;
		LOG_PREFIX = "NETWORK_UPDATE Node " + platform.getSelfId();
	}

	public TransactionRecord freeze(final TransactionBody transactionBody, final Instant consensusTime) {
		log.debug("FreezeHandler - Handling FreezeTransaction: {}", transactionBody);
		FreezeTransactionBody freezeBody = transactionBody.getFreeze();
		TransactionReceipt receipt;
		if (transactionBody.getFreeze().hasUpdateFile()) {
			//save the file ID and will be used after platform goes into maintenance mode
			updateFeatureFile = transactionBody.getFreeze().getUpdateFile();
			updateFileHash = transactionBody.getFreeze().getFileHash().toByteArray();
		}
		try {
			platform.setFreezeTime(
					freezeBody.getStartHour(),
					freezeBody.getStartMin(),
					freezeBody.getEndHour(),
					freezeBody.getEndMin());
			receipt = getTransactionReceipt(ResponseCodeEnum.SUCCESS, exchange.activeRates());
			log.info("Freeze time starts {}:{} end {}:{}", freezeBody.getStartHour(),
					freezeBody.getStartMin(),
					freezeBody.getEndHour(),
					freezeBody.getEndMin());
		} catch (IllegalArgumentException ex) {
			log.warn("FreezeHandler - freeze fails. {}", ex.getMessage());
			receipt = getTransactionReceipt(INVALID_FREEZE_TRANSACTION_BODY, exchange.activeRates());
		}

		TransactionRecord.Builder transactionRecord = RequestBuilder.getTransactionRecord(
				transactionBody.getTransactionFee(),
				transactionBody.getMemo(),
				transactionBody.getTransactionID(),
				RequestBuilder.getTimestamp(consensusTime), receipt);
		return transactionRecord.build();
	}

	public void handleUpdateFeature() {
		if (updateFeatureFile == null) {
			log.info(String.format("%s Update file id is not defined, no update will be conducted", LOG_PREFIX));
			return;
		}
		log.info("{} Running update with FileID {}", LOG_PREFIX, updateFeatureFile);

		FileID fileIDtoUse = updateFeatureFile;
		updateFeatureFile = null; // reset to null since next freeze may not need file update
		if (hfs.exists(fileIDtoUse)) {
			log.info("{} ready to read file content, FileID = {}", LOG_PREFIX, fileIDtoUse);
			byte[] fileBytes = hfs.cat(fileIDtoUse);
			if ( fileBytes == null || fileBytes.length == 0) {
				log.error(String.format("%s Update file is empty", LOG_PREFIX));
				log.error("{} {}", LOG_PREFIX, ABORT_UDPATE_MESSAGE);
				return;
			}
			try {
				byte[] readFileHash = MessageDigest.getInstance("SHA-384").digest(fileBytes);
				if (Arrays.equals(readFileHash, updateFileHash)) {
					updateFeatureWithFileContents(fileBytes);
				} else {
					log.error(String.format("%s File hash mismatch", LOG_PREFIX));
					log.error("{} Hash from transaction body {}", LOG_PREFIX, hex(updateFileHash));
					log.error("{} Hash from file system {}", LOG_PREFIX, hex(readFileHash));
					log.error("{} {}", LOG_PREFIX, ABORT_UDPATE_MESSAGE);
				}
			} catch (NoSuchAlgorithmException e) {
				log.error("{} Exception {}", LOG_PREFIX, e);
			}
		} else {
			log.error(String.format("%s File ID %s not found in file system ", LOG_PREFIX, fileIDtoUse));
		}
	}

	private void updateFeatureWithFileContents(final byte[] fileBytes) {
		log.info("{} current directory {}", LOG_PREFIX, System.getProperty("user.dir"));
		File directory = new File(TEMP_DIR);
		try {
			if (directory.exists()) {
				log.info("{} clean directory {}", LOG_PREFIX, directory);
				// delete everything in it recursively
				FileUtils.cleanDirectory(directory);
			} else {
				log.info("{} create directory {}", LOG_PREFIX, directory);
				directory.mkdir();
			}
			log.info("{} has read file content {} bytes", LOG_PREFIX, fileBytes.length);

			log.info("{} unzipping file to directory {} ", LOG_PREFIX, TEMP_DIR);
			//unzip bytes stream to target directory
			UnzipUtility.unzip(fileBytes, TEMP_DIR);

			File deleteTxt = new File(DELETE_FILE);
			if (deleteTxt.exists()) {
				log.info("{} executing delete file list {}", LOG_PREFIX, DELETE_FILE);
				deleteFileFromList(DELETE_FILE);

				log.info("{} deleting file {}", LOG_PREFIX, DELETE_FILE);
				deleteTxt.delete();
			}

			File script = new File(FULL_SCRIPT_PATH);
			if (script.exists()) {
				if (script.setExecutable(true)) {
					log.info("{} ready to execute script {}", LOG_PREFIX, FULL_SCRIPT_PATH);
					runScript(FULL_SCRIPT_PATH);
				} else {
					log.error("{} could not change to executable permission for file {}", LOG_PREFIX, FULL_SCRIPT_PATH);
				}
			}
		} catch (SecurityException | IOException e) {
			log.error("Exception during handleUpdateFeature ", e);
		}
	}

	private void deleteFileFromList(String deleteFileListName) {
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

	private void runScript(String scriptFullPath) {
		try {
			log.info("{} start running script: {}", LOG_PREFIX, scriptFullPath);
			Runtime.getRuntime().exec(" nohup " + scriptFullPath + " " + platform.getSelfId().getId());
		} catch (SecurityException | NullPointerException | IllegalArgumentException | IOException e) {
			log.error("{} run script exception ", LOG_PREFIX, e);
		}
	}
}
