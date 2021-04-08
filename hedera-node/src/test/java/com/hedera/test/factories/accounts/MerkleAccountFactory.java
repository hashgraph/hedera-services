package com.hedera.test.factories.accounts;

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

import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.factories.keys.KeyTree;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.TokenID;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class MerkleAccountFactory {
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
	private Set<TokenID> associatedTokens = new HashSet<>();

	public MerkleAccount get() {
		MerkleAccount value = new MerkleAccount();
		memo.ifPresent(s -> value.setMemo(s));
		proxy.ifPresent(p -> value.setProxy(EntityId.fromGrpcAccountId(p)));
		balance.ifPresent(b -> { try { value.setBalance(b); } catch (Exception ignore) {} });
		deleted.ifPresent(b -> value.setDeleted(b));
		accountKeys.ifPresent(k -> value.setKey(k));
		expirationTime.ifPresent(l -> value.setExpiry(l));
		autoRenewPeriod.ifPresent(d -> value.setAutoRenewSecs(d));
		isSmartContract.ifPresent(b -> value.setSmartContract(b));
		receiverSigRequired.ifPresent(b -> value.setReceiverSigRequired(b));
		var tokens = new MerkleAccountTokens();
		tokens.associateAll(associatedTokens);
		value.setTokens(tokens);
		return value;
	}

	private MerkleAccountFactory() {}
	public static MerkleAccountFactory newAccount() {
		return new MerkleAccountFactory();
	}
	public static MerkleAccountFactory newContract() {
		return new MerkleAccountFactory().isSmartContract(true);
	}

	public MerkleAccountFactory proxy(AccountID id) {
		proxy = Optional.of(id);
		return this;
	}

	public MerkleAccountFactory balance(long amount) {
		balance = Optional.of(amount);
		return this;
	}

	public MerkleAccountFactory tokens(TokenID... tokens) {
		associatedTokens.addAll(List.of(tokens));
		return this;
	}

	public MerkleAccountFactory receiverThreshold(long v) {
		receiverThreshold = Optional.of(v);
		return this;
	}
	public MerkleAccountFactory senderThreshold(long v) {
		senderThreshold = Optional.of(v);
		return this;
	}
	public MerkleAccountFactory receiverSigRequired(boolean b) {
		receiverSigRequired = Optional.of(b);
		return this;
	}
	public MerkleAccountFactory keyFactory(KeyFactory keyFactory) {
		this.keyFactory = keyFactory;
		return this;
	}
	public MerkleAccountFactory accountKeys(KeyTree kt) throws Exception {
		return accountKeys(kt.asKey(keyFactory));
	}
	public MerkleAccountFactory accountKeys(Key k) throws Exception {
		return accountKeys(JKey.mapKey(k));
	}
	public MerkleAccountFactory accountKeys(JKey k) {
		accountKeys = Optional.of(k);
		return this;
	}
	public MerkleAccountFactory autoRenewPeriod(long p) {
		autoRenewPeriod = Optional.of(p);
		return this;
	}
	public MerkleAccountFactory deleted(boolean b) {
		deleted = Optional.of(b);
		return this;
	}
	public MerkleAccountFactory expirationTime(long l) {
		expirationTime = Optional.of(l);
		return this;
	}
	public MerkleAccountFactory memo(String s) {
		memo = Optional.of(s);
		return this;
	}
	public MerkleAccountFactory isSmartContract(boolean b) {
		isSmartContract = Optional.of(b);
		return this;
	}
}
