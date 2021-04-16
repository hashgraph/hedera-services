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

import com.hederahashgraph.api.proto.java.FileID;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Utils {
	enum ServiceType {
		CRYPTO, CONSENSUS, TOKEN, FILE, CONTRACT, SCHEDULED, INVALID
	}

	private static final Map<String, ServiceType> SERVICES_TO_ENUM = Map.ofEntries(
			Map.entry("crypto", ServiceType.CRYPTO),
			Map.entry("consensus", ServiceType.CONSENSUS),
			Map.entry("token", ServiceType.TOKEN),
			Map.entry("file", ServiceType.FILE),
			Map.entry("contract", ServiceType.CONTRACT),
			Map.entry("scheduled", ServiceType.SCHEDULED));
	private static final Set<ServiceType> VALID_SERVICE_TYPES = new HashSet<>(SERVICES_TO_ENUM.values());

	private static final Map<String, Long> NAMES_TO_NUMBERS = Map.ofEntries(
			Map.entry("address-book", 101L),
			Map.entry("addressBook.json", 101L),
			Map.entry("node-details", 102L),
			Map.entry("nodeDetails.json", 102L),
			Map.entry("rates", 112L),
			Map.entry("exchangeRates.json", 112L),
			Map.entry("fees", 111L),
			Map.entry("feeSchedules.json", 111L),
			Map.entry("props", 121L),
			Map.entry("application.properties", 121L),
			Map.entry("permissions", 122L),
			Map.entry("api-permission.properties", 122L),
			Map.entry("throttles", 123L),
			Map.entry("throttles.json", 123L));
	private static final Map<FileID, String> IDS_TO_NAMES = NAMES_TO_NUMBERS.entrySet().stream()
			.filter(entry -> !entry.getKey().contains("."))
			.collect(Collectors.toMap(
					(Map.Entry<String, Long> entry) -> FileID.newBuilder().setFileNum(entry.getValue()).build(),
					Map.Entry::getKey));

	private static final Set<Long> VALID_NUMBERS = new HashSet<>(NAMES_TO_NUMBERS.values());

	public static String nameOf(FileID fid) {
		return Optional.ofNullable(IDS_TO_NAMES.get(fid)).orElse("<N/A>");
	}

	public static EnumSet<ServiceType> rationalizedServices(final String[] services) {
		if (Arrays.asList(services).contains("all")) {
			return EnumSet.copyOf(VALID_SERVICE_TYPES);
		}
		return Arrays.stream(services)
				.map(s -> SERVICES_TO_ENUM.getOrDefault(s, ServiceType.INVALID))
				.peek(s -> {
					if (!VALID_SERVICE_TYPES.contains(s)) {
						throw new IllegalArgumentException("Invalid ServiceType provided!");
					}
				}).collect(Collectors.toCollection(() -> EnumSet.noneOf(ServiceType.class)));
	}

	public static long rationalized(String sysfile) {
		long fileId;
		try {
			fileId = Long.parseLong(sysfile);
		} catch (Exception e) {
			fileId = NAMES_TO_NUMBERS.getOrDefault(sysfile, 0L);
		}
		if (!VALID_NUMBERS.contains(fileId)) {
			throw new IllegalArgumentException("No such system file '" + sysfile + "'!");
		}
		return fileId;
	}

	public static long[] rationalized(String[] sysfiles) {
		if (Arrays.asList(sysfiles).contains("all")) {
			return VALID_NUMBERS.stream().mapToLong(Number::longValue).toArray();
		}

		return Arrays.stream(sysfiles)
				.map(Utils::getFileId)
				.peek(num -> {
					if (!VALID_NUMBERS.contains(num)) {
						throw new IllegalArgumentException("No such system file '" + num + "'!");
					}
				}).mapToLong(l -> l).toArray();
	}

	private static long getFileId(String file) {
		long fileId;
		try {
			fileId = Long.parseLong(file);
		} catch (Exception e) {
			fileId = NAMES_TO_NUMBERS.getOrDefault(file, 0L);
			if (fileId == 0) {
				throw new IllegalArgumentException("No such system file '" + file + "'!");
			}
		}
		return fileId;
	}
}
