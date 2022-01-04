package com.hedera.services.bdd.spec.queries;

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

import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountBalance;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountInfo;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountRecords;
import com.hedera.services.bdd.spec.queries.crypto.ReferenceType;

public class QueryVerbsWithAlias {
	public static HapiGetAccountInfo getAliasedAccountInfo(final String sourceKey) {
		return new HapiGetAccountInfo(sourceKey, ReferenceType.ALIAS_KEY_NAME);
	}

	public static HapiGetAccountRecords getAliasedAccountRecords(final String sourceKey) {
		return new HapiGetAccountRecords(sourceKey, ReferenceType.ALIAS_KEY_NAME);
	}

	public static HapiGetAccountBalance getAliasedAccountBalance(final String sourceKey) {
		return new HapiGetAccountBalance(sourceKey, ReferenceType.ALIAS_KEY_NAME);
	}
}
