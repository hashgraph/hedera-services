package com.hedera.services.legacy.initialization;

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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.utils.EntityIdUtils;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@RunWith(JUnitPlatform.class)
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
						new FCMap<>(new MerkleEntityId.Provider(), MerkleAccount.LEGACY_PROVIDER)));
	}

	@Test
	public void handlesNullProxyAccount() throws IOException {
		// setup:
		FCMap<MerkleEntityId, MerkleAccount> savedAccounts = new FCMap<>(
				new MerkleEntityId.Provider(),
				MerkleAccount.LEGACY_PROVIDER);
		// and:
		var in = new SerializableDataInputStream(Files.newInputStream(Paths.get(LEGACY_ACCOUNTS_LOC)));
		// and:
		savedAccounts.copyFrom(in);
		savedAccounts.copyFromExtra(in);

		// given:
		savedAccounts.get(MerkleEntityId.fromAccountId(EntityIdUtils.accountParsedFromString("1.2.3")))
				.setProxy(null);

		// expect:
		Assertions.assertDoesNotThrow(() -> ExportExistingAccounts.asJsonArray(savedAccounts));
	}

	@Test
	public void reproducesLegacyFile() throws IOException {
		// setup:
		FCMap<MerkleEntityId, MerkleAccount> savedAccounts = new FCMap<>(
				new MerkleEntityId.Provider(),
				MerkleAccount.LEGACY_PROVIDER);
		// and:
		var in = new SerializableDataInputStream(Files.newInputStream(Paths.get(LEGACY_ACCOUNTS_LOC)));
		// and:
		savedAccounts.copyFrom(in);
		savedAccounts.copyFromExtra(in);

		// given:
		String expected = Files.readString(Paths.get(LEGACY_EXPORT_LOC)).strip();

		// when:
		ExportExistingAccounts.exportAccounts(TMP_EXPORT_LOC, savedAccounts);
		// and:
		String actual = Files.readString(Paths.get(TMP_EXPORT_LOC)).strip();

		// then:
		Assertions.assertEquals(expected, actual);
	}

	@AfterAll
	public static void cleanup() {
		var f = new File(TMP_EXPORT_LOC);

		if (f.exists()) {
			f.delete();
		}
	}
}
