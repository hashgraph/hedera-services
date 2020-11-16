package com.hedera.services.bdd.spec.transactions.contract;

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
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.fees.FeeCalculator;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.TxnFactory;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hedera.services.bdd.spec.transactions.contract.HapiContractCreate.DEPRECATED_CID_ADMIN_KEY;

public class HapiContractUpdate extends HapiTxnOp<HapiContractUpdate> {
	static final Logger log = LogManager.getLogger(HapiContractUpdate.class);

	private final String contract;
	private boolean useDeprecatedAdminKey = false;
	private Optional<Long> newExpirySecs = Optional.empty();
	private OptionalLong newExpiryTime = OptionalLong.empty();
	private Optional<String> newKey = Optional.empty();
	private Optional<String> newMemo = Optional.empty();
	private boolean wipeToThresholdKey = false;
	private boolean useEmptyAdminKeyList = false;

	public HapiContractUpdate(String contract) {
		this.contract = contract;
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.ContractUpdate;
	}

	public HapiContractUpdate newKey(String name) {
		newKey = Optional.of(name);
		return this;
	}
	public HapiContractUpdate newExpiryTime(long t) {
		newExpiryTime = OptionalLong.of(t);
		return this;
	}
	public HapiContractUpdate newExpirySecs(long t) {
		newExpirySecs = Optional.of(t);
		return this;
	}
	public HapiContractUpdate newMemo(String s) {
		newMemo = Optional.of(s);
		return this;
	}
	public HapiContractUpdate useDeprecatedAdminKey() {
		useDeprecatedAdminKey = true;
		return this;
	}
	public HapiContractUpdate improperlyEmptyingAdminKey() {
		wipeToThresholdKey = true;
		return this;
	}
	public HapiContractUpdate properlyEmptyingAdminKey() {
		useEmptyAdminKeyList = true;
		return this;
	}

	@Override
	protected void updateStateOf(HapiApiSpec spec) throws Throwable {
		if (actualStatus != SUCCESS) {
			return;
		}
		if (!useDeprecatedAdminKey) {
			newKey.ifPresent(k -> spec.registry().saveKey(contract, spec.registry().getKey(k)));
		}
		if (useEmptyAdminKeyList) {
			spec.registry().forgetAdminKey(contract);
		}
	}

	@Override
	protected Consumer<TransactionBody.Builder> opBodyDef(HapiApiSpec spec) throws Throwable {
		Optional<Key> key = newKey.map(spec.registry()::getKey);
		ContractUpdateTransactionBody opBody = spec
				.txns()
				.<ContractUpdateTransactionBody, ContractUpdateTransactionBody.Builder>body(
						ContractUpdateTransactionBody.class, b -> {
							b.setContractID(spec.registry().getContractId(contract));
							if (useDeprecatedAdminKey) {
								b.setAdminKey(DEPRECATED_CID_ADMIN_KEY);
							} else if (wipeToThresholdKey) {
								b.setAdminKey(TxnUtils.EMPTY_THRESHOLD_KEY);
							} else if (useEmptyAdminKeyList) {
								b.setAdminKey(TxnUtils.EMPTY_KEY_LIST);
							} else {
								key.ifPresent(k -> b.setAdminKey(k));
							}
							newExpiryTime.ifPresent(s ->
									b.setExpirationTime(Timestamp.newBuilder().setSeconds(s).build()));
							newExpirySecs.ifPresent(s -> b.setExpirationTime(TxnFactory.expiryGiven(s)));
							newMemo.ifPresent(s -> b.setMemo(s));
						}
				);
		return builder -> builder.setContractUpdateInstance(opBody);
	}

	@Override
	protected List<Function<HapiApiSpec, Key>> defaultSigners() {
		List<Function<HapiApiSpec, Key>> signers = new ArrayList<>(oldDefaults());
		if (!useDeprecatedAdminKey && newKey.isPresent()) {
			signers.add(spec -> spec.registry().getKey(newKey.get()));
		}
		return signers;
	}
	private List<Function<HapiApiSpec, Key>> oldDefaults() {
		return List.of(
				spec -> spec.registry().getKey(effectivePayer(spec)),
				spec -> spec.registry().getKey(contract));
	}

	@Override
	protected Function<Transaction, TransactionResponse> callToUse(HapiApiSpec spec) {
		return spec.clients().getScSvcStub(targetNodeFor(spec), useTls)::updateContract;
	}

	@Override
	protected long feeFor(HapiApiSpec spec, Transaction txn, int numPayerKeys) throws Throwable {
		Timestamp newExpiry = TxnFactory.expiryGiven(newExpirySecs.orElse(spec.setup().defaultExpirationSecs()));
		Timestamp oldExpiry = TxnUtils.currContractExpiry(contract, spec);
		final Timestamp expiry = TxnUtils.inConsensusOrder(oldExpiry, newExpiry) ? newExpiry : oldExpiry;
		FeeCalculator.ActivityMetrics metricsCalc = (txBody, sigUsage) ->
				scFees.getContractUpdateTxFeeMatrices(txBody, expiry, sigUsage);
		return spec.fees().forActivityBasedOp(HederaFunctionality.ContractUpdate, metricsCalc, txn, numPayerKeys);
	}

	@Override
	protected HapiContractUpdate self() {
		return this;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		return super.toStringHelper().add("contract", contract);
	}
}

