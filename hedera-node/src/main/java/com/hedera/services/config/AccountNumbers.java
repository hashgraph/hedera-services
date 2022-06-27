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

import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;

import javax.inject.Inject;
import javax.inject.Singleton;

import static com.hedera.services.config.EntityNumbers.UNKNOWN_NUMBER;

@Singleton
public class AccountNumbers {
	private final PropertySource properties;

	private long treasury = UNKNOWN_NUMBER;
	private long systemAdmin = UNKNOWN_NUMBER;
	private long freezeAdmin = UNKNOWN_NUMBER;
	private long addressBookAdmin = UNKNOWN_NUMBER;
	private long systemDeleteAdmin = UNKNOWN_NUMBER;
	private long feeSchedulesAdmin = UNKNOWN_NUMBER;
	private long exchangeRatesAdmin = UNKNOWN_NUMBER;
	private long systemUndeleteAdmin = UNKNOWN_NUMBER;
	private long stakingRewardAccount = UNKNOWN_NUMBER;
	private long nodeRewardAccount = UNKNOWN_NUMBER;

	@Inject
	public AccountNumbers(@CompositeProps PropertySource properties) {
		this.properties = properties;
	}

	public long treasury() {
		if (treasury == UNKNOWN_NUMBER) {
			treasury = properties.getLongProperty("accounts.treasury");
		}
		return treasury;
	}

	public long freezeAdmin() {
		if (freezeAdmin == UNKNOWN_NUMBER) {
			freezeAdmin = properties.getLongProperty("accounts.freezeAdmin");
		}
		return freezeAdmin;
	}

	public long systemAdmin() {
		if (systemAdmin == UNKNOWN_NUMBER) {
			systemAdmin = properties.getLongProperty("accounts.systemAdmin");
		}
		return systemAdmin;
	}

	public long addressBookAdmin() {
		if (addressBookAdmin == UNKNOWN_NUMBER) {
			addressBookAdmin = properties.getLongProperty("accounts.addressBookAdmin");
		}
		return addressBookAdmin;
	}

	public long feeSchedulesAdmin() {
		if (feeSchedulesAdmin == UNKNOWN_NUMBER) {
			feeSchedulesAdmin = properties.getLongProperty("accounts.feeSchedulesAdmin");
		}
		return feeSchedulesAdmin;
	}

	public long exchangeRatesAdmin() {
		if (exchangeRatesAdmin == UNKNOWN_NUMBER) {
			exchangeRatesAdmin = properties.getLongProperty("accounts.exchangeRatesAdmin");
		}
		return exchangeRatesAdmin;
	}

	public long systemDeleteAdmin() {
		if (systemDeleteAdmin == UNKNOWN_NUMBER) {
			systemDeleteAdmin = properties.getLongProperty("accounts.systemDeleteAdmin");
		}
		return systemDeleteAdmin;
	}

	public long systemUndeleteAdmin() {
		if (systemUndeleteAdmin == UNKNOWN_NUMBER) {
			systemUndeleteAdmin = properties.getLongProperty("accounts.systemUndeleteAdmin");
		}
		return systemUndeleteAdmin;
	}

	public long stakingRewardAccount() {
		if (stakingRewardAccount == UNKNOWN_NUMBER) {
			stakingRewardAccount = properties.getLongProperty("accounts.stakingRewardAccount");
		}
		return stakingRewardAccount;
	}

	public long nodeRewardAccount() {
		if (nodeRewardAccount == UNKNOWN_NUMBER) {
			nodeRewardAccount = properties.getLongProperty("accounts.nodeRewardAccount");
		}
		return nodeRewardAccount;
	}

	public boolean isSuperuser(long num) {
		return num == treasury() || num == systemAdmin();
	}
}
