package com.hedera.services.legacy.unit;

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

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.exceptions.NegativeAccountBalanceException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NodeAccountsCreation {
	private static final Logger log = LogManager.getLogger(NodeAccountsCreation.class);

	private static long INITIAL_GENESIS_COINS = 5000000000000000000l;

	public static void insertAccount(
			long balance,
			String publicKey,
			AccountID accountID,
			FCMap<MerkleEntityId, MerkleAccount> map
	) throws DecoderException, NegativeAccountBalanceException {
		LocalDate date = LocalDate.parse("2018-09-01");
		long expiryTime = Long.MAX_VALUE;

		Key accountKeys = Key.newBuilder()
				.setKeyList(KeyList.newBuilder()
						.addKeys(Key.newBuilder()
								.setEd25519(ByteString.copyFrom(MiscUtils.commonsHexToBytes(publicKey))).build())
						.build())
				.build();
		MerkleEntityId merkleEntityId = MerkleEntityId.fromAccountId(accountID);

		JKey jKey = JKey.mapKey(accountKeys);
		MerkleAccount hAccount = new HederaAccountCustomizer()
				.isReceiverSigRequired(false)
				.proxy(EntityId.MISSING_ENTITY_ID)
				.isDeleted(false)
				.expiry(expiryTime)
				.memo("")
				.isSmartContract(false)
				.key(jKey)
				.autoRenewPeriod(date.toEpochDay())
				.customizing(new MerkleAccount());
		hAccount.setBalance(balance);

		map.put(merkleEntityId, hAccount);
	}

	public static void writeToFile(String path, byte[] data) throws IOException {
		File f = new File(path);
		File parent = f.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}

		try (FileOutputStream fos = new FileOutputStream(f, false)) {
			fos.write(data);
			fos.flush();
		} catch (IOException e) {
			log.error("Error while writing to file {}", path, e);
			throw e;
		}
	}

	public static void writeToFileUTF8(String path, String data) throws IOException {
		byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
		writeToFile(path, bytes);
	}

}
