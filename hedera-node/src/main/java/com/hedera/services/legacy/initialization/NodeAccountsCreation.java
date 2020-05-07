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
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.legacy.core.AccountKeyListObj;
import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.exception.NegativeAccountBalanceException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
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

import static com.hedera.services.legacy.core.jproto.JAccountID.convert;

public class NodeAccountsCreation {
	private static final Logger log = LogManager.getLogger(NodeAccountsCreation.class);

	private static String GEN_ACCOUNT_PATH = PropertiesLoader.getGenAccountPath();
	private static String GEN_PUB_KEY_PATH = PropertiesLoader.getGenPubKeyPath();
	private static String GEN_PRIV_KEY_PATH = PropertiesLoader.getGenPrivKeyPath();
	private static String GEN_PUB_KEY_32BYTES_PATH = PropertiesLoader.getGenPub32KeyPath();
	private static long INITIAL_GENESIS_COINS = PropertiesLoader.getInitialGenesisCoins();
	private static long INITIAL_COINS = PropertiesLoader.getInitialCoins();

	public static void createAccounts(
			long balance,
			String publicKey,
			AccountID accountID,
			FCMap<MapKey, HederaAccount> map
	) throws DecoderException, NegativeAccountBalanceException {
		LocalDate date = LocalDate.parse("2018-09-01");
		long expiryTime = PropertiesLoader.getExpiryTime();

		Key accountKeys = Key.newBuilder()
				.setKeyList(KeyList.newBuilder()
						.addKeys(Key.newBuilder()
								.setEd25519(ByteString.copyFrom(MiscUtils.commonsHexToBytes(publicKey))).build())
						.build())
				.build();
		MapKey mapKey = MapKey.getMapKey(accountID);

		JKey jKey = JKey.mapKey(accountKeys);
		JAccountID proxyId = convert(AccountID.getDefaultInstance());
		HederaAccount hAccount = new HederaAccountCustomizer()
				.fundsSentRecordThreshold(INITIAL_GENESIS_COINS)
				.fundsReceivedRecordThreshold(INITIAL_GENESIS_COINS)
				.isReceiverSigRequired(false)
				.proxy(proxyId)
				.isDeleted(false)
				.expiry(expiryTime)
				.memo("")
				.isSmartContract(false)
				.key(jKey)
				.autoRenewPeriod(date.toEpochDay())
				.customizing(new HederaAccount());
		hAccount.setBalance(balance);

		map.put(mapKey, hAccount);
	}

	/**
	 * This method returns the Start up account object Map
	 */
	public static Map<String, List<AccountKeyListObj>> getAccountMapFromPath(String path) {
		String keyBase64Pub = readFileContentUTF8(path);
		byte[] accountKeyPairHolderBytes = Base64.getDecoder().decode(keyBase64Pub);
		@SuppressWarnings("unchecked")
		Map<String, List<AccountKeyListObj>> accountKeyPairHolder = null;
		try {
			accountKeyPairHolder =
					(Map<String, List<AccountKeyListObj>>) convertFromBytes(accountKeyPairHolderBytes);
		} catch (IOException | ClassNotFoundException e) {
			log.error("Unable to deserialize startup keystore!", e);
		}
		return accountKeyPairHolder;
	}

	public static byte[] convertToBytes(Object object) throws IOException {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
			ObjectOutput out = new ObjectOutputStream(bos)) {
			out.writeObject(object);
			return bos.toByteArray();
		}
	}

	private static Object convertFromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
			 ObjectInput in = new ObjectInputStream(bis)) {
			return in.readObject();
		}
	}

	private static String readFileContentUTF8(String filePath) {
		String fileString = null;
		try {
			fileString = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
		} catch (IOException e) {
			log.error("Error while reading from file {}. ", filePath, e);
		}

		return fileString;
	}

	/**
	 * Write bytes to a file.
	 *
	 * @param path
	 * 		the file path to write bytes
	 * @param data
	 * 		the byte array data
	 */
	public static void writeToFile(String path, byte[] data) throws IOException {
		writeToFile(path, data, false);
	}

	/**
	 * Write bytes to a file.
	 *
	 * @param append
	 * 		append to existing file if true
	 */
	public static void writeToFile(String path, byte[] data, boolean append) throws IOException {
		File f = new File(path);
		File parent = f.getParentFile();
		if (parent != null && !parent.exists()) {
			parent.mkdirs();
		}

		try (FileOutputStream fos = new FileOutputStream(f, append)) {
			fos.write(data);
			fos.flush();
		} catch (IOException e) {
			log.error("Error while writing to file {}", path, e);
			throw e;
		}
	}

	/**
	 * Write string to a file using UTF_8 encoding.
	 *
	 * @param path
	 * 		the file path to write bytes
	 * @param data
	 * 		the byte array data
	 */
	public static void writeToFileUTF8(String path, String data) throws IOException {
		byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
		writeToFile(path, bytes);
	}

	/**
	 * This method initializes Node accounts from exists in AddressBook.
	 */
	public void initializeNodeAccounts(AddressBook addressBook, FCMap<MapKey, HederaAccount> map)
			throws DecoderException, InvalidKeySpecException, IOException, NegativeAccountBalanceException {
		log.info("Initialization of Startup Account and Node Accounts started");
		Map<String, List<AccountKeyListObj>> accountMap = getAccountMapFromPath(GEN_ACCOUNT_PATH);

		List<AccountKeyListObj> startAccountList = accountMap.get(ApplicationConstants.START_ACCOUNT);

		// get the Public Key
		String publicKeyStartAcct = startAccountList.get(0).getKeyPairList().get(0).getPublicKeyAbyteStr();
		String publicKeyStr = startAccountList.get(0).getKeyPairList().get(0).getPublicKeyStr();
		String privateKeyStr = startAccountList.get(0).getKeyPairList().get(0).getPrivateKeyStr();
		writeToFileUTF8(GEN_PUB_KEY_32BYTES_PATH, publicKeyStartAcct);
		writeToFileUTF8(GEN_PUB_KEY_PATH, publicKeyStr);
		writeToFileUTF8(GEN_PRIV_KEY_PATH, privateKeyStr);
		// Check if Map is initialised withAccount 1 and 2
		MapKey mapKey = new MapKey(ApplicationConstants.DEFAULT_FILE_SHARD,
				ApplicationConstants.DEFAULT_FILE_REALM, 1);
		if (!map.containsKey(mapKey)) {
			// Genesis Account with Account ID 1 will not have any balance
			AccountID accountIDOne = RequestBuilder.getAccountIdBuild(1L,
					ApplicationConstants.DEFAULT_FILE_REALM, ApplicationConstants.DEFAULT_SHARD);
			createAccounts(INITIAL_COINS, publicKeyStartAcct, accountIDOne, map);

			// Genesis Account with Account ID 2 will have any balance
			AccountID accountIDTwo = RequestBuilder.getAccountIdBuild(2L,
					ApplicationConstants.DEFAULT_FILE_REALM, ApplicationConstants.DEFAULT_SHARD);
			createAccounts(INITIAL_GENESIS_COINS, publicKeyStartAcct, accountIDTwo, map);
			log.info("Startup Account Created in Hedera");
		}
		// Get the total nodes for initialization
		int nodeCount = addressBook.getSize();
		log.info("Node count size :: " + nodeCount);
		// now iterate through the list and get the Address
		Address address;

		// Store the list of addresses in ArrayList
		List<Address> addressList = new ArrayList<>();
		for (int i = 0; i < nodeCount; i++) {
			address = addressBook.getAddress(i);
			addressList.add(address);
		}
		long nodeAccountNum;
		long nodeRealmNum;
		long nodeShardNum;
		String[] accountIdArr;
		AccountID accountID;
		for (Address address1 : addressList) {
			address = address1;
			accountIdArr = address.getMemo().split("\\.");
			nodeShardNum = Long.parseLong(accountIdArr[0]);
			nodeRealmNum = Long.parseLong(accountIdArr[1]);
			nodeAccountNum = Long.parseLong(accountIdArr[2]);
			mapKey = new MapKey(nodeShardNum, nodeRealmNum, nodeAccountNum);
			if (!map.containsKey(mapKey)) {
				accountID = RequestBuilder.getAccountIdBuild(nodeAccountNum, nodeRealmNum, nodeShardNum);
				log.info("The node Public Key for Account Num ===> " + nodeAccountNum
						+ " Public key ====>>> " + publicKeyStartAcct);
				createAccounts(INITIAL_COINS, publicKeyStartAcct, accountID, map);
			}
		}

		// Now create Accounts till AccountID 100
		log.info("System is now going to check if initial 100 accounts exist or not");
		log.info(
				"If it does not exist , it will go ahead and create with default keys ..it may take some time..");
		int startCount = 2 + addressList.size() + 1;
		for (long i = startCount; i <= 100; i++) {
			AccountID sysAccountID = RequestBuilder.getAccountIdBuild(i,
					ApplicationConstants.DEFAULT_FILE_REALM, ApplicationConstants.DEFAULT_SHARD);
			mapKey = new MapKey(sysAccountID.getShardNum(), sysAccountID.getRealmNum(),
					sysAccountID.getAccountNum());
			if (!map.containsKey(mapKey)) {
				createAccounts(INITIAL_COINS, publicKeyStartAcct, sysAccountID, map);
			}

		}

		long totalLedgerBalance = 0L;
		for (MapKey currKey : map.keySet()) {
			HederaAccount currMv = map.get(currKey);
			totalLedgerBalance += currMv.getBalance();
		}
		log.info("Total balance for ledger " + totalLedgerBalance);

		log.info("Account initialization process completed");
	}

}
