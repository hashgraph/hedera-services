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
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.ContractGetBytecodeQuery;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

import java.util.Optional;

import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;

public class HapiGetContractBytecode extends HapiQueryOp<HapiGetContractBytecode> {
	static final Logger log = LogManager.getLogger(HapiGetContractBytecode.class);
	private final String contract;
	private Optional<byte[]> expected = Optional.empty();
	private boolean hasExpectations = false;

	public HapiGetContractBytecode(String contract) {
		this.contract = contract;
	}

	public HapiGetContractBytecode isNonEmpty() {
		hasExpectations = true;
		return this;
	}

	public HapiGetContractBytecode hasBytecode(byte[] c) {
		expected = Optional.of(c);
		return this;
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.ContractGetBytecode;
	}

	@Override
	protected HapiGetContractBytecode self() {
		return this;
	}

	@Override
	protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable {
		if (hasExpectations) {
			Assert.assertTrue("Empty bytecode!",
					!response.getContractGetBytecodeResponse().getBytecode().isEmpty());
		}
		if (expected.isPresent()) {
			Assert.assertArrayEquals(
					"Wrong bytecode!",
					expected.get(),
					response.getContractGetBytecodeResponse().getBytecode().toByteArray());
		}
	}

	@Override
	protected void submitWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getContractBytecodeQuery(spec, payment, false);
		response = spec.clients().getScSvcStub(targetNodeFor(spec), useTls).contractGetBytecode(query);
	}

	@Override
	protected long lookupCostWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getContractBytecodeQuery(spec, payment, true);
		Response response = spec.clients().getScSvcStub(targetNodeFor(spec), useTls).contractGetBytecode(query);
		return costFrom(response);
	}

	private Query getContractBytecodeQuery(HapiApiSpec spec, Transaction payment, boolean costOnly) {
		var target = TxnUtils.asContractId(contract, spec);
		ContractGetBytecodeQuery query = ContractGetBytecodeQuery.newBuilder()
				.setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
				.setContractID(target)
				.build();
		return Query.newBuilder().setContractGetBytecode(query).build();
	}

	@Override
	protected long costOnlyNodePayment(HapiApiSpec spec) throws Throwable {
		return spec.fees().forOp(HederaFunctionality.ContractGetBytecode, scFees.getCostForQueryByIDOnly());
	}

	@Override
	protected boolean needsPayment() {
		return true;
	}

	@Override
	protected MoreObjects.ToStringHelper toStringHelper() {
		return super.toStringHelper()
				.add("contract", contract);
	}
}
