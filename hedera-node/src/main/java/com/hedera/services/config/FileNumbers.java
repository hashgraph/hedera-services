package com.hedera.services.config;

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

import com.hedera.services.context.properties.PropertySource;
import com.hederahashgraph.api.proto.java.FileID;

import static com.hedera.services.config.EntityNumbers.UNKNOWN_NUMBER;

public class FileNumbers {
	private final PropertySource properties;

	private long addressBook = UNKNOWN_NUMBER;
	private long nodeDetails = UNKNOWN_NUMBER;
	private long feeSchedules = UNKNOWN_NUMBER;
	private long exchangeRates = UNKNOWN_NUMBER;
	private long apiPermissions = UNKNOWN_NUMBER;
	private long applicationProperties = UNKNOWN_NUMBER;

	private long systemFileCutoff = UNKNOWN_NUMBER;

	public FileNumbers(PropertySource properties) {
		this.properties = properties;
	}

	public long addressBook() {
		if (addressBook == UNKNOWN_NUMBER) {
			addressBook = properties.getLongProperty("files.addressBook.num");
		}
		return addressBook;
	}

	public long nodeDetails() {
		if (nodeDetails == UNKNOWN_NUMBER) {
			nodeDetails = properties.getLongProperty("files.nodeDetails.num");
		}
		return nodeDetails;
	}

	public long feeSchedules() {
		if (feeSchedules == UNKNOWN_NUMBER) {
			feeSchedules = properties.getLongProperty("files.feeSchedules.num");
		}
		return feeSchedules;
	}

	public long exchangeRates() {
		if (exchangeRates == UNKNOWN_NUMBER) {
			return properties.getLongProperty("files.exchangeRates.num");
		}
		return exchangeRates;
	}

	public long applicationProperties() {
		if (applicationProperties == UNKNOWN_NUMBER) {
			applicationProperties = properties.getLongProperty("files.applicationProperties.num");
		}
		return applicationProperties;
	}

	public long apiPermissions() {
		if (apiPermissions == UNKNOWN_NUMBER) {
			apiPermissions = properties.getLongProperty("files.apiPermissions.num");
		}
		return apiPermissions;
	}

	public boolean isSystem(long num) {
		if (systemFileCutoff == UNKNOWN_NUMBER) {
			systemFileCutoff = properties.getLongProperty("hedera.lastProtectedEntity.num");
		}
		return num <= systemFileCutoff;
	}

	public FileID toFid(long num) {
		return FileID.newBuilder()
				.setRealmNum(properties.getLongProperty("hedera.realm"))
				.setShardNum(properties.getLongProperty("hedera.shard"))
				.setFileNum(num)
				.build();
	}
}
