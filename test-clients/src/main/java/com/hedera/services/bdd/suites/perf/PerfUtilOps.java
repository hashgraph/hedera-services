package com.hedera.services.bdd.suites.perf;

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

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.NOISY_RETRY_PRECHECKS;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiApiSuite.API_PERMISSIONS;
import static com.hedera.services.bdd.suites.HapiApiSuite.GENESIS;
import static java.util.Map.entry;

public class PerfUtilOps {
	public static HapiSpecOperation stdMgmtOf(
			AtomicLong duration,
			AtomicReference<TimeUnit> unit,
			AtomicInteger maxOpsPerSec
	) {
		return withOpContext((spec, opLog) -> {
			var ciProps = spec.setup().ciPropertiesMap();
			if (ciProps.has("duration")) {
				duration.set(ciProps.getLong("duration"));
			}
			if (ciProps.has("unit")) {
				unit.set(ciProps.getTimeUnit("unit"));
			}
			if (ciProps.has("maxOpsPerSec")) {
				maxOpsPerSec.set(ciProps.getInteger("maxOpsPerSec"));
			}
		});
	}

	public static HapiTxnOp tokenOpsEnablement() {
		return fileUpdate(API_PERMISSIONS)
				.fee(9_999_999_999L)
				.payingWith(GENESIS)
				.overridingProps(Map.ofEntries(
						entry("tokenCreate", "0-*"),
						entry("tokenFreezeAccount", "0-*"),
						entry("tokenUnfreezeAccount", "0-*"),
						entry("tokenGrantKycToAccount", "0-*"),
						entry("tokenRevokeKycFromAccount", "0-*"),
						entry("tokenDelete", "0-*"),
						entry("tokenMint", "0-*"),
						entry("tokenBurn", "0-*"),
						entry("tokenAccountWipe", "0-*"),
						entry("tokenUpdate", "0-*"),
						entry("tokenGetInfo", "0-*"),
						entry("tokenAssociateToAccount", "0-*"),
						entry("tokenDissociateFromAccount", "0-*")
				));
	}

	public static HapiTxnOp scheduleOpsEnablement() {
		return fileUpdate(API_PERMISSIONS)
				.fee(9_999_999_999L)
				.payingWith(GENESIS)
				.overridingProps(Map.ofEntries(
						entry("scheduleCreate", "0-*"),
						entry("scheduleDelete", "0-*"),
						entry("scheduleSign", "0-*"),
						entry("scheduleGetInfo", "0-*")
				));
	}
}
