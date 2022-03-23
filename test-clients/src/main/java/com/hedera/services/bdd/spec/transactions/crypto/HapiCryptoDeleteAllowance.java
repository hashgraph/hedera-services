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
import com.google.protobuf.BoolValue;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.fees.AdapterUtils;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountInfo;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.crypto.CryptoApproveAllowanceMeta;
import com.hedera.services.usage.crypto.CryptoDeleteAllowanceMeta;
import com.hedera.services.usage.crypto.ExtantCryptoContext;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.CryptoWipeAllowance;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.NftWipeAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenWipeAllowance;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hedera.services.bdd.suites.crypto.CryptoApproveAllowanceSuite.MISSING_OWNER;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class HapiCryptoDeleteAllowance extends HapiTxnOp<HapiCryptoDeleteAllowance> {
	static final Logger log = LogManager.getLogger(HapiCryptoDeleteAllowance.class);

	private List<CryptoAllowances> cryptoAllowances = new ArrayList<>();
	private List<TokenAllowances> tokenAllowances = new ArrayList<>();
	private List<NftAllowances> nftAllowances = new ArrayList<>();

	public HapiCryptoDeleteAllowance() {
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.CryptoApproveAllowance;
	}

	@Override
	protected HapiCryptoDeleteAllowance self() {
		return this;
	}

	public HapiCryptoDeleteAllowance addCryptoDeleteAllowance(String owner) {
		cryptoAllowances.add(CryptoAllowances.from(owner));
		return this;
	}

	public HapiCryptoDeleteAllowance addTokenDeleteAllowance(String owner, String token) {
		tokenAllowances.add(TokenAllowances.from(owner, token));
		return this;
	}

	public HapiCryptoDeleteAllowance addNftDeleteAllowance(String owner, String token, List<Long> serials) {
		nftAllowances.add(NftAllowances.from(owner, token, serials));
		return this;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		try {
			FeeCalculator.ActivityMetrics metricsCalc = (_txn, svo) -> {
				var baseMeta = new BaseTransactionMeta(_txn.getMemoBytes().size(), 0);
				var opMeta = new CryptoDeleteAllowanceMeta(_txn.getCryptoDeleteAllowance(),
						_txn.getTransactionID().getTransactionValidStart().getSeconds());
				var accumulator = new UsageAccumulator();
				cryptoOpsUsage.cryptoDeleteAllowanceUsage(suFrom(svo), baseMeta, opMeta, accumulator);
				return AdapterUtils.feeDataFrom(accumulator);
			};
			return spec.fees().forActivityBasedOp(HederaFunctionality.CryptoDeleteAllowance, metricsCalc, txn,
					numPayerKeys);
		} catch (Throwable ignore) {
			return HapiApiSuite.ONE_HBAR;
		}
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		List<CryptoWipeAllowance> callowances = new ArrayList<>();
		List<TokenWipeAllowance> tallowances = new ArrayList<>();
		List<NftWipeAllowance> nftallowances = new ArrayList<>();
		calculateAllowances(spec, callowances, tallowances, nftallowances);
		CryptoDeleteAllowanceTransactionBody opBody = spec
				.txns()
				.<CryptoDeleteAllowanceTransactionBody, CryptoDeleteAllowanceTransactionBody.Builder>body(
						CryptoDeleteAllowanceTransactionBody.class, b -> {
							b.addAllTokenAllowances(tallowances);
							b.addAllCryptoAllowances(callowances);
							b.addAllNftAllowances(nftallowances);
						});
		return b -> b.setCryptoDeleteAllowance(opBody);
	}

	private void calculateAllowances(final HapiApiSpec spec,
			final List<CryptoWipeAllowance> callowances,
			final List<TokenWipeAllowance> tallowances,
			final List<NftWipeAllowance> nftallowances) {
		for (var entry : cryptoAllowances) {
			final var builder = CryptoWipeAllowance.newBuilder();
			if (entry.owner() != MISSING_OWNER) {
				builder.setOwner(spec.registry().getAccountID(entry.owner()));
			}

			callowances.add(builder.build());
		}

		for (var entry : tokenAllowances) {
			final var builder = TokenWipeAllowance.newBuilder()
					.setTokenId(spec.registry().getTokenID(entry.token()));
			if (entry.owner() != MISSING_OWNER) {
				builder.setOwner(spec.registry().getAccountID(entry.owner()));
			}
			tallowances.add(builder.build());
		}
		for (var entry : nftAllowances) {
			final var builder = NftWipeAllowance.newBuilder()
					.setTokenId(spec.registry().getTokenID(entry.token()))
					.addAllSerialNumbers(entry.serials());
			if (entry.owner() != MISSING_OWNER) {
				builder.setOwner(spec.registry().getAccountID(entry.owner()));
			}
			nftallowances.add(builder.build());
		}
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		return Arrays.asList(
				spec -> spec.registry().getKey(effectivePayer(spec)));
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls)::deleteAllowance;
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) {
		if (actualStatus != SUCCESS) {
			return;
		}
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper()
				.add("cryptoDeleteAllowances", cryptoAllowances)
				.add("tokenDeleteAllowances", tokenAllowances)
				.add("nftDeleteAllowances", nftAllowances);
		return helper;
	}

	private record CryptoAllowances(String owner) {
		static CryptoAllowances from(String owner) {
			return new CryptoAllowances(owner);
		}
	}

	private record TokenAllowances(String owner, String token) {
		static TokenAllowances from(String owner, String token) {
			return new TokenAllowances(owner, token);
		}
	}

	private record NftAllowances(String owner, String token, List<Long> serials) {
		static NftAllowances from(String owner, String token, List<Long> serials) {
			return new NftAllowances(owner, token, serials);
		}
	}
}
