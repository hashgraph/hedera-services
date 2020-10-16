package com.hedera.services.bdd.suites.freeze;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freeze;
import static com.hedera.services.bdd.suites.utils.ZipUtil.createZip;
import static junit.framework.TestCase.fail;


public class UpdateServerFiles extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(UpdateServerFiles.class);
	private static String zipFile = "Archive.zip";
	private static final String DEFAULT_SCRIPT = "src/main/resource/testfiles/updateFeature/updateSettings/exec.sh";

	private static String uploadPath = "updateFiles/";

	private static int FREEZE_LAST_MINUTES = 2;
	private static String fileIDString = "UPDATE_FEATURE"; // mnemonic for file 0.0.150

	public static void main(String... args) {

		if (args.length > 0) {
			uploadPath = args[0];
		}

		if (args.length > 1) {
			fileIDString = args[1];
		}

		new UpdateServerFiles().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				postiveTests()
		);
	}

	private List<HapiApiSpec> postiveTests() {
		return Arrays.asList(
				uploadGivenDirectory()
		);
	}

	// Zip all files under target directory and add an unzip and launch script to it
	// then send to server to update server
	private HapiApiSpec uploadGivenDirectory() {

		log.info("Creating zip file from " + uploadPath);
		//create directory if uploadPath doesn't exist
		if (!new File(uploadPath).exists()) {
			new File(uploadPath).mkdirs();
		}
		final String temp_dir = "temp/";
		final String sdk_dir = temp_dir + "sdk/";
		byte[] data = null;
		try {
			//create a temp sdk directory
			File directory = new File(temp_dir);
			if (directory.exists()) {
				// delete everything in it recursively

				FileUtils.cleanDirectory(directory);

			} else {
				directory.mkdir();
			}

			(new File(sdk_dir)).mkdir();
			//copy files to sdk directory
			FileUtils.copyDirectory(new File(uploadPath), new File(sdk_dir));
			createZip(temp_dir, zipFile, DEFAULT_SCRIPT);
			String uploadFile = zipFile;

			log.info("Uploading file " + uploadFile);
			data = Files.readAllBytes(Paths.get(uploadFile));
		} catch (IOException e) {
			log.error("Directory creation failed", e);
			fail("Directory creation failed");
		}
		final byte[] hash = UpdateFile150.sha384Digest(data);
		return defaultHapiSpec("uploadFileAndUpdate")
				.given(
						fileUpdate(APP_PROPERTIES).payingWith(GENESIS)
								.overridingProps(Map.of("maxFileSize", "2048000")),
						UtilVerbs.updateLargeFile(GENESIS, fileIDString, ByteString.copyFrom(data))
				).when(
						freeze().setFileID(fileIDString)
								.setFileHash(hash).payingWith(GENESIS)
								.startingIn(60).seconds().andLasting(10).minutes()
				).then(
				);
	}
}
