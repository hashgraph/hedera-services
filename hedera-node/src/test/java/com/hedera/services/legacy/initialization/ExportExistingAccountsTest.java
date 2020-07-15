package com.hedera.services.legacy.initialization;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
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
	public void reproducesLegacyFile() throws IOException {
		// setup:
		FCMap<MerkleEntityId, MerkleAccount> helper = new FCMap<>(new MerkleEntityId.Provider(),
				MerkleAccount.LEGACY_PROVIDER);
		// and:
		var in = new SerializableDataInputStream(Files.newInputStream(Paths.get(LEGACY_ACCOUNTS_LOC)));
		// and:
		helper.copyFrom(in);
		helper.copyFromExtra(in);

		// given:
		String expected = Files.readString(Paths.get(LEGACY_EXPORT_LOC));

		// when:
		ExportExistingAccounts.exportAccounts(TMP_EXPORT_LOC, helper);
		// and:
		String actual = Files.readString(Paths.get(TMP_EXPORT_LOC));

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