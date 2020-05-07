package com.hedera.services.legacy.unit.initialization;

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
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.initialization.NodeAccountsCreation;
import com.hedera.services.legacy.logic.ApplicationConstants;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.DecoderException;
import org.junit.Assert;
import org.junit.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.security.spec.InvalidKeySpecException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;

public class Ver2ToVer3AcctMigrationTest {

	private FCMap<MapKey, HederaAccount> getAccountMapForTest() throws Exception {
		FCMap<MapKey, HederaAccount> fcMap = new FCMap<>(MapKey::deserialize, HederaAccount::deserialize);
		for (int i = 1; i <= 100; i++) {
			createAccount(fcMap, i);
		}
		createAccount(fcMap, 1001);
		createAccount(fcMap, 1070);
		return fcMap;
	}

	/**
	 * Load Default Key
	 * @throws URISyntaxException 
	 */
	private Key getDefaultKey() throws DecoderException, InvalidKeySpecException, URISyntaxException {
		URL url = Ver2ToVer3AcctMigration.class.getResource("/genesisKey/StartUpAccount.txt");
		Map<String, List<AccountKeyListObj>> accountMap = NodeAccountsCreation.getAccountMapFromPath(Paths.get(url.toURI()).toString());

		List<AccountKeyListObj> startAccountList = accountMap.get(ApplicationConstants.START_ACCOUNT);

		// get the Public Key
		String publicKeyStartAcct =
				startAccountList.get(0).getKeyPairList().get(0).getPublicKeyAbyteStr();

		return Key.newBuilder()
				.setKeyList(KeyList.newBuilder()
						.addKeys(Key.newBuilder()
								.setEd25519(ByteString.copyFrom(MiscUtils.commonsHexToBytes(publicKeyStartAcct))).build())
						.build())
				.build();
	}

	private void createAccount(FCMap<MapKey, HederaAccount> fcMap, long accountNum) throws Exception {
		MapKey mk = new MapKey();
		mk.setShardNum(0);
		mk.setRealmNum(0);
		mk.setAccountNum(accountNum);

		HederaAccount mv = new HederaAccount();
		mv.setBalance(accountNum * 10);
		mv.setReceiverThreshold(accountNum * 20);
		mv.setSenderThreshold(accountNum * 30);
		mv.setReceiverSigRequired(accountNum % 2 == 0);
		byte[] bytes = ("test" + accountNum).getBytes();
		Key defaultKey = Key.newBuilder().setEd25519(ByteString.copyFrom(bytes)).build();
		mv.setAccountKeys(JKey.mapKey(defaultKey));
		fcMap.put(mk, mv);
	}

	/**
	 * (1) verify the size of fcMap should increase by 101; because 900-1000 are created during migration
	 * (2) verify the expiration value after migration
	 * @throws Exception
	 */
	@Test
	public void migrationTest() throws Exception {
		FCMap<MapKey, HederaAccount> fcMap = getAccountMapForTest();
		
		Key key = getDefaultKey();

		long sizeBeforeMigration = fcMap.size();
		Ver2ToVer3AcctMigration.migrate(fcMap, 1l, key);
		long sizeAfterMigration = fcMap.size();

		//size should increase by 101; because 900-1000 are created during migration
		Assert.assertEquals(sizeBeforeMigration + 101, sizeAfterMigration);

		// Verify expirationTime
		for (MapKey mapKey : fcMap.keySet()) {
			long accountNum = mapKey.getAccountNum();
			long expirationTime = fcMap.get(mapKey).getExpirationTime();
			if (accountNum <= 1000) {
				Assert.assertEquals(Long.MAX_VALUE, expirationTime);
			} else {
				Assert.assertEquals(Ver2ToVer3AcctMigration.getExpirationTimeForMigratedAccount(accountNum), expirationTime);
			}
		}
	}

	@Test
	public void getExpirationTimeForMigratedAccountTest() {
		Assert.assertEquals(Long.MAX_VALUE,
				Ver2ToVer3AcctMigration.getExpirationTimeForMigratedAccount(2));

		Assert.assertEquals(Long.MAX_VALUE,
				Ver2ToVer3AcctMigration.getExpirationTimeForMigratedAccount(1000));

		Assert.assertEquals(Instant.parse("2019-11-01T00:01:00Z").getEpochSecond(),
				Ver2ToVer3AcctMigration.getExpirationTimeForMigratedAccount(1001));

		Assert.assertEquals(Instant.parse("2019-11-01T01:10:00Z").getEpochSecond(),
				Ver2ToVer3AcctMigration.getExpirationTimeForMigratedAccount(1070));
	}

	@Test
	public void getMaxAccountNumTest() throws Exception {
		FCMap<MapKey, HederaAccount> fcMap = getAccountMapForTest();
		Assert.assertEquals(1070, Ver2ToVer3AcctMigration.getMaxAccountNum(fcMap));
	}
}
