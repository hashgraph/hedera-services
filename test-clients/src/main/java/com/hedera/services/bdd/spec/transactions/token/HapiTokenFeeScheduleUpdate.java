package com.hedera.services.bdd.spec.transactions.token;

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
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.queries.token.HapiGetTokenInfo;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.usage.token.TokenUpdateUsage;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CustomFee.Builder;
import com.hederahashgraph.api.proto.java.TokenInfo;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;

import com.hederahashgraph.api.proto.java.CustomFee;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class HapiTokenFeeScheduleUpdate extends HapiTxnOp<HapiTokenFeeScheduleUpdate> {
	static final Logger log = LogManager.getLogger(HapiTokenFeeScheduleUpdate.class);

	private String token;
	private Optional<String> newCustomFeesKey = Optional.empty();

	private boolean useEmptyAdminKeyList = false;

	private final List<Function<HapiApiSpec, CustomFee>> feeScheduleSuppliers = new ArrayList<>();


	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.TokenUpdate;
	}

	public HapiTokenFeeScheduleUpdate(String token) {
		this.token = token;
	}

	public HapiTokenFeeScheduleUpdate customFeesKey(String newCustomFeesKey) {
		this.newCustomFeesKey = Optional.of(newCustomFeesKey);
		return this;
	}

	public HapiTokenFeeScheduleUpdate withCustom(Function<HapiApiSpec, CustomFee> supplier) {
		feeScheduleSuppliers.add(supplier);
		return this;
	}

	@Override
	protected HapiTokenFeeScheduleUpdate self() {
		return this;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		try {
			final TokenInfo info = lookupInfo(spec);
			// TODO: add fee calc for this
			FeeCalculator.ActivityMetrics metricsCalc = (_txn, svo) -> {
				var estimate = TokenUpdateUsage.newEstimate(_txn, suFrom(svo));
				estimate.givenCurrentExpiry(info.getExpiry().getSeconds());
				return estimate.get();
			};
			return spec.fees().forActivityBasedOp(HederaFunctionality.TokenUpdate, metricsCalc, txn, numPayerKeys);
		} catch (Throwable ignore) {
			return HapiApiSuite.ONE_HBAR;
		}
	}

	private TokenInfo lookupInfo(HapiApiSpec spec) throws Throwable {
		HapiGetTokenInfo subOp = getTokenInfo(token).noLogging();
		Optional<Throwable> error = subOp.execFor(spec);
		if (error.isPresent()) {
			if (!loggingOff) {
				log.warn("Unable to look up current info for "
								+ HapiPropertySource.asTokenString(spec.registry().getTokenID(token)),
						error.get());
			}
			throw error.get();
		}
		return subOp.getResponse().getTokenGetInfo().getTokenInfo();
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		var id = TxnUtils.asTokenId(token, spec);
		TokenFeeScheduleUpdateTransactionBody opBody = spec
				.txns()
				.<TokenFeeScheduleUpdateTransactionBody, TokenFeeScheduleUpdateTransactionBody.Builder>body(
						TokenFeeScheduleUpdateTransactionBody.class, b -> {
							b.setTokenId(id);

							if (!feeScheduleSuppliers.isEmpty()) {
								for (var supplier : feeScheduleSuppliers) {
									b.addCustomFees(supplier.apply(spec));
								}
							}
						});
		return b -> b.setTokenFeeScheduleUpdate(opBody);
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		List<Function<HapiApiSpec, Key>> signers = new ArrayList<>();
		signers.add(spec -> spec.registry().getKey(effectivePayer(spec)));
		signers.add(spec -> {
			try {
				return spec.registry().getAdminKey(token); // TODO: switch to feeScheduleKey when it's ready
			} catch (Exception ignore) {
				return Key.getDefaultInstance();
			}
		});
		return signers;
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getTokenSvcStub(targetNodeFor(spec), useTls)::updateTokenFeeSchedule;
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) {
		if (actualStatus != SUCCESS) {
			return;
		}
		var registry = spec.registry();
		if (useEmptyAdminKeyList) {
			registry.forgetAdminKey(token);
		}
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super.toStringHelper()
				.add("token", token);
		return helper;
	}
}
