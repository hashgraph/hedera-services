package com.hedera.test.forensics.domain;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.legacy.core.MapKey;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.fcmap.FCMap;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toList;

public class PojoLedger {
	private List<PojoAccount> accounts;

	public static PojoLedger fromDisk(String dumpLoc) throws Exception {
		try (FCDataInputStream fin = new FCDataInputStream(Files.newInputStream(Path.of(dumpLoc)))) {
			FCMap<MapKey, HederaAccount> fcm = new FCMap<>(MapKey::deserialize, HederaAccount::deserialize);
			fcm.copyFrom(fin);
			fcm.copyFromExtra(fin);
			return from(fcm);
		}
	}

	public static PojoLedger from(FCMap<MapKey, HederaAccount> ledger) {
		var pojo = new PojoLedger();
		var readable = ledger.entrySet()
				.stream()
				.map(PojoAccount::fromEntry)
				.sorted(comparingInt(PojoLedger::accountNum))
				.collect(toList());
		pojo.setAccounts(readable);
		return pojo;
	}

	public void asJsonTo(String readableLoc) throws IOException {
		var om = new ObjectMapper();
		om.writerWithDefaultPrettyPrinter().writeValue(new File(readableLoc), this);
	}

	private static int accountNum(PojoAccount a) {
		return Integer.parseInt(a.getId().split("[.]")[2]);
	}

	public List<PojoAccount> getAccounts() {
		return accounts;
	}

	public void setAccounts(List<PojoAccount> accounts) {
		this.accounts = accounts;
	}
}
