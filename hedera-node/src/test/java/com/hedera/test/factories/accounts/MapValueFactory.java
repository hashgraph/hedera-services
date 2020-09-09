package com.hedera.test.factories.accounts;

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

import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MapValueFactory {
	private KeyFactory keyFactory = KeyFactory.getDefaultInstance();
	private Optional<Long> balance = Optional.empty();
	private Optional<Long> receiverThreshold = Optional.empty();
	private Optional<Long> senderThreshold = Optional.empty();
	private Optional<Boolean> receiverSigRequired = Optional.empty();
	private Optional<JKey> accountKeys = Optional.empty();
	private Optional<Long> autoRenewPeriod = Optional.empty();
	private Optional<Boolean> deleted = Optional.empty();
	private Optional<Long> expirationTime = Optional.empty();
	private Optional<String> memo = Optional.empty();
	private Optional<Boolean> isSmartContract = Optional.empty();
	private Optional<AccountID> proxy = Optional.empty();
	private List<Map.Entry<TokenID, Long>> tokenBalances = new ArrayList<>();

	public MerkleAccount get() {
		MerkleAccount value = new MerkleAccount();
		memo.ifPresent(s -> value.setMemo(s));
		proxy.ifPresent(p -> value.setProxy(EntityId.ofNullableAccountId(p)));
		balance.ifPresent(b -> { try { value.setBalance(b); } catch (Exception ignore) {} });
		deleted.ifPresent(b -> value.setDeleted(b));
		accountKeys.ifPresent(k -> value.setKey(k));
		expirationTime.ifPresent(l -> value.setExpiry(l));
		autoRenewPeriod.ifPresent(d -> value.setAutoRenewSecs(d));
		senderThreshold.ifPresent(l -> value.setSenderThreshold(l));
		isSmartContract.ifPresent(b -> value.setSmartContract(b));
		receiverThreshold.ifPresent(l -> value.setReceiverThreshold(l));
		receiverSigRequired.ifPresent(b -> value.setReceiverSigRequired(b));
		tokenBalances.forEach(entry -> {
			var token = new MerkleToken();
			token.setKycKey(TxnHandlingScenario.TOKEN_KYC_KT.asJKeyUnchecked());
			value.grantKyc(entry.getKey(), token);
			value.adjustTokenBalance(entry.getKey(), token, entry.getValue());
		});
		return value;
	}

	private MapValueFactory() {}
	public static MapValueFactory newAccount() {
		return new MapValueFactory();
	}
	public static MapValueFactory newContract() {
		return new MapValueFactory().isSmartContract(true);
	}

	public MapValueFactory proxy(AccountID id) {
		proxy = Optional.of(id);
		return this;
	}

	public MapValueFactory balance(long amount) {
		balance = Optional.of(amount);
		return this;
	}
	public MapValueFactory tokenBalance(TokenID token, long amount) {
		tokenBalances.add(new AbstractMap.SimpleImmutableEntry<>(token, amount));
		return this;
	}
	public MapValueFactory receiverThreshold(long v) {
		receiverThreshold = Optional.of(v);
		return this;
	}
	public MapValueFactory senderThreshold(long v) {
		senderThreshold = Optional.of(v);
		return this;
	}
	public MapValueFactory receiverSigRequired(boolean b) {
		receiverSigRequired = Optional.of(b);
		return this;
	}
	public MapValueFactory keyFactory(KeyFactory keyFactory) {
		this.keyFactory = keyFactory;
		return this;
	}
	public MapValueFactory accountKeys(KeyTree kt) throws Exception {
		return accountKeys(kt.asKey(keyFactory));
	}
	public MapValueFactory accountKeys(Key k) throws Exception {
		return accountKeys(JKey.mapKey(k));
	}
	public MapValueFactory accountKeys(JKey k) {
		accountKeys = Optional.of(k);
		return this;
	}
	public MapValueFactory autoRenewPeriod(long p) {
		autoRenewPeriod = Optional.of(p);
		return this;
	}
	public MapValueFactory deleted(boolean b) {
		deleted = Optional.of(b);
		return this;
	}
	public MapValueFactory expirationTime(long l) {
		expirationTime = Optional.of(l);
		return this;
	}
	public MapValueFactory memo(String s) {
		memo = Optional.of(s);
		return this;
	}
	public MapValueFactory isSmartContract(boolean b) {
		isSmartContract = Optional.of(b);
		return this;
	}
}
