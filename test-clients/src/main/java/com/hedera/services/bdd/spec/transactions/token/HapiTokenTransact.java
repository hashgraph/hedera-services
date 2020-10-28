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
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.usage.token.TokenTransactUsage;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TokenTransfersTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.summingLong;
import static java.util.stream.Collectors.toList;

public class HapiTokenTransact extends HapiTxnOp<HapiTokenTransact> {
	static final Logger log = LogManager.getLogger(HapiTokenTransact.class);

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
							var xfers = transfersFor(spec);
							for (TokenTransferList scopedXfers : xfers) {
								if (scopedXfers.getToken() == TokenID.getDefaultInstance()) {
									b.setHbarTransfers(TransferList.newBuilder()
											.addAllAccountAmounts(scopedXfers.getTransfersList())
											.build());
								} else {
									b.addTokenTransfers(scopedXfers);
								}
							}
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

	private List<TokenTransferList> transfersFor(HapiApiSpec spec) {
		Map<TokenID, List<AccountAmount>> aggregated = providers.stream()
				.map(p -> p.specializedFor(spec))
				.collect(groupingBy(
						TokenTransferList::getToken,
						flatMapping(xfers -> xfers.getTransfersList().stream(), toList())));
		return aggregated.entrySet().stream()
				.map(entry -> TokenTransferList.newBuilder()
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
				TransactionBody txn = CommonUtils.extractTransactionBody(txnSubmitted);
				helper.add(
						"transfers",
						TxnUtils.readableTokenTransferList(txn.getTokenTransfers()));
			} catch (Exception ignore) {}
		}
		return helper;
	}
}
