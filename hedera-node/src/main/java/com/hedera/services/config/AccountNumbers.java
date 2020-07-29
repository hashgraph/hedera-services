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

import static com.hedera.services.config.EntityNumbers.UNKNOWN_NUMBER;

public class AccountNumbers {

	private final PropertySource properties;

	private long treasury = UNKNOWN_NUMBER;
	private long systemAdmin = UNKNOWN_NUMBER;
	private long freezeAdmin = UNKNOWN_NUMBER;
	private long addressBookAdmin = UNKNOWN_NUMBER;
	private long feeSchedulesAdmin = UNKNOWN_NUMBER;
	private long exchangeRatesAdmin = UNKNOWN_NUMBER;

	public AccountNumbers(PropertySource properties) {
		this.properties = properties;
	}

	public long treasury() {
		if (treasury == UNKNOWN_NUMBER) {
			treasury = properties.getLongProperty("bootstrap.accounts.treasury");
		}
		return treasury;
	}

	public long freezeAdmin() {
		if (freezeAdmin == UNKNOWN_NUMBER) {
			freezeAdmin = properties.getLongProperty("bootstrap.accounts.freezeAdmin");
		}
		return freezeAdmin;
	}

	public long systemAdmin() {
		if (systemAdmin == UNKNOWN_NUMBER) {
			systemAdmin = properties.getLongProperty("bootstrap.accounts.systemAdmin");
		}
		return systemAdmin;
	}

	public long addressBookAdmin() {
		if (addressBookAdmin == UNKNOWN_NUMBER) {
			addressBookAdmin = properties.getLongProperty("bootstrap.accounts.addressBookAdmin");
		}
		return addressBookAdmin;
	}

	public long feeSchedulesAdmin() {
		if (feeSchedulesAdmin == UNKNOWN_NUMBER) {
			feeSchedulesAdmin = properties.getLongProperty("bootstrap.accounts.feeSchedulesAdmin");
		}
		return feeSchedulesAdmin;
	}

	public long exchangeRatesAdmin() {
		if (exchangeRatesAdmin == UNKNOWN_NUMBER) {
			exchangeRatesAdmin = properties.getLongProperty("bootstrap.accounts.exchangeRatesAdmin");
		}
		return exchangeRatesAdmin;
	}

	public boolean isSysAdmin(long num) {
		return num == treasury() || num == systemAdmin();
	}
}
