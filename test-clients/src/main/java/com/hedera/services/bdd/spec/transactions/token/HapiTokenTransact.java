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
import com.hedera.services.usage.token.TokenTransactUsage;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.hederahashgraph.api.proto.java.TokenRefTransferList;
import com.hederahashgraph.api.proto.java.TokenTransfersTransactionBody;
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

import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static java.util.stream.Collectors.flatMapping;
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

		public TokenRefTransferList specializedFor(HapiApiSpec spec) {
			var scopedTransfers = TokenRefTransferList.newBuilder();
			if (useSymbols)	 {
				var symbol = spec.registry().getSymbol(token);
				scopedTransfers.setToken(TokenRef.newBuilder().setSymbol(symbol).build());
			} else {
				var id = spec.registry().getTokenID(token);
				scopedTransfers.setToken(TokenRef.newBuilder().setTokenId(id).build());
			}
			scopedTransfers.addTransfers(adjustment(sender, -amount, spec));
			if (receiver.isPresent()) {
				scopedTransfers.addTransfers(adjustment(receiver.get(), +amount, spec));
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
				HederaFunctionality.TokenTransact, this::usageEstimate, txn, numPayerKeys);
	}

	private FeeData usageEstimate(TransactionBody txn, SigValueObj svo) {
		return TokenTransactUsage.newEstimate(txn, suFrom(svo)).get();
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		TokenTransfersTransactionBody opBody = spec
				.txns()
				.<TokenTransfersTransactionBody, TokenTransfersTransactionBody.Builder>body(
						TokenTransfersTransactionBody.class, b -> {
							b.addAllTokenTransfers(transfersFor(spec));
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
			partyInvolvements.entrySet().forEach(entry -> {
				if (entry.getValue() < 0 || spec.registry().isSigRequired(entry.getKey())) {
					partyKeys.add(spec.registry().getKey(entry.getKey()));
				}
			});
			return partyKeys;
		};
	}

	private List<TokenRefTransferList> transfersFor(HapiApiSpec spec) {
		Map<TokenRef, List<AccountAmount>> aggregated = providers.stream()
				.map(p -> p.specializedFor(spec))
				.collect(groupingBy(
						TokenRefTransferList::getToken,
						flatMapping(xfers -> xfers.getTransfersList().stream(), toList())));
		return aggregated.entrySet().stream()
				.map(entry -> TokenRefTransferList.newBuilder()
						.setToken(entry.getKey())
						.addAllTransfers(entry.getValue())
						.build())
				.collect(toList());
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
