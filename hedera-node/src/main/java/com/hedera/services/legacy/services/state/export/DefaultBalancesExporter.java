package com.hedera.services.legacy.services.state.export;

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

import com.hedera.services.legacy.export.AccountBalanceExport;
import com.hedera.services.state.exports.BalancesExporter;
import com.hedera.services.ServicesState;
import com.swirlds.common.AddressBook;
import com.swirlds.common.Platform;

import java.time.Instant;

public class DefaultBalancesExporter implements BalancesExporter {
	private final Platform platform;
	private final AccountBalanceExport delegate;

	public DefaultBalancesExporter(Platform platform, AddressBook addressBook) {
		this.platform = platform;
		delegate = new AccountBalanceExport(addressBook);
	}

	@Override
	public void toCsvFile(ServicesState signedState, Instant when) {
		String file = delegate.exportAccountsBalanceCSVFormat(signedState, when);
		if (file != null) {
			delegate.signAccountBalanceFile(platform, file);
		}
	}

	@Override
	public boolean isTimeToExport(Instant now) {
		return delegate.timeToExport(now);
	}
}
