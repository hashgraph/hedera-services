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

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.legacy.config.PropertiesLoader;
import com.hedera.services.legacy.logic.ApplicationConstants;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.exception.NegativeAccountBalanceException;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.spec.InvalidKeySpecException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NodeAccountsCreation {
	private static final Logger log = LogManager.getLogger(NodeAccountsCreation.class);

	static String GEN_ACCOUNT_PATH = PropertiesLoader.getGenAccountPath();
	static String GEN_PRIV_KEY_PATH = PropertiesLoader.getGenPrivKeyPath();
	static String GEN_PUB_KEY_PATH = PropertiesLoader.getGenPubKeyPath();
	static String GEN_PUB_KEY_32BYTES_PATH = PropertiesLoader.getGenPub32KeyPath();

	private static long INITIAL_GENESIS_COINS = PropertiesLoader.getInitialGenesisCoins();
	private static long INITIAL_COINS = PropertiesLoader.getInitialCoins();

	public void initializeNodeAccounts(
			AddressBook addressBook,
			FCMap<MerkleEntityId, MerkleAccount> map
	) throws DecoderException, InvalidKeySpecException, IOException, NegativeAccountBalanceException {
		log.info("Initialization of Startup Account and Node Accounts started");
		Map<String, List<AccountKeyListObj>> accountMap = readBase64EncodedGenesisKey(GEN_ACCOUNT_PATH);

		List<AccountKeyListObj> startAccountList = accountMap.get(ApplicationConstants.START_ACCOUNT);

		String publicKeyStartAcct = startAccountList.get(0).getKeyPairList().get(0).getPublicKeyAbyteStr();
		String publicKeyStr = startAccountList.get(0).getKeyPairList().get(0).getPublicKeyStr();
		String privateKeyStr = startAccountList.get(0).getKeyPairList().get(0).getPrivateKeyStr();
		writeToFileUTF8(GEN_PUB_KEY_32BYTES_PATH, publicKeyStartAcct);
		writeToFileUTF8(GEN_PUB_KEY_PATH, publicKeyStr);
		writeToFileUTF8(GEN_PRIV_KEY_PATH, privateKeyStr);

		MerkleEntityId merkleEntityId = new MerkleEntityId(
				ApplicationConstants.DEFAULT_FILE_SHARD,
				ApplicationConstants.DEFAULT_FILE_REALM,
				1);
		if (!map.containsKey(merkleEntityId)) {
			AccountID accountIDOne = RequestBuilder.getAccountIdBuild(1L,
					ApplicationConstants.DEFAULT_FILE_REALM, ApplicationConstants.DEFAULT_SHARD);
			insertAccount(INITIAL_COINS, publicKeyStartAcct, accountIDOne, map);

			// Genesis Account with Account ID 2 will have any balance
			AccountID accountIDTwo = RequestBuilder.getAccountIdBuild(2L,
					ApplicationConstants.DEFAULT_FILE_REALM, ApplicationConstants.DEFAULT_SHARD);
			insertAccount(INITIAL_GENESIS_COINS, publicKeyStartAcct, accountIDTwo, map);
			log.info("Startup Account Created in Hedera");
		}
		int nodeCount = addressBook.getSize();
		log.info("Node count size :: " + nodeCount);
		Address address;

		List<Address> addressList = new ArrayList<>();
		for (int i = 0; i < nodeCount; i++) {
			address = addressBook.getAddress(i);
			addressList.add(address);
		}
		for (Address address1 : addressList) {
			address = address1;
			var id = EntityIdUtils.accountParsedFromString(address.getMemo());
			merkleEntityId = MerkleEntityId.fromAccountId(id);
			if (!map.containsKey(merkleEntityId)) {
				log.info(
						"The node Public Key for Account Num ===> "
								+ id.getAccountNum()
								+ " Public key ====>>> "
								+ publicKeyStartAcct);
				insertAccount(INITIAL_COINS, publicKeyStartAcct, id, map);
			}
		}

		log.info("System is now going to check if initial 100 accounts exist or not");
		log.info( "If it does not exist , it will go ahead and create with default keys ..it may take some time..");
		int startCount = 2 + addressList.size() + 1;
		for (long i = startCount; i <= 100; i++) {
			AccountID sysAccountID = RequestBuilder.getAccountIdBuild(i,
					ApplicationConstants.DEFAULT_FILE_REALM, ApplicationConstants.DEFAULT_SHARD);
			merkleEntityId = new MerkleEntityId(sysAccountID.getShardNum(), sysAccountID.getRealmNum(),
					sysAccountID.getAccountNum());
			if (!map.containsKey(merkleEntityId)) {
				insertAccount(INITIAL_COINS, publicKeyStartAcct, sysAccountID, map);
			}

		}

		long totalLedgerBalance = 0L;
		for (MerkleEntityId currKey : map.keySet()) {
			MerkleAccount currMv = map.get(currKey);
			totalLedgerBalance += currMv.getBalance();
		}

		log.info("Total balance for ledger " + totalLedgerBalance);
		log.info("Account initialization process completed");
	}

	public static void insertAccount(
			long balance,
			String publicKey,
			AccountID accountID,
			FCMap<MerkleEntityId, MerkleAccount> map
	) throws DecoderException, NegativeAccountBalanceException {
		LocalDate date = LocalDate.parse("2018-09-01");
		long expiryTime = PropertiesLoader.getExpiryTime();

		Key accountKeys = Key.newBuilder()
				.setKeyList(KeyList.newBuilder()
						.addKeys(Key.newBuilder()
								.setEd25519(ByteString.copyFrom(MiscUtils.commonsHexToBytes(publicKey))).build())
						.build())
				.build();
		MerkleEntityId merkleEntityId = MerkleEntityId.fromAccountId(accountID);

		JKey jKey = JKey.mapKey(accountKeys);
		MerkleAccount hAccount = new HederaAccountCustomizer()
				.fundsSentRecordThreshold(INITIAL_GENESIS_COINS)
				.fundsReceivedRecordThreshold(INITIAL_GENESIS_COINS)
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

	@SuppressWarnings("unchecked")
	public static Map<String, List<AccountKeyListObj>> readBase64EncodedGenesisKey(String loc) {
		Map<String, List<AccountKeyListObj>> keysListMap = null;
		try {
			var keyBase64Pub = Files.readString(Paths.get(loc));
			byte[] accountKeyPairHolderBytes = Base64.getDecoder().decode(keyBase64Pub);
			keysListMap = (Map<String, List<AccountKeyListObj>>) convertFromBytes(accountKeyPairHolderBytes);
		} catch (IOException | ClassNotFoundException e) {
			log.error("Unable to deserialize startup keystore!", e);
		}
		return keysListMap;
	}

	private static Object convertFromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
			 ObjectInput in = new ObjectInputStream(bis)) {
			return in.readObject();
		}
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
