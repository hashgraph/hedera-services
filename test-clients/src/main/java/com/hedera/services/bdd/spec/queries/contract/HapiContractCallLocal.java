package com.hedera.services.bdd.spec.queries.contract;

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
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.infrastructure.meta.ActionableContractCallLocal;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.ContractFnResultAsserts;
import com.hedera.services.bdd.spec.assertions.ErroringAsserts;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ethereum.core.CallTransaction;
import org.spongycastle.util.encoders.Hex;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.assertions.AssertUtils.rethrowSummaryError;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;

public class HapiContractCallLocal extends HapiQueryOp<HapiContractCallLocal> {
	private static final Logger log = LogManager.getLogger(HapiContractCallLocal.class);

	private String FALLBACK_ABI = "<empty>";
	private String abi;
	private String contract;
	private Object[] params;
	private Optional<Long> gas = Optional.empty();
	private Optional<Long> maxResultSize = Optional.empty();
	private Optional<String> details = Optional.empty();
	private Optional<ContractFnResultAsserts> expectations = Optional.empty();
	private Optional<Function<HapiApiSpec, Object[]>> paramsFn = Optional.empty();

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.ContractCallLocal;
	}

	public static HapiContractCallLocal fromDetails(String actionable) {
		HapiContractCallLocal localCall = new HapiContractCallLocal();
		localCall.details = Optional.of(actionable);
		return localCall;
	}

	private HapiContractCallLocal() {
	}

	public HapiContractCallLocal(String abi, String contract, Object... params) {
		this.abi = abi;
		this.contract = contract;
		this.params = params;
	}

	public HapiContractCallLocal(String abi, String contract, Function<HapiApiSpec, Object[]> fn) {
		this(abi, contract);
		paramsFn = Optional.of(fn);
	}

	public HapiContractCallLocal(String contract) {
		this.abi = FALLBACK_ABI;
		this.params = new Object[0];
		this.contract = contract;
	}

	public HapiContractCallLocal has(ContractFnResultAsserts provider) {
		expectations = Optional.of(provider);
		return this;
	}

	public HapiContractCallLocal maxResultSize(long size) {
		maxResultSize = Optional.of(size);
		return this;
	}

	public HapiContractCallLocal gas(long amount) {
		gas = Optional.of(amount);
		return this;
	}

	@Override
	protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable {
		if (expectations.isPresent()) {
			ContractFunctionResult actual = response.getContractCallLocal().getFunctionResult();
			log.info(Hex.toHexString(actual.getContractCallResult().toByteArray()));
			ErroringAsserts<ContractFunctionResult> asserts = expectations.get().assertsFor(spec);
			List<Throwable> errors = asserts.errorsIn(actual);
			rethrowSummaryError(log, "Bad local call result!", errors);
		}
	}

	@Override
	protected HapiContractCallLocal self() {
		return this;
	}

	@Override
	protected void submitWith(HapiApiSpec spec, Transaction payment) {
		Query query = getContractCallLocal(spec, payment, false);
		response = spec.clients().getScSvcStub(targetNodeFor(spec), useTls).contractCallLocalMethod(query);
		if (verboseLoggingOn) {
			log.info(spec.logPrefix() + this + " result = " + response.getContractCallLocal().getFunctionResult());
		}
	}

	@Override
	protected long lookupCostWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getContractCallLocal(spec, payment, true);
		Response response = spec.clients().getScSvcStub(targetNodeFor(spec), useTls).contractCallLocalMethod(query);
		return costFrom(response);
	}

	private Query getContractCallLocal(HapiApiSpec spec, Transaction payment, boolean costOnly) {
		if (details.isPresent()) {
			ActionableContractCallLocal actionable = spec.registry().getActionableLocalCall(details.get());
			contract = actionable.getContract();
			abi = actionable.getDetails().getAbi();
			params = actionable.getDetails().getExampleArgs();
		} else if (paramsFn.isPresent()) {
			params = paramsFn.get().apply(spec);
		}

		byte[] callData = (abi != FALLBACK_ABI)
				? CallTransaction.Function.fromJsonInterface(abi).encode(params) : new byte[] { };

		var target = TxnUtils.asContractId(contract, spec);
		ContractCallLocalQuery query = ContractCallLocalQuery.newBuilder()
				.setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
				.setContractID(target)
				.setFunctionParameters(ByteString.copyFrom(callData))
				.setGas(gas.orElse(spec.setup().defaultCallGas()))
				.setMaxResultSize(maxResultSize.orElse(spec.setup().defaultMaxLocalCallRetBytes()))
				.build();
		return Query.newBuilder().setContractCallLocal(query).build();
	}

	@Override
	protected long costOnlyNodePayment(HapiApiSpec spec) throws Throwable {
		return spec.fees().forOp(HederaFunctionality.ContractCallLocal, scFees.getCostForQueryByIDOnly());
	}

	@Override
	protected boolean needsPayment() {
		return true;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		MoreObjects.ToStringHelper helper = super
				.toStringHelper()
				.add("contract", contract)
				.add("abi", abi)
				.add("params", Arrays.toString(params));
		gas.ifPresent(a -> helper.add("gas", a));
		maxResultSize.ifPresent(s -> helper.add("maxResultSize", s));
		return helper;
	}
}
