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

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.utils.MiscUtils;
import com.swirlds.fcmap.FCMap;
import org.junit.Before;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.builder.RequestBuilder;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleAccount;

import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;

public class CrptDelAcctValtionAndStartupBalCheckTest {

	long payerAccount;
	long nodeAccount;

	private AccountID nodeAccountId;
	private AccountID payerAccountId;
	private AccountID deleteAccountID;
	private AccountID transferAccountID;
	private AccountID account1ID;
	private AccountID account2ID;
	private AccountID account3ID;
	FCStorageWrapper storageWrapper;
	TransactionHandler transactionHandler = null;
	FCMap<MerkleEntityId, MerkleAccount> fcMap = null;
	private long LARGE_BALANCE = 1000000000000000l;
	AccountID feeAccount;
	List<AccountAmount> accountAmountsList;
	HederaFunctionality hederaFunc;
	Key keys;
	List<Key> keyListp ;

	@Before
	public void setUp() throws Exception {
		payerAccount = 10011;
		nodeAccount = 3l;
		payerAccountId = RequestBuilder.getAccountIdBuild(payerAccount, 0l, 0l);
		nodeAccountId = RequestBuilder.getAccountIdBuild(nodeAccount, 0l, 0l);
		fcMap = new FCMap<>(new MerkleEntityId.Provider(), MerkleAccount.LEGACY_PROVIDER);
		feeAccount = RequestBuilder.getAccountIdBuild(98l, 0l, 0l);
		accountAmountsList = new LinkedList<>();
		hederaFunc = HederaFunctionality.CryptoTransfer;

		KeyPair pair = new KeyPairGenerator().generateKeyPair();
		List<Key> keyList = new ArrayList<>();
		HashMap<String, PrivateKey> pubKey2privKeyMap = new HashMap<>();
		keyList.add(PrivateKeyToKey(pair.getPrivate()));
		addKeyMap(pair, pubKey2privKeyMap);
		SignatureList sigList = SignatureList.getDefaultInstance();
		byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
		Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
		 keyListp = Collections.singletonList(key);
		keys = Key.newBuilder().setKeyList(KeyList.newBuilder().addAllKeys(keyListp).build()).build();
	}

	private static Key PrivateKeyToKey(PrivateKey privateKey) {
		byte[] pubKey = ((EdDSAPrivateKey) privateKey).getAbyte();
		Key key = Key.newBuilder().setEd25519(ByteString.copyFrom(pubKey)).build();
		return key;
	}

	private static void addKeyMap(KeyPair pair, Map<String, PrivateKey> pubKey2privKeyMap) {
		byte[] pubKey = ((EdDSAPublicKey) pair.getPublic()).getAbyte();
		String pubKeyHex = MiscUtils.commonsBytesToHex(pubKey);
		pubKey2privKeyMap.put(pubKeyHex, pair.getPrivate());
	}	
}
