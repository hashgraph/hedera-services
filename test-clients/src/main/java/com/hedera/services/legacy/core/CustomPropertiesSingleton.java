package com.hedera.services.legacy.core;

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

public class CustomPropertiesSingleton {

	private static final long DAY_SECS = 24 * 60 * 60;
	private static CustomPropertiesSingleton instance = null;
	private CustomProperties properties;
	private long accountDuration;
	private long fileDurtion;
	private long contractDuration;
	private long updateDurationValue;

	private CustomPropertiesSingleton() {
		this.properties = TestHelper.getApplicationPropertiesNew();
		this.accountDuration = properties.getLong("ACCOUNT_DURATION", DAY_SECS * 30);
		this.fileDurtion = properties.getLong("FILE_DURATION", DAY_SECS * 30);
		this.contractDuration = properties.getLong("CONTRACT_DURATION", DAY_SECS * 30);
		this.updateDurationValue = properties.getLong("UPDATE_DURATION_VALUE", DAY_SECS * 30);
	}

	public static CustomPropertiesSingleton getInstance() {
		if (instance == null) {
			synchronized (CustomPropertiesSingleton.class) {
				if (instance == null) {
					instance = new CustomPropertiesSingleton();
				}
			}
		}
		return instance;
	}

	public CustomProperties getCustomProperties() {
		return properties;
	}

	public long getAccountDuration() {
		return accountDuration;
	}

	public long getFileDurtion() {
		return fileDurtion;
	}

	public long getContractDuration() {
		return contractDuration;
	}

	public long getUpdateDurationValue() {
		return updateDurationValue;
	}
}
