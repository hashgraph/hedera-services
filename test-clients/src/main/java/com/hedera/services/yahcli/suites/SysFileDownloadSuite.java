package com.hedera.services.yahcli.suites;

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
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.queries.file.HapiGetFileContents;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.sysfiles.AddressBookPojo;
import com.hedera.services.bdd.suites.utils.sysfiles.serdes.AddrBkJsonToGrpcBytes;
import com.hedera.services.bdd.suites.utils.sysfiles.serdes.SysFileSerde;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.AddrBkJsonToGrpcBytes.setAppropriateVersion;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.StandardSerdes.SYS_FILE_SERDES;

public class SysFileDownloadSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SysFileDownloadSuite.class);

	private final Map<String, Long> NAMES_TO_NUMBERS = Map.ofEntries(
			Map.entry("book", 101L),
			Map.entry("details", 102L),
			Map.entry("rates", 112L),
			Map.entry("fees", 111L),
			Map.entry("props", 121L),
			Map.entry("permissions", 122L));
	private final Set<Long> VALID_NUMBERS = new HashSet<>(NAMES_TO_NUMBERS.values());

	private final String destDir;
	private final Map<String, String> specConfig;
	private final String[] sysFilesToDownload;
	private final String version;

	public SysFileDownloadSuite(
			String destDir,
			Map<String, String> specConfig,
			String[] sysFilesToDownload,
			String version
	) {
		this.destDir = destDir;
		this.specConfig = specConfig;
		this.sysFilesToDownload = sysFilesToDownload;
		this.version = version;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				downloadSysFiles(),
		});
	}

	private HapiApiSpec downloadSysFiles() {
		long[] targets = rationalized(sysFilesToDownload);

		return HapiApiSpec.customHapiSpec("downloadSysFiles").withProperties(
				specConfig
		).given().when().then(
				Arrays.stream(targets)
						.mapToObj(this::appropriateQuery)
						.toArray(HapiSpecOperation[]::new)
		);
	}

	private HapiGetFileContents appropriateQuery(long fileNum) {
		String fid = String.format("0.0.%d", fileNum);
		SysFileSerde<String> serde = SYS_FILE_SERDES.get(fileNum);
		setAppropriateVersion(version);
		String fqLoc = destDir + File.separator + serde.preferredFileName();
		return getFileContents(fid).saveReadableTo(serde::fromRawFile, fqLoc);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	private long[] rationalized(String[] sysfiles) {
		if(Arrays.asList(sysfiles).contains("all")) {
			return VALID_NUMBERS.stream().mapToLong(Number::longValue).toArray();
		}

		return Arrays.stream(sysfiles)
				.map(this::getFileId)
				.peek(num -> {
					if (!VALID_NUMBERS.contains(num)) {
						throw new IllegalArgumentException("No such system file '" + num + "'!");
					}
				}).mapToLong(l -> l).toArray();
	}

	private long getFileId(String file) {
		long fileId;
		try {
			fileId = Long.parseLong(file);
		} catch (Exception e) {
			fileId = NAMES_TO_NUMBERS.getOrDefault(file, 0L);
			// TODO if fileId is 0 , throw an exception mentioning the file name received.
		}
		return fileId;
	}
}
