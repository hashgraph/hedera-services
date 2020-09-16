package com.hedera.services.context.domain.haccount;

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

import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.core.jproto.JThresholdKey;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTest;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

import static com.hedera.services.state.serdes.DomainSerdesTest.recordOne;
import static com.hedera.services.state.serdes.DomainSerdesTest.recordTwo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@RunWith(JUnitPlatform.class)
class LegacyAccountsTest {
	int N = 3;

	@Test
	public void readFcMap() throws Exception {
		// setup:
/*
		FCMap<MapKey, HederaAccount> subject = new FCMap<>(MapKey::deserialize, HederaAccount::deserialize);

		for (int s = 0; s < 3; s++) {
			subject.put(keyFrom(s), accountFrom(s));
		}

		var out = new FCDataOutputStream(...);
		subject.copyTo(out);
		subject.copyToExtra(out);
*/

		// given:
		FCMap<MerkleEntityId, MerkleAccount> subject = new FCMap<>(new MerkleEntityId.Provider(),
				MerkleAccount.LEGACY_PROVIDER);
		// and:
		var in = new SerializableDataInputStream(
				Files.newInputStream(Paths.get("src/test/resources/testAccounts.fcm")));

		// when:
		subject.copyFrom(in);
		subject.copyFromExtra(in);

		// then:
		assertEquals(subject.size(), N);
		for (int s = 0; s < N; s++) {
			var id = idFrom(s);
			assertTrue(subject.containsKey(id));
			var actual = subject.get(id);
			var expected = accountFrom(s);

			System.out.println("--- Expected ---");
			System.out.println(expected.toString());
			System.out.println("-> Records");
			for (ExpirableTxnRecord record : expected.records()) {
				System.out.println(record.toString());
			}
			System.out.println("--- Actual ---");
			System.out.println(actual.toString());
			System.out.println("-> Records");
			for (ExpirableTxnRecord record : actual.records()) {
				System.out.println(record.toString());
			}

			assertEquals(expected.state(), actual.state());
			assertEquals(expected.recordList(), actual.recordList());
		}
	}

	private MerkleEntityId idFrom(long s) {
		long t = s + 1;
		return new MerkleEntityId(t, 2 * t, 3 * t);
	}

	String[] memos = new String[] {
			"\"This was Mr. Bleaney's room. He stayed,",
			"Where like a pillow on a bed",
			"'Twas brillig, and the slithy toves",
	};
	JKey[] keys = new JKey[] {
			new JEd25519Key("abcdefghijklmnopqrstuvwxyz012345".getBytes()),
			new JKeyList(List.of(new JEd25519Key("ABCDEFGHIJKLMNOPQRSTUVWXYZ543210".getBytes()))),
			new JThresholdKey(
					new JKeyList(List.of(new JEd25519Key("ABCDEFGHIJKLMNOPQRSTUVWXYZ543210".getBytes()))),
					1)
	};
	List<List<ExpirableTxnRecord>> records = List.of(
			Collections.emptyList(),
			List.of(recordOne()),
			List.of(recordOne(), recordTwo()));

	public MerkleAccount accountFrom(int s) throws Exception {
		long v = s + 1;
		MerkleAccount account = new HederaAccountCustomizer()
				.proxy(new EntityId(v, 2 * v, 3 * v))
				.key(keys[s])
				.memo(memos[s])
				.isSmartContract(s % 2 != 0)
				.isDeleted(s == 2)
				.isReceiverSigRequired(s == 0)
				.fundsSentRecordThreshold(v * 1_234L)
				.fundsReceivedRecordThreshold(v * 5_432L)
				.expiry(1_234_567_890L + v)
				.autoRenewPeriod(666L * v)
				.customizing(new MerkleAccount());
		account.setBalance(888L * v);
		MerkleAccountTest.offerRecordsInOrder(account, records.get(s));
		return account;
	}
}
