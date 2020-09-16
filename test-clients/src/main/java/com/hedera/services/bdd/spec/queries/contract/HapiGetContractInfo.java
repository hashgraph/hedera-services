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
import com.google.common.io.*;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hederahashgraph.api.proto.java.*;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.assertions.ContractInfoAsserts;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static com.hedera.services.bdd.spec.HapiApiSpec.ensureDir;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.rethrowSummaryError;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerCostHeader;
import static com.hedera.services.bdd.spec.queries.QueryUtils.answerHeader;
import static com.hederahashgraph.api.proto.java.ContractGetInfoResponse.ContractInfo;

import com.hedera.services.bdd.spec.assertions.ErroringAsserts;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;

public class HapiGetContractInfo extends HapiQueryOp<HapiGetContractInfo> {
	private static final Logger log = LogManager.getLogger(HapiGetContractInfo.class);

	private final String contract;
	private boolean getPredefinedId = false;
	private Optional<String> contractInfoPath = Optional.empty();
	private Optional<String> validateDirPath = Optional.empty();
	private Optional<String> registryEntry = Optional.empty();

	private Optional<ContractInfoAsserts> expectations = Optional.empty();

	public HapiGetContractInfo(String contract) {
		this.contract = contract;
	}

	public HapiGetContractInfo(String contract, boolean idPredefined) {
		this.contract = contract;
		getPredefinedId = idPredefined;
	}

	public HapiGetContractInfo has(ContractInfoAsserts provider) {
		expectations = Optional.of(provider);
		return this;
	}

	public HapiGetContractInfo hasExpectedInfo() {
		expectations = Optional.of(ContractInfoAsserts.contractWith().knownInfoFor(contract));
		return this;
	}

	public HapiGetContractInfo savingTo(String dirPath) {
		contractInfoPath = Optional.of(dirPath);
		return this;
	}

	public HapiGetContractInfo saveToRegistry(String registryEntry) {
		this.registryEntry = Optional.of(registryEntry);
		return this;
	}

	public HapiGetContractInfo checkingAgainst(String dirPath) {
		validateDirPath = Optional.of(dirPath);
		return this;
	}

	@Override
	public HederaFunctionality type() {
		return HederaFunctionality.ContractGetInfo;
	}

	@Override
	protected HapiGetContractInfo self() {
		return this;
	}

	@Override
	protected void assertExpectationsGiven(HapiApiSpec spec) throws Throwable {
		if (expectations.isPresent()) {
			ContractInfo actualInfo = response.getContractGetInfo().getContractInfo();
			ErroringAsserts<ContractInfo> asserts = expectations.get().assertsFor(spec);
			List<Throwable> errors = asserts.errorsIn(actualInfo);
			rethrowSummaryError(log, "Bad contract info!", errors);
		}
	}

	@Override
	protected void submitWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getContractInfoQuery(spec, payment, false);
		response = spec.clients().getScSvcStub(targetNodeFor(spec), useTls).getContractInfo(query);
		ContractInfo contractInfo = response.getContractGetInfo().getContractInfo();
		if (verboseLoggingOn) {
			log.info("Info: " + contractInfo);
		}
		if (contractInfoPath.isPresent()) {
			saveContractInfo(spec, contractInfo);
		}
		if (validateDirPath.isPresent()) {
			validateAgainst(spec, contractInfo);
		}
		if (registryEntry.isPresent()) {
			spec.registry().saveContractInfo(registryEntry.get(), contractInfo);
		}
	}

	@Override
	protected long lookupCostWith(HapiApiSpec spec, Transaction payment) throws Throwable {
		Query query = getContractInfoQuery(spec, payment, true);
		Response response = spec.clients().getScSvcStub(targetNodeFor(spec), useTls).getContractInfo(query);
		return costFrom(response);
	}

	private String specScopedDir(HapiApiSpec spec, Optional<String> prefix) {
		return prefix.map(d -> d + "/" + spec.getName()).get();
	}

	private void saveContractInfo(HapiApiSpec spec, ContractInfo contractInfo) {
		String specSnapshotDir = specScopedDir(spec, contractInfoPath);
		ensureDir(specSnapshotDir);
		String snapshotDir = specSnapshotDir + "/" + contract;
		ensureDir(snapshotDir);

		try {
			File contractIdFile = new File(snapshotDir + "/contractId.txt");
			ByteSink byteSinkId = Files.asByteSink(contractIdFile);
			byteSinkId.write(contractInfo.getContractID().toByteArray());

			File contractInfoFile = new File(snapshotDir + "/contractInfo.bin");
			ByteSink byteSinkInfo = Files.asByteSink(contractInfoFile);
			byteSinkInfo.write(contractInfo.toByteArray());

			if (verboseLoggingOn) {
				log.info("Saved contractInfo of " + contractInfo.getContractID() + " to " + snapshotDir);
			}
		} catch (Exception e) {
			log.error("Couldn't save contractInfo of " + contractInfo.getContractID(), e);
		}
	}

	private void validateAgainst(HapiApiSpec spec, ContractInfo contractInfo) {
		String specExpectationsDir = specScopedDir(spec, validateDirPath);
		try {
			String expectationsDir = specExpectationsDir + "/" + contract;

			File contractInfoFile = new File(expectationsDir + "/contractInfo.bin");
			ByteSource byteSourceInfo = Files.asByteSource(contractInfoFile);
			ContractInfo savedContractInfo = ContractInfo.parseFrom(byteSourceInfo.read());
			if (verboseLoggingOn) {
				log.info("Info: " + contractInfo);
			}
			Assert.assertEquals(contractInfo.getAccountID().getAccountNum(),
					savedContractInfo.getAccountID().getAccountNum());
			Assert.assertEquals(contractInfo.getStorage(), savedContractInfo.getStorage());
			Assert.assertEquals(contractInfo.getBalance(), savedContractInfo.getBalance());
		} catch (Exception e) {
			log.error("Something amiss with the expected records...", e);
			Assert.fail("Impossible to meet expectations (on records)!");
		}
	}

	private ContractID readContractID(HapiApiSpec spec) {
		String specExpectationsDir = specScopedDir(spec, validateDirPath);
		try {
			String expectationsDir = specExpectationsDir + "/" + contract;
			File contractIdFile = new File(expectationsDir + "/contractId.txt");
			ByteSource contractIdByteSource = Files.asByteSource(contractIdFile);
			ContractID contractID = ContractID.parseFrom(contractIdByteSource.read());
			return contractID;
		} catch (Exception e) {
			log.error("Something wrong with the expected ContractInfo file", e);
			return null;
		}
	}

	private Query getContractInfoQuery(HapiApiSpec spec, Transaction payment, boolean costOnly) {
		ContractGetInfoQuery contractGetInfo;
		if (getPredefinedId) {
			var contractID = readContractID(spec);
			if (contractID != null) {
				contractGetInfo = ContractGetInfoQuery.newBuilder()
						.setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
						.setContractID(contractID)
						.build();
			} else {
				log.error("Couldn't read contractID from saved file");
				return null;
			}
		} else {
			var target = TxnUtils.asContractId(contract, spec);
			contractGetInfo = ContractGetInfoQuery.newBuilder()
					.setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
					.setContractID(target)
					.build();
		}
		return Query.newBuilder().setContractGetInfo(contractGetInfo).build();
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
