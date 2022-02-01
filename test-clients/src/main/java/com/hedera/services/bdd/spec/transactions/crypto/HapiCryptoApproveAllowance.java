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
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class HapiCryptoApproveAllowance extends HapiTxnOp<HapiCryptoApproveAllowance> {
	static final Logger log = LogManager.getLogger(HapiCryptoApproveAllowance.class);
	private Map<String, Long> cryptoAllowances = new HashMap<>();
	private Map<AllowanceKey, Long> tokenAllowances = new HashMap<>();
	private Map<AllowanceKey, AllowanceVal> nftAllowances = new HashMap<>();
	private String account;

	public HapiCryptoApproveAllowance() {
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.CryptoApproveAllowance;
	}

	@Override
	protected HapiCryptoApproveAllowance self() {
		return this;
	}

	public HapiCryptoApproveAllowance addCryptoAllowance(String spender, long allowance) {
		cryptoAllowances.put(spender, allowance);
		return this;
	}

	public HapiCryptoApproveAllowance addTokenAllowance(String token, String spender, long allowance) {
		tokenAllowances.put(AllowanceKey.from(token, spender), allowance);
		return this;
	}

	public HapiCryptoApproveAllowance addNftAllowance(String token, String spender, boolean approvedForAll,
			List<Long> serials) {
		nftAllowances.put(AllowanceKey.from(token, spender), AllowanceVal.from(approvedForAll, serials));
		return this;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().forActivityBasedOp(HederaFunctionality.CryptoApproveAllowance,
				cryptoFees::getCryptoApproveAllowanceFeeMatrices, txn, numPayerKeys);
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		List<CryptoAllowance> callowances = new ArrayList<>();
		List<TokenAllowance> tallowances = new ArrayList<>();
		List<NftAllowance> nftallowances = new ArrayList<>();
		calculateAllowances(spec, callowances, tallowances, nftallowances);
		CryptoApproveAllowanceTransactionBody opBody = spec
				.txns()
				.<CryptoApproveAllowanceTransactionBody, CryptoApproveAllowanceTransactionBody.Builder>body(
						CryptoApproveAllowanceTransactionBody.class, b -> {
							b.addAllTokenAllowances(tallowances);
							b.addAllCryptoAllowances(callowances);
							b.addAllNftAllowances(nftallowances);
						});
		return b -> b.setCryptoApproveAllowance(opBody);
	}

	private void calculateAllowances(final HapiApiSpec spec,
			final List<CryptoAllowance> callowances,
			final List<TokenAllowance> tallowances,
			final List<NftAllowance> nftallowances) {
		for (Map.Entry entry : cryptoAllowances.entrySet()) {
			final var builder = CryptoAllowance.newBuilder()
					.setSpender(spec.registry().getAccountID(entry.getKey().toString()))
					.setAmount((Long) entry.getValue())
					.build();
			callowances.add(builder);
		}

		for (Map.Entry entry : tokenAllowances.entrySet()) {
			final AllowanceKey key = (AllowanceKey) entry.getKey();
			final Long value = (Long) entry.getValue();
			final var builder = TokenAllowance.newBuilder()
					.setTokenId(spec.registry().getTokenID(key.token()))
					.setSpender(spec.registry().getAccountID(key.spender()))
					.setAmount(value)
					.build();
			tallowances.add(builder);
		}
		for (Map.Entry entry : nftAllowances.entrySet()) {
			final AllowanceKey key = (AllowanceKey) entry.getKey();
			final AllowanceVal value = (AllowanceVal) entry.getValue();
			final var builder = NftAllowance.newBuilder()
					.setTokenId(spec.registry().getTokenID(key.token()))
					.setSpender(spec.registry().getAccountID(key.spender()))
					.setApprovedForAll(BoolValue.of(value.approvedForAll()))
					.addAllSerialNumbers(value.serials())
					.build();
			nftallowances.add(builder);
		}
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		return Arrays.asList(
				spec -> spec.registry().getKey(effectivePayer(spec)));
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls)::approveAllowances;
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
				.add("cryptoAllowances", cryptoAllowances)
				.add("tokenAllowances", tokenAllowances)
				.add("nftAllowances", nftAllowances);
		return helper;
	}

	private record AllowanceKey(String token, String spender) {
		static AllowanceKey from(String token, String spender) {
			return new AllowanceKey(token, spender);
		}
	}

	private record AllowanceVal(boolean approvedForAll, List<Long> serials) {
		static AllowanceVal from(boolean approvedForAll, List<Long> serials) {
			return new AllowanceVal(approvedForAll, serials);
		}
	}
}
