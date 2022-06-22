package com.hedera.services.bdd.spec.transactions.util;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.spec.fees.AdapterUtils;
import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.crypto.CryptoCreateMeta;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.RandomGenerateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hederahashgraph.fee.SigValueObj;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType;
import static com.hedera.services.bdd.spec.transactions.TxnFactory.bannerWith;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asId;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.netOf;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class HapiRandomGenerate extends HapiTxnOp<HapiRandomGenerate> {
	static final Logger log = LogManager.getLogger(HapiRandomGenerate.class);

	private Optional<Integer> range = Optional.empty();

	public HapiRandomGenerate() {
	}

	public HapiRandomGenerate(int range) {
		this.range = Optional.of(range);
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.RandomGenerate;
	}

	public HapiRandomGenerate range(int range) {
		this.range = Optional.of(range);
		return this;
	}

	@Override
	protected HapiRandomGenerate self() {
		return this;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		return spec.fees().maxFeeTinyBars();
//		return spec.fees().forActivityBasedOp(
//				HederaFunctionality.RandomGenerate, this::usageEstimate, txn, numPayerKeys);
	}

	private FeeData usageEstimate(TransactionBody txn, SigValueObj svo) {
		var baseMeta = new BaseTransactionMeta(txn.getMemoBytes().size(), 0);
		var opMeta = new CryptoCreateMeta(txn.getCryptoCreateAccount());
		var accumulator = new UsageAccumulator();
		cryptoOpsUsage.cryptoCreateUsage(suFrom(svo), baseMeta, opMeta, accumulator);
		return AdapterUtils.feeDataFrom(accumulator);
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		RandomGenerateTransactionBody opBody = spec
				.txns()
				.<RandomGenerateTransactionBody, RandomGenerateTransactionBody.Builder>body(
						RandomGenerateTransactionBody.class, b -> {
							if (range.isPresent()) {
								b.setRange(range.get());
							}
						});
		return b -> b.setRandomGenerate(opBody);
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		return Arrays.asList(
				spec -> spec.registry().getKey(effectivePayer(spec)));
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getUtilSvcStub(targetNodeFor(spec), useTls)::randomGenerate;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		return super.toStringHelper().add("range", range);
	}
}
