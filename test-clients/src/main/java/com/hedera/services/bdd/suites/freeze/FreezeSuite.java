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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.legacy.regression.Utilities;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileCreate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freeze;
import static com.hedera.services.bdd.suites.utils.ZipUtil.createZip;


public class FreezeSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(FreezeSuite.class);

	private static final String UPLOAD_PATH_PREFIX = "src/main/resource/testfiles/updateFeature/";
	private static final String UPDATE_NEW_FILE = UPLOAD_PATH_PREFIX + "addNewFile/newFile.zip";
	private static String zipFile = "Archive.zip";

	private int startHour;
	private int startMin;
	private int endHour;
	private int endMin;
	private static String uploadPath = "updateSettings";

	public static void main(String... args) {

		if (args.length > 0) {
			uploadPath = args[0];
		}
		new FreezeSuite().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveTests(),
				negativeTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return Arrays.asList(
		);
	}

	private List<HapiApiSpec> negativeTests() {
		return Arrays.asList(
				uploadNewFile()
		);
	}

	private HapiApiSpec uploadNewFile() {
		String uploadFile = UPDATE_NEW_FILE;
		generateFreezeTime(1);
		if (uploadPath != null) {
			log.info("Creating zip file from " + uploadPath);
			createZip(UPLOAD_PATH_PREFIX + uploadPath, zipFile, null);
			uploadFile = zipFile;
		}

		log.info("Uploading file " + uploadFile);
		return defaultHapiSpec("uploadFileAndUpdate")
				.given(
						fileCreate("newFile.zip").path(uploadFile)
				).when(
						freeze().setFileName("newFile.zip")
								.startingIn(60).seconds().andLasting(1).minutes()
				).then(
				);
	}

	private void generateFreezeTime(int freezeDurationMinutes) {
		long freezeStartTimeMillis = System.currentTimeMillis() + 60000l;
		int[] startHourMin = Utilities.getUTCHourMinFromMillis(freezeStartTimeMillis);
		startHour = startHourMin[0];
		startMin = startHourMin[1];
		endMin = startMin + freezeDurationMinutes;
		endHour = (startHour + endMin / 60) % 24;
		endMin = endMin % 60;
	}
}
