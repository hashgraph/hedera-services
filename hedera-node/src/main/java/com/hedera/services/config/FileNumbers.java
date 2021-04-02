package com.hedera.services.config;

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

import com.hedera.services.context.properties.PropertySource;
import com.hederahashgraph.api.proto.java.FileID;

import static com.hedera.services.config.EntityNumbers.UNKNOWN_NUMBER;

public class FileNumbers {
	private final HederaNumbers hederaNums;
	private final PropertySource properties;

	private long addressBook = UNKNOWN_NUMBER;
	private long nodeDetails = UNKNOWN_NUMBER;
	private long feeSchedules = UNKNOWN_NUMBER;
	private long exchangeRates = UNKNOWN_NUMBER;
	private long apiPermissions = UNKNOWN_NUMBER;
	private long applicationProperties = UNKNOWN_NUMBER;
	private long softwareUpdateZip = UNKNOWN_NUMBER;
	private long throttleDefinitions = UNKNOWN_NUMBER;

	public FileNumbers(HederaNumbers hederaNums, PropertySource properties) {
		this.hederaNums = hederaNums;
		this.properties = properties;
	}

	public long addressBook() {
		if (addressBook == UNKNOWN_NUMBER) {
			addressBook = properties.getLongProperty("files.addressBook");
		}
		return addressBook;
	}

	public long nodeDetails() {
		if (nodeDetails == UNKNOWN_NUMBER) {
			nodeDetails = properties.getLongProperty("files.nodeDetails");
		}
		return nodeDetails;
	}

	public long feeSchedules() {
		if (feeSchedules == UNKNOWN_NUMBER) {
			feeSchedules = properties.getLongProperty("files.feeSchedules");
		}
		return feeSchedules;
	}

	public long exchangeRates() {
		if (exchangeRates == UNKNOWN_NUMBER) {
			return properties.getLongProperty("files.exchangeRates");
		}
		return exchangeRates;
	}

	public long applicationProperties() {
		if (applicationProperties == UNKNOWN_NUMBER) {
			applicationProperties = properties.getLongProperty("files.networkProperties");
		}
		return applicationProperties;
	}

	public long apiPermissions() {
		if (apiPermissions == UNKNOWN_NUMBER) {
			apiPermissions = properties.getLongProperty("files.hapiPermissions");
		}
		return apiPermissions;
	}

	public long softwareUpdateZip() {
		if (softwareUpdateZip == UNKNOWN_NUMBER) {
			softwareUpdateZip = properties.getLongProperty("files.softwareUpdateZip");
		}
		return softwareUpdateZip;
	}

	public long throttleDefinitions() {
		if (throttleDefinitions == UNKNOWN_NUMBER) {
			throttleDefinitions = properties.getLongProperty("files.throttleDefinitions");
		}
		return throttleDefinitions;
	}

	public FileID toFid(long num) {
		return FileID.newBuilder()
				.setShardNum(hederaNums.shard())
				.setRealmNum(hederaNums.realm())
				.setFileNum(num)
				.build();
	}
}
