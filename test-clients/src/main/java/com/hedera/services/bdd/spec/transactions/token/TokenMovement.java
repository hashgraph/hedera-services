package com.hedera.services.bdd.spec.transactions.token;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TokenMovement {
	private final long amount;
	private final String token;
	private final Optional<String> sender;
	private final Optional<String> receiver;
	private final Optional<List<String>> receivers;

	public static final TokenID HBAR_SENTINEL_TOKEN_ID = TokenID.getDefaultInstance();

	TokenMovement(
			String token,
			Optional<String> sender,
			long amount,
			Optional<String> receiver,
			Optional<List<String>> receivers
	) {
		this.token = token;
		this.sender = sender;
		this.amount = amount;
		this.receiver = receiver;
		this.receivers = receivers;
	}

	public boolean isTrulyToken() {
		return token != HapiApiSuite.HBAR_TOKEN_SENTINEL;
	}

	public List<Map.Entry<String, Long>> generallyInvolved() {
		if (sender.isPresent()) {
			Map.Entry<String, Long> senderEntry = new AbstractMap.SimpleEntry<>(sender.get(), -amount);
			return receiver.isPresent()
					? List.of(senderEntry, new AbstractMap.SimpleEntry<>(receiver.get(), -amount))
					: (receivers.isPresent() ? involvedInDistribution(senderEntry) : List.of(senderEntry));
		}
		return Collections.emptyList();
	}

	private List<Map.Entry<String, Long>> involvedInDistribution(Map.Entry<String, Long> senderEntry) {
		List<Map.Entry<String, Long>> all = new ArrayList<>();
		all.add(senderEntry);
		var targets = receivers.get();
		var perTarget = senderEntry.getValue() / targets.size();
		for (String target : targets) {
			all.add(new AbstractMap.SimpleEntry<>(target, -perTarget));
		}
		return all;
	}

	public TokenTransferList specializedFor(HapiApiSpec spec) {
		var scopedTransfers = TokenTransferList.newBuilder();
		var id = isTrulyToken() ? spec.registry().getTokenID(token) : HBAR_SENTINEL_TOKEN_ID;
		scopedTransfers.setToken(id);
		if (sender.isPresent()) {
			scopedTransfers.addTransfers(adjustment(sender.get(), -amount, spec));
		}
		if (receiver.isPresent()) {
			scopedTransfers.addTransfers(adjustment(receiver.get(), +amount, spec));
		} else if (receivers.isPresent()) {
			var targets = receivers.get();
			var amountPerReceiver = amount / targets.size();
			for (int i = 0, n = targets.size(); i < n; i++) {
				scopedTransfers.addTransfers(adjustment(targets.get(i), +amountPerReceiver, spec));
			}
		}
		return scopedTransfers.build();
	}

	private AccountAmount adjustment(String name, long value, HapiApiSpec spec) {
		return AccountAmount.newBuilder()
				.setAccountID(spec.registry().getAccountID(name))
				.setAmount(value)
				.build();
	}

	public static class Builder {
		private final long amount;
		private final String token;

		public Builder(long amount, String token) {
			this.token = token;
			this.amount = amount;
		}

		public TokenMovement between(String sender, String receiver) {
			return new TokenMovement(
					token,
					Optional.of(sender),
					amount,
					Optional.of(receiver),
					Optional.empty());
		}

		public TokenMovement distributing(String sender, String... receivers) {
			return new TokenMovement(
					token,
					Optional.of(sender),
					amount,
					Optional.empty(),
					Optional.of(List.of(receivers)));
		}

		public TokenMovement from(String magician) {
			return new TokenMovement(
					token,
					Optional.of(magician),
					amount,
					Optional.empty(),
					Optional.empty());
		}

		public TokenMovement empty() {
			return new TokenMovement(token, Optional.empty(), amount, Optional.empty(), Optional.empty());
		}
	}

	public static Builder moving(long amount, String token) {
		return new Builder(amount, token);
	}

	public static Builder movingHbar(long amount) {
		return new Builder(amount, HapiApiSuite.HBAR_TOKEN_SENTINEL);
	}
}
