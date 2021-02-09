package com.hedera.services.legacy.initialization;

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

import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;

class ExportExistingAccountsTest {
	private static final String LEGACY_ACCOUNTS_LOC = "src/test/resources/testAccounts.fcm";
	private static final String LEGACY_EXPORT_LOC = "src/test/resources/legacyAccounts.json";

	private static final String TMP_EXPORT_LOC = "src/test/resources/currentAccounts.json";

	@Test
	public void throwsOnInvalidLoc() {
		// expect:
		Assertions.assertThrows(IOException.class, () ->
				ExportExistingAccounts.exportAccounts(
						"not/a/location",
						new FCMap<>()));
	}

	@AfterAll
	public static void cleanup() {
		var f = new File(TMP_EXPORT_LOC);

		if (f.exists()) {
			f.delete();
		}
	}
}
