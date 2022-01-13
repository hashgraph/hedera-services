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

import com.google.protobuf.ByteString;
import com.hedera.services.context.annotations.CompositeProps;
import com.hedera.services.context.properties.PropertySource;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NetworkInfo {
	// Default the ledgerID to dev/preprod
	private static final String DEFAULT_LEDGER_ID = "0x03";

	private final PropertySource properties;
	private ByteString ledgerId;

	@Inject
	public NetworkInfo(@CompositeProps PropertySource properties) {
		this.properties = properties;
	}

	public ByteString ledgerId() {
		if (ledgerId == null) {
			if (properties.containsProperty("ledger.id")) {
				ledgerId = ByteString.copyFromUtf8(properties.getStringProperty("ledger.id"));
			} else {
				ledgerId = ByteString.copyFromUtf8(DEFAULT_LEDGER_ID);
			}
		}
		return ledgerId;
	}
}
