package com.hedera.test.forensics;

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
import com.hedera.services.context.PrimitiveContext;
import com.hedera.test.forensics.domain.PojoFs;
import com.hedera.test.forensics.domain.PojoLedger;
import com.swirlds.common.AddressBook;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.platform.Utilities;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static com.hedera.test.forensics.FcByteViews.offsetsOf;

@Disabled
@RunWith(JUnitPlatform.class)
public class FcmToJsonUtil {
	final List<String> accountsLocs = List.of(
			"/Users/tinkerm/Dev/hgn2/services-hedera/hedera-node/forensics/iss-demo/0/accounts-round12.fcm",
			"/Users/tinkerm/Dev/hgn2/services-hedera/hedera-node/forensics/iss-demo/1/accounts-round12.fcm",
			"/Users/tinkerm/Dev/hgn2/services-hedera/hedera-node/forensics/iss-demo/2/accounts-round12.fcm"
	);
	final List<String> storageLocs = List.of(
			"/Users/tinkerm/Dev/hgn2/services-hedera/hedera-node/forensics/iss-demo/0/storage-round12.fcm",
			"/Users/tinkerm/Dev/hgn2/services-hedera/hedera-node/forensics/iss-demo/1/storage-round12.fcm",
			"/Users/tinkerm/Dev/hgn2/services-hedera/hedera-node/forensics/iss-demo/2/storage-round12.fcm"
	);
	final List<String> topicsLocs = List.of(
			"/Users/tinkerm/Dev/hgn2/services-hedera/hedera-node/forensics/iss-demo/0/topics-round12.fcm",
			"/Users/tinkerm/Dev/hgn2/services-hedera/hedera-node/forensics/iss-demo/1/topics-round12.fcm",
			"/Users/tinkerm/Dev/hgn2/services-hedera/hedera-node/forensics/iss-demo/2/topics-round12.fcm"
	);

	@Test
	public void convertStorageToJson() throws Exception {
		for (String dumpLoc : storageLocs) {
			PojoFs.fromDisk(dumpLoc).asJsonTo(jsonSuffixed(dumpLoc));
		}
	}

	@Test
	public void convertAccountsToJson() throws Exception {
		for (String dumpLoc : accountsLocs) {
			PojoLedger.fromDisk(dumpLoc).asJsonTo(jsonSuffixed(dumpLoc));
		}
	}

	@Test
	public void readSwh() throws Exception {
		var LOCATION = "?";

		var om = new ObjectMapper();
		var primitives = new PrimitiveContext(new AddressBook());

		var signedState = Files.readAllBytes(Path.of(LOCATION));
		var fcTreeStarts = offsetsOf(TREE_BEGIN, signedState);
		System.out.println("FCMap(s) start @ " + fcTreeStarts);
		var fcTreeEnds = offsetsOf(TREE_END, signedState);
		System.out.println("FCMap(s)  end  @ " + fcTreeEnds);

		try (InputStream in = Files.newInputStream(Path.of(LOCATION))) {
			try (FCDataInputStream fin = new FCDataInputStream(in)) {
				var version = fin.readLong();
				System.out.println("Swirlds Version @ " + version);
				var bytes = Utilities.readByteArray(fin);
				System.out.println(" - Read " + bytes.length + " bytes (hash?)");
				primitives.copyFrom(fin);
			}
		}

		var accountsIn = new ByteArrayInputStream(signedState, fcTreeStarts.get(3), fcTreeEnds.get(3) + 4);
		try (FCDataInputStream fin = new FCDataInputStream(accountsIn)) {
			primitives.getAccounts().copyFromExtra(fin);
		}

		var ledger = PojoLedger.from(primitives.getAccounts());
		om.writerWithDefaultPrettyPrinter().writeValue(new File(jsonSuffixed(LOCATION)), ledger);
	}

	private String jsonSuffixed(String path) {
		int n = path.length();
		return path.substring(0, n - 4) + ".json";
	}

	private final static int TREE_BEGIN = 1835364971;
	private final static int TREE_END = 1818588274;
}
