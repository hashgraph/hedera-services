package com.hedera.services.state.exports;


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

import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToStringAccountsExporterTest {
	private String testExportLoc = "accounts.txt";
	private MerkleAccount account1 = new HederaAccountCustomizer()
			.isReceiverSigRequired(true)
			.proxy(EntityId.MISSING_ENTITY_ID)
			.isDeleted(false)
			.expiry(1_234_567L)
			.memo("This ecstasy doth unperplex")
			.isSmartContract(true)
			.key(new JEd25519Key("first-fake".getBytes()))
			.autoRenewPeriod(555_555L)
			.customizing(new MerkleAccount());
	private MerkleAccount account2 = new HederaAccountCustomizer()
			.isReceiverSigRequired(false)
			.proxy(EntityId.MISSING_ENTITY_ID)
			.isDeleted(true)
			.expiry(7_654_321L)
			.memo("We said, and show us what we love")
			.isSmartContract(false)
			.key(new JEd25519Key("second-fake".getBytes()))
			.autoRenewPeriod(444_444L)
			.customizing(new MerkleAccount());
	private ToStringAccountsExporter subject = new ToStringAccountsExporter();

	@Test
	void producesExpectedText() throws Exception {
		// setup:
		account1.setBalance(1L);
		account1.setTokens(new MerkleAccountTokens(new long[] { 1L, 2L, 3L, 3L, 2L, 1L }));
		account2.setBalance(2L);
		account2.setTokens(new MerkleAccountTokens(new long[] { 0L, 0L, 1234L }));
		// and:
		var desired = "0.0.1\n" +
				"---\n" +
				"MerkleAccount{state=MerkleAccountState{key=ed25519: \"first-fake\"\n" +
				", expiry=1234567, balance=1, autoRenewSecs=555555, memo=This ecstasy doth unperplex, deleted=false, " +
				"smartContract=true, receiverSigRequired=true, proxy=EntityId{shard=0, realm=0, num=0}}, # records=0, " +
				"tokens=[3.2.1, 1.2.3]}\n" +
				"\n" +
				"0.0.2\n" +
				"---\n" +
				"MerkleAccount{state=MerkleAccountState{key=ed25519: \"second-fake\"\n" +
				", expiry=7654321, balance=2, autoRenewSecs=444444, memo=We said, and show us what we love, " +
				"deleted=true, smartContract=false, receiverSigRequired=false, proxy=EntityId{shard=0, realm=0, " +
				"num=0}}, # records=0, tokens=[1234.0.0]}\n";

		// given:
		FCMap<MerkleEntityId, MerkleAccount> accounts = new FCMap<>();
		// and:
		accounts.put(new MerkleEntityId(0, 0, 2), account2);
		accounts.put(new MerkleEntityId(0, 0, 1), account1);

		// when:
		subject.toFile(testExportLoc, accounts);
		// and:
		var result = Files.readString(Paths.get(testExportLoc));

		// then:
		assertEquals(desired, result);
	}

	@AfterEach
	void cleanup() {
		var f = new File(testExportLoc);
		if (f.exists()) {
			f.delete();
		}
	}
}