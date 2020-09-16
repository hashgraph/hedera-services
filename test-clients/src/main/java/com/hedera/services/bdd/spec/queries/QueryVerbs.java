package com.hedera.services.bdd.spec.queries;

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

import com.hedera.services.bdd.spec.queries.consensus.HapiGetTopicInfo;
import com.hedera.services.bdd.spec.queries.meta.HapiGetVersionInfo;
import com.hedera.services.bdd.spec.queries.token.HapiGetTokenInfo;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.contract.HapiContractCallLocal;
import com.hedera.services.bdd.spec.queries.contract.HapiGetContractBytecode;
import com.hedera.services.bdd.spec.queries.contract.HapiGetContractInfo;
import com.hedera.services.bdd.spec.queries.contract.HapiGetContractRecords;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountBalance;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountInfo;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountRecords;
import com.hedera.services.bdd.spec.queries.file.HapiGetFileContents;
import com.hedera.services.bdd.spec.queries.file.HapiGetFileInfo;
import com.hedera.services.bdd.spec.queries.meta.HapiGetReceipt;
import com.hedera.services.bdd.spec.queries.meta.HapiGetTxnRecord;

import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.queries.contract.HapiContractCallLocal.fromDetails;

public class QueryVerbs {
	public static HapiGetReceipt getReceipt(String txn) {
		return new HapiGetReceipt(txn);
	}
	public static HapiGetReceipt getReceipt(TransactionID txnId) {
		return new HapiGetReceipt(txnId);
	}

	public static HapiGetFileInfo getFileInfo(String file) {
		return new HapiGetFileInfo(file);
	}
	public static HapiGetFileInfo getFileInfo(Supplier<String> supplier) {
		return new HapiGetFileInfo(supplier);
	}
	public static HapiGetFileContents getFileContents(String file) {
		return new HapiGetFileContents(file);
	}

	public static HapiGetAccountInfo getAccountInfo(String account) {
		return new HapiGetAccountInfo(account);
	}
	public static HapiGetAccountRecords getAccountRecords(String account) {
		return new HapiGetAccountRecords(account);
	}

	public static HapiGetTxnRecord getTxnRecord(String txn) {
		return new HapiGetTxnRecord(txn);
	}
	public static HapiGetTxnRecord getTxnRecord(TransactionID txnId) {
		return new HapiGetTxnRecord(txnId);
	}

	public static HapiGetContractInfo getContractInfo(String contract) {
		return new HapiGetContractInfo(contract);
	}
	public static HapiGetContractInfo getContractInfo(String contract, boolean idPredefined) {
		return new HapiGetContractInfo(contract, idPredefined);
	}
	public static HapiGetContractBytecode getContractBytecode(String contract) {
		return new HapiGetContractBytecode(contract);
	}
	public static HapiGetContractRecords getContractRecords(String contract) {
		return new HapiGetContractRecords(contract);
	}
	public static HapiContractCallLocal callContractLocal(String contract) {
		return new HapiContractCallLocal(contract);
	}

	public static HapiContractCallLocal contractCallLocal(String contract, String abi, Object... params) {
		return new HapiContractCallLocal(abi, contract, params);
	}
	public static HapiContractCallLocal contractCallLocalFrom(String details) {
		return fromDetails(details);
	}
	public static HapiContractCallLocal contractCallLocal(
			String contract, String abi, Function<HapiApiSpec, Object[]> fn
	) {
		return new HapiContractCallLocal(abi, contract, fn);
	}

	public static HapiGetAccountBalance getAccountBalance(String account) {
		return new HapiGetAccountBalance(account);
	}
	public static HapiGetAccountBalance getAccountBalance(Supplier<String> supplier) {
		return new HapiGetAccountBalance(supplier);
	}

	public static HapiGetTopicInfo getTopicInfo(String topic) {
		return new HapiGetTopicInfo(topic);
	}

	public static HapiGetVersionInfo getVersionInfo() {
		return new HapiGetVersionInfo();
	}

	public static HapiGetTokenInfo getTokenInfo(String token) {
		return new HapiGetTokenInfo(token);
	}
}
