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

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.TokenTransfer;
import com.hederahashgraph.api.proto.java.TokenTransfers;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.netOf;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingLong;
import static java.util.stream.Collectors.toList;

public class HapiTokenTransact extends HapiTxnOp<HapiTokenTransact> {
	static final Logger log = LogManager.getLogger(HapiTokenTransact.class);

	public static class TokenMovement {
		private final long amount;
		private final String token;
		private final String sender;
		private final boolean useSymbols;
		private final Optional<String> receiver;

		TokenMovement(
				String token,
				String sender,
				long amount,
				Optional<String> receiver,
				boolean useSymbols
		) {
			this.token = token;
			this.sender = sender;
			this.amount = amount;
			this.receiver = receiver;
			this.useSymbols = useSymbols;
		}

		public List<Map.Entry<String, Long>> generallyInvolved() {
			Map.Entry<String, Long> senderEntry = new AbstractMap.SimpleEntry<>(sender, -amount);
			return receiver.isPresent()
					? List.of(senderEntry, new AbstractMap.SimpleEntry<>(receiver.get(), -amount))
					: List.of(senderEntry);
		}

		public List<TokenTransfer> specializedFor(HapiApiSpec spec) {
			List<TokenTransfer> transfers = new ArrayList<>();
			if (useSymbols)	 {
				var symbol = spec.registry().getSymbol(token);
				transfers.add(symbolicTransfer(sender, symbol, -amount, spec));
				if (receiver.isPresent()) {
					transfers.add(symbolicTransfer(receiver.get(), symbol, +amount, spec));
				}
			} else {
				var id = spec.registry().getTokenID(token);
				transfers.add(idTransfer(sender, id, -amount, spec));
				if (receiver.isPresent()) {
					transfers.add(idTransfer(receiver.get(), id, +amount, spec));
				}
			}

			return transfers;
		}

		private TokenTransfer idTransfer(String name, TokenID id, long value, HapiApiSpec spec) {
			return TokenTransfer.newBuilder()
					.setAccount(spec.registry().getAccountID(name))
					.setToken(TokenRef.newBuilder().setTokenId(id))
					.setAmount(value)
					.build();
		}

		private TokenTransfer symbolicTransfer(String name, String symbol, long value, HapiApiSpec spec) {
			return TokenTransfer.newBuilder()
					.setAccount(spec.registry().getAccountID(name))
					.setToken(TokenRef.newBuilder().setSymbol(symbol))
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
				return new TokenMovement(token, sender, amount, Optional.of(receiver), false);
			}

			public TokenMovement symbolicallyBetween(String sender, String receiver) {
				return new TokenMovement(token, sender, amount, Optional.of(receiver), true);
			}

			public TokenMovement from(String magician) {
				return new TokenMovement(token, magician, amount, Optional.empty(), false);
			}
		}

		public static TokenMovement.Builder moving(long amount, String token) {
			return new TokenMovement.Builder(amount, token);
		}
	}

	private List<TokenMovement> providers;

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.TokenTransact;
	}

	public HapiTokenTransact(TokenMovement... sources) {
		this.providers = List.of(sources);
	}

	@Override
	protected HapiTokenTransact self() {
		return this;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(
				HederaFunctionality.TokenTransact, this::mockTokenTransactUsage, txn, numPayerKeys);
	}

	private FeeData mockTokenTransactUsage(TransactionBody ignoredTxn, SigValueObj ignoredSigUsage) {
		return TxnUtils.defaultPartitioning(
				FeeComponents.newBuilder()
						.setMin(1)
						.setMax(1_000_000)
						.setConstant(2)
						.setBpt(2)
						.setVpt(2)
						.setRbh(2)
						.setSbh(2)
						.setGas(2)
						.setTv(2)
						.setBpr(2)
						.setSbpr(2)
						.build(), 2);
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		TokenTransfers opBody = spec
				.txns()
				.<TokenTransfers, TokenTransfers.Builder>body(
						TokenTransfers.class, b -> {
							b.addAllTransfers(transfersFor(spec));
						});
		return b -> b.setTokenTransfers(opBody);
	}

	@Override
	protected Function<HapiApiSpec, List<Key>> variableDefaultSigners() {
		return spec -> {
			List<Key> partyKeys = new ArrayList<>();
			Map<String, Long> partyInvolvements = providers.stream()
					.map(TokenMovement::generallyInvolved)
					.flatMap(List::stream)
					.collect(groupingBy(
							Map.Entry::getKey,
							summingLong(Map.Entry<String, Long>::getValue)));
			System.out.println(partyInvolvements);
			partyInvolvements.entrySet().forEach(entry -> {
				if (entry.getValue() < 0) {
					partyKeys.add(spec.registry().getKey(entry.getKey()));
				}
			});
			return partyKeys;
		};
	}

	private List<TokenTransfer> transfersFor(HapiApiSpec spec) {
		return providers.stream().map(p -> p.specializedFor(spec)).flatMap(List::stream).collect(toList());
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls)::transferTokens;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper();
		if (txnSubmitted != null) {
			try {
				TransactionBody txn = TransactionBody.parseFrom(txnSubmitted.getBodyBytes());
				helper.add(
						"transfers",
						TxnUtils.readableTokenTransferList(txn.getTokenTransfers()));
			} catch (Exception ignore) {}
		}
		return helper;
	}
}
