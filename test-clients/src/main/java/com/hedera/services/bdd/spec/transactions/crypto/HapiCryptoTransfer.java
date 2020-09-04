package com.hedera.services.bdd.spec.transactions.crypto;

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
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static java.util.stream.Collectors.*;

import java.util.stream.Collector;
import java.util.stream.Stream;
import static java.util.stream.Collectors.toList;

public class HapiCryptoTransfer extends HapiTxnOp<HapiCryptoTransfer> {
	private final int numLogicalTransfers;
	private final Function<HapiApiSpec, TransferList> transfersProvider;

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.CryptoTransfer;
	}

	private static Collector<TransferList, ?, TransferList> transferCollector(
			BinaryOperator<List<AccountAmount>> reducer) {
		return collectingAndThen(
				reducing(Collections.EMPTY_LIST, TransferList::getAccountAmountsList, reducer),
				aList -> TransferList.newBuilder().addAllAccountAmounts((List<AccountAmount>)aList).build());
	}
	private final static BinaryOperator<List<AccountAmount>> accountAppend = (a, b) ->
			Stream.of(a, b).flatMap(List::stream).collect(toList());
	private final static BinaryOperator<List<AccountAmount>> accountMerge = (a, b) ->
			Stream.of(a, b).flatMap(List::stream).collect(collectingAndThen(
					groupingBy(AccountAmount::getAccountID, mapping(AccountAmount::getAmount, toList())),
					aMap -> aMap.entrySet()
							.stream()
							.map(entry ->
								AccountAmount.newBuilder()
										.setAccountID(entry.getKey())
										.setAmount(entry.getValue().stream().mapToLong(l -> (long)l).sum())
										.build())
							.collect(toList())));
	private final static Collector<TransferList, ?, TransferList> mergingAccounts = transferCollector(accountMerge);
	private final static Collector<TransferList, ?, TransferList> appendingAccounts = transferCollector(accountAppend);

	@SafeVarargs
	public HapiCryptoTransfer(Function<HapiApiSpec, TransferList>... providers) {
		numLogicalTransfers = providers.length;
		if (providers.length == 0) {
			transfersProvider = ignore -> TransferList.getDefaultInstance();
		} else if (providers.length == 1) {
			transfersProvider = providers[0];
		} else {
			this.transfersProvider = spec -> Stream.of(providers).map(p -> p.apply(spec)).collect(mergingAccounts);
		}
	}

	@Override
	protected Function<HapiApiSpec, List<Key>> variableDefaultSigners() {
		return spec -> {
			List<Key> partyKeys = new ArrayList<>();
			TransferList transfers = transfersProvider.apply(spec);
			transfers.getAccountAmountsList().stream().forEach(accountAmount -> {
				String account = spec.registry().getAccountIdName(accountAmount.getAccountID());
				boolean isPayer = (accountAmount.getAmount() < 0L);
				if (isPayer || spec.registry().isSigRequired(account)) {
					partyKeys.add(spec.registry().getKey(account));
				}
			});
			return partyKeys;
		};
	}

	public static Function<HapiApiSpec, TransferList> tinyBarsFromTo(String from, String to, long amount) {
		return tinyBarsFromTo(from, to, ignore -> amount);
	}

	public static Function<HapiApiSpec, TransferList> tinyBarsFromTo(
			String from, String to, Function<HapiApiSpec, Long> amountFn) {
		return spec -> {
			long amount = amountFn.apply(spec);
			AccountID toAccount = asId(to, spec);
			AccountID fromAccount = asId(from, spec);
			return TransferList.newBuilder()
					.addAllAccountAmounts(Arrays.asList(
						AccountAmount.newBuilder().setAccountID(toAccount).setAmount(amount).build(),
						AccountAmount.newBuilder().setAccountID(fromAccount).setAmount(-1L * amount).build())).build();
		};
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		CryptoTransferTransactionBody opBody = spec.txns()
				.<CryptoTransferTransactionBody, CryptoTransferTransactionBody.Builder>body(
						CryptoTransferTransactionBody.class, builder -> {
							builder.setTransfers(transfersProvider.apply(spec));
						}
				);
		return builder -> builder.setCryptoTransfer(opBody);
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerSigs) throws Throwable {
		return spec.fees().forActivityBasedOp(
				HederaFunctionality.CryptoTransfer,
				cryptoFees::getCryptoTransferTxFeeMatrices,
				txn, numPayerSigs);
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls)::cryptoTransfer;
	}

	@Override
	protected HapiCryptoTransfer self() {
		return this;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper();
		if (txnSubmitted != null) {
			try {
				TransactionBody txn = TransactionBody.parseFrom(txnSubmitted.getBodyBytes());
				helper.add(
						"transfers",
						TxnUtils.readableTransferList(txn.getCryptoTransfer().getTransfers()));
			} catch (Exception ignore) {}
		}
		return helper;
	}
}
