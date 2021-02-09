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
import com.google.protobuf.StringValue;
import com.google.protobuf.UInt64Value;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.queries.file.HapiGetFileInfo;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.usage.crypto.ExtantCryptoContext;
import com.hedera.services.usage.file.ExtantFileContext;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountInfo;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnFactory.expiryGiven;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.inConsensusOrder;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.defaultUpdateSigners;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.suFrom;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class HapiCryptoUpdate extends HapiTxnOp<HapiCryptoUpdate> {
	static final Logger log = LogManager.getLogger(HapiCryptoUpdate.class);

	private final String account;
	private OptionalLong sendThreshold = OptionalLong.empty();
	private Optional<Key> updKey = Optional.empty();
	private Optional<Long> lifetimeSecs = Optional.empty();
	private Optional<String> entityMemo = Optional.empty();
	private Optional<String> updKeyName = Optional.empty();
	private Optional<Boolean> updSigRequired = Optional.empty();

	public HapiCryptoUpdate(String account) {
		this.account = account;
	}

	public HapiCryptoUpdate sendThreshold(long v) {
		sendThreshold = OptionalLong.of(v);
		return this;
	}
	public HapiCryptoUpdate lifetime(long secs) {
		lifetimeSecs = Optional.of(secs);
		return this;
	}
	public HapiCryptoUpdate entityMemo(String memo) {
		entityMemo = Optional.of(memo);
		return this;
	}
	public HapiCryptoUpdate key(String name) {
		updKeyName = Optional.of(name);
		return this;
	}
	public HapiCryptoUpdate receiverSigRequired(boolean isRequired) {
		updSigRequired = Optional.of(isRequired);
		return this;
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.CryptoUpdate;
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) {
		if (actualStatus != SUCCESS) {
			return;
		}
		updKey.ifPresent(k -> spec.registry().saveKey(account, k));
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		try {
			updKey = updKeyName.map(spec.registry()::getKey);
		} catch (Exception ignore) { }
		var id = TxnUtils.asId(account, spec);
		CryptoUpdateTransactionBody opBody = spec
				.txns()
				.<CryptoUpdateTransactionBody, CryptoUpdateTransactionBody.Builder>body(
						CryptoUpdateTransactionBody.class, builder -> {
							builder.setAccountIDToUpdate(id);
							updSigRequired.ifPresent(u -> builder.setReceiverSigRequiredWrapper(BoolValue.of(u)));
							updKey.ifPresent(k -> builder.setKey(k));
							entityMemo.ifPresent(m -> builder.setMemo(StringValue.newBuilder().setValue(m).build()));
							sendThreshold.ifPresent(v ->
									builder.setSendRecordThresholdWrapper(
											UInt64Value.newBuilder().setValue(v).build()));
						}
				);
		return builder -> builder.setCryptoUpdateAccount(opBody);
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		return defaultUpdateSigners(account, updKeyName, this::effectivePayer);
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getCryptoSvcStub(targetNodeFor(spec), useTls)::updateAccount;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		try {
			final CryptoGetInfoResponse.AccountInfo info = lookupInfo(spec);
			FeeCalculator.ActivityMetrics metricsCalc = (_txn, svo) -> {
				var ctx = ExtantCryptoContext.newBuilder()
						.setCurrentExpiry(info.getExpirationTime().getSeconds())
						.setCurrentMemo(info.getMemo())
						.setCurrentKey(info.getKey())
						.setCurrentlyHasProxy(info.hasProxyAccountID())
						.build();
				return cryptoOpsUsage.cryptoUpdateUsage(_txn, suFrom(svo), ctx);
			};
			return spec.fees().forActivityBasedOp(HederaFunctionality.CryptoUpdate, metricsCalc, txn, numPayerKeys);
		} catch (Throwable ignore) {
			return HapiApiSuite.ONE_HBAR;
		}
	}

	private CryptoGetInfoResponse.AccountInfo lookupInfo(HapiApiSpec spec) throws Throwable {
		HapiGetAccountInfo subOp = getAccountInfo(account).noLogging();
		Optional<Throwable> error = subOp.execFor(spec);
		if (error.isPresent()) {
			if (!loggingOff) {
				log.warn("Unable to look up current account info!", error.get());
			}
			throw error.get();
		}
		return subOp.getResponse().getCryptoGetInfo().getAccountInfo();
	}

	@Override
	protected HapiCryptoUpdate self() {
		return this;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		return super.toStringHelper().add("account", account);
	}
}
