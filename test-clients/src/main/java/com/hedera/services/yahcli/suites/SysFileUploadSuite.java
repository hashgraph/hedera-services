package com.hedera.services.yahcli.suites;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.bdd.suites.utils.sysfiles.BookEntryPojo;
import com.hedera.services.bdd.suites.utils.sysfiles.serdes.SysFileSerde;
import com.hederahashgraph.api.proto.java.NodeAddress;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.updateLargeFile;
import static com.hedera.services.bdd.suites.utils.sysfiles.serdes.StandardSerdes.SYS_FILE_SERDES;

public class SysFileUploadSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(SysFileUploadSuite.class);

	private static final Map<String, Long> NAMES_TO_NUMBERS = Map.ofEntries(
			Map.entry("book", 101L),
			Map.entry("addressBook.json", 101L),
			Map.entry("details", 102L),
			Map.entry("nodeDetails.json", 102L),
			Map.entry("rates", 112L),
			Map.entry("exchangeRates.json", 112L),
			Map.entry("fees", 111L),
			Map.entry("feeSchedules.json", 111L),
			Map.entry("props", 121L),
			Map.entry("application.properties", 121L),
			Map.entry("permissions", 122L),
			Map.entry("api-permission.properties", 122L));

	private static final Set<Long> VALID_NUMBERS = new HashSet<>(NAMES_TO_NUMBERS.values());

	static Map<String, Function<BookEntryPojo, Stream<NodeAddress>>> updateConverters = new HashMap<>(Map.of(
			"addressBook.json", BookEntryPojo::toAddressBookEntries,
			"nodeDetails.json", BookEntryPojo::toNodeDetailsEntry));

	private final String srcDir;
	private final Map<String, String> specConfig;
	private final long sysFileId;

	public SysFileUploadSuite(final String srcDir, final Map<String, String> specConfig, final String sysFile) {
		this.srcDir = srcDir;
		this.specConfig = specConfig;
		this.sysFileId = rationalized(sysFile);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				uploadSysFiles(),
		});
	}

	private HapiApiSpec uploadSysFiles() {
		final ByteString uploadData = appropriateContents(sysFileId);

		return HapiApiSpec.customHapiSpec(String.format("UploadSystemFile-%s", sysFileId)).withProperties(
				specConfig
		).given().when().then(
				updateLargeFile(
						DEFAULT_PAYER,
						String.format("0.0.%d",sysFileId),
						uploadData,
						true,
						OptionalLong.of(10_000_000_000L)
				)
		);
	}

	private ByteString appropriateContents(final Long fileNum) {
		SysFileSerde<String> serde = SYS_FILE_SERDES.get(fileNum);
		String name = serde.preferredFileName();
		String loc = srcDir + File.separator + name;
		try {
			var stylized = Files.readString(Paths.get(loc));
			return ByteString.copyFrom(serde.toRawFile(stylized));
		} catch (IOException e) {
			throw new IllegalStateException("Cannot read update file @ '" + loc + "'!", e);
		}
	}

	private long rationalized(String sysfile) {
		long fileId;
		try{
			fileId = Long.parseLong(sysfile);
		} catch (Exception e) {
			fileId = NAMES_TO_NUMBERS.getOrDefault(sysfile, 0L);
		}
		if (!VALID_NUMBERS.contains(fileId)) {
			throw new IllegalArgumentException("No such system file '" + fileId + "'!");
		}
		return fileId;
	}
}
