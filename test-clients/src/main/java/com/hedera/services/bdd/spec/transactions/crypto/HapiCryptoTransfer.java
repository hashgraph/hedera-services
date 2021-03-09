package com.hedera.services.bdd.spec.transactions.crypto;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.transactions.nft.Acquisition;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.usage.crypto.CryptoTransferUsage;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.NftTransferList;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.HBAR_SENTINEL_TOKEN_ID;
import static java.util.stream.Collectors.*;

import java.util.stream.Collector;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class HapiCryptoTransfer extends HapiTxnOp<HapiCryptoTransfer> {
	static final Logger log = LogManager.getLogger(HapiCryptoTransfer.class);

	private static final List<Acquisition> MISSING_NFT_PROVIDERS = null;
	private static final List<TokenMovement> MISSING_TOKEN_AWARE_PROVIDERS = null;
	private static final Function<HapiApiSpec, TransferList> MISSING_HBAR_ONLY_PROVIDER = null;
	private static final int DEFAULT_TOKEN_TRANSFER_USAGE_MULTIPLIER = 60;

	private boolean logResolvedStatus = false;

	private Function<HapiApiSpec, TransferList> hbarOnlyProvider = MISSING_HBAR_ONLY_PROVIDER;
	private List<TokenMovement> tokenAwareProviders = MISSING_TOKEN_AWARE_PROVIDERS;
	private List<Acquisition> nftProviders = MISSING_NFT_PROVIDERS;

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.CryptoTransfer;
	}

	public HapiCryptoTransfer showingResolvedStatus() {
		logResolvedStatus = true;
		return this;
	}

	private static Collector<TransferList, ?, TransferList> transferCollector(
			BinaryOperator<List<AccountAmount>> reducer
	) {
		return collectingAndThen(
				reducing(
						Collections.emptyList(),
						TransferList::getAccountAmountsList,
						reducer),
				aList -> TransferList.newBuilder().addAllAccountAmounts(aList).build());
	}

	private final static BinaryOperator<List<AccountAmount>> accountMerge = (a, b) ->
			Stream.of(a, b).flatMap(List::stream).collect(collectingAndThen(
					groupingBy(AccountAmount::getAccountID, mapping(AccountAmount::getAmount, toList())),
					aMap -> aMap.entrySet()
							.stream()
							.map(entry ->
									AccountAmount.newBuilder()
											.setAccountID(entry.getKey())
											.setAmount(entry.getValue().stream().mapToLong(l -> (long) l).sum())
											.build())
							.collect(toList())));
	private final static Collector<TransferList, ?, TransferList> mergingAccounts = transferCollector(accountMerge);

	@SafeVarargs
	public HapiCryptoTransfer(Function<HapiApiSpec, TransferList>... providers) {
		if (providers.length == 0) {
			hbarOnlyProvider = ignore -> TransferList.getDefaultInstance();
		} else if (providers.length == 1) {
			hbarOnlyProvider = providers[0];
		} else {
			this.hbarOnlyProvider = spec -> Stream.of(providers).map(p -> p.apply(spec)).collect(mergingAccounts);
		}
	}

	public HapiCryptoTransfer() {
	}

	public HapiCryptoTransfer(TokenMovement... sources) {
		this.tokenAwareProviders = List.of(sources);
	}

	public HapiCryptoTransfer changingOwnership(Acquisition... acquisitions) {
		nftProviders = List.of(acquisitions);
		return this;
	}

	@Override
	protected Function<HapiApiSpec, List<Key>> variableDefaultSigners() {
		if (hbarOnlyProvider != MISSING_HBAR_ONLY_PROVIDER) {
			return hbarOnlyVariableDefaultSigners();
		} else {
			return tokenAwareVariableDefaultSigners();
		}
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
							AccountAmount.newBuilder().setAccountID(fromAccount).setAmount(
									-1L * amount).build())).build();
		};
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		CryptoTransferTransactionBody opBody = spec.txns()
				.<CryptoTransferTransactionBody, CryptoTransferTransactionBody.Builder>body(
						CryptoTransferTransactionBody.class, b -> {
							if (hbarOnlyProvider != MISSING_HBAR_ONLY_PROVIDER) {
								b.setTransfers(hbarOnlyProvider.apply(spec));
							}
							if (tokenAwareProviders != MISSING_TOKEN_AWARE_PROVIDERS) {
								var xfers = transfersFor(spec);
								for (TokenTransferList scopedXfers : xfers) {
									if (scopedXfers.getToken() == HBAR_SENTINEL_TOKEN_ID) {
										b.setTransfers(TransferList.newBuilder()
												.addAllAccountAmounts(scopedXfers.getTransfersList())
												.build());
									} else {
										b.addTokenTransfers(scopedXfers);
									}
								}
							}
							if (nftProviders != MISSING_NFT_PROVIDERS) {
								b.addAllNftTransfers(nftTransfersFor(spec));
							}
						}
				);
		return builder -> builder.setCryptoTransfer(opBody);
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(
				HederaFunctionality.CryptoTransfer,
				(_txn, _svo) -> usageEstimate(_txn, _svo, spec.fees().tokenTransferUsageMultiplier()),
				txn,
				numPayerKeys);
	}

	private FeeData usageEstimate(TransactionBody txn, SigValueObj svo, int multiplier) {
		return CryptoTransferUsage.newEstimate(txn, suFrom(svo))
				.givenTokenMultiplier(multiplier)
				.get();
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
				TransactionBody txn = CommonUtils.extractTransactionBody(txnSubmitted);
				helper.add(
						"transfers",
						TxnUtils.readableTransferList(txn.getCryptoTransfer().getTransfers()));
				helper.add(
						"tokenTransfers",
						TxnUtils.readableTokenTransfers(txn.getCryptoTransfer().getTokenTransfersList()));
				helper.add(
						"nftTransfers",
						TxnUtils.readableNftTransfers(txn.getCryptoTransfer().getNftTransfersList()));
			} catch (Exception ignore) { }
		}
		return helper;
	}

	private Function<HapiApiSpec, List<Key>> tokenAwareVariableDefaultSigners() {
		return spec -> {
			Set<Key> partyKeys = new HashSet<>();
			if (tokenAwareProviders != MISSING_TOKEN_AWARE_PROVIDERS) {
				Map<String, Long> partyInvolvements = tokenAwareProviders.stream()
						.map(TokenMovement::generallyInvolved)
						.flatMap(List::stream)
						.collect(groupingBy(
								Map.Entry::getKey,
								summingLong(Map.Entry<String, Long>::getValue)));
				partyInvolvements.forEach((account, value) -> {
					int divider = account.indexOf("|");
					var key = account.substring(divider + 1);
					if (value < 0 || spec.registry().isSigRequired(key)) {
						partyKeys.add(spec.registry().getKey(key));
					}
				});
			}
			addNftSignersFor(spec, partyKeys);
			return new ArrayList<>(partyKeys);
		};
	}

	private Function<HapiApiSpec, List<Key>> hbarOnlyVariableDefaultSigners() {
		return spec -> {
			Set<Key> partyKeys = new HashSet<>();
			TransferList transfers = hbarOnlyProvider.apply(spec);
			transfers.getAccountAmountsList().stream().forEach(accountAmount -> {
				String account = spec.registry().getAccountIdName(accountAmount.getAccountID());
				boolean isPayer = (accountAmount.getAmount() < 0L);
				if (isPayer || spec.registry().isSigRequired(account)) {
					partyKeys.add(spec.registry().getKey(account));
				}
			});
			addNftSignersFor(spec, partyKeys);
			return new ArrayList<>(partyKeys);
		};
	}

	private void addNftSignersFor(HapiApiSpec spec, Set<Key> partyKeys) {
		if (nftProviders != MISSING_NFT_PROVIDERS) {
			var registry = spec.registry();
			nftProviders.forEach(acquisition -> {
						partyKeys.add(registry.getKey(acquisition.getFromAccount()));
						if (registry.isSigRequired(acquisition.getToAccount())) {
							partyKeys.add(registry.getKey(acquisition.getToAccount()));
						}
					}
			);
		}
	}

	private List<NftTransferList> nftTransfersFor(HapiApiSpec spec) {
		Map<NftID, List<NftTransfer>> aggregated = nftProviders.stream()
				.map(p -> p.specializedFor(spec))
				.collect(groupingBy(
						NftTransferList::getNft,
						flatMapping(
								xfers -> xfers.getTransferList().stream(),
								toList())));
		return aggregated.entrySet().stream()
				.map(entry -> NftTransferList.newBuilder()
						.setNft(entry.getKey())
						.addAllTransfer(entry.getValue())
						.build())
				.collect(toList());
	}

	private List<TokenTransferList> transfersFor(HapiApiSpec spec) {
		Map<TokenID, List<AccountAmount>> aggregated = tokenAwareProviders.stream()
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
	protected void updateStateOf(HapiApiSpec spec) throws Throwable {
		if (logResolvedStatus) {
			log.info("Resolved to {}", actualStatus);
		}
	}
}
