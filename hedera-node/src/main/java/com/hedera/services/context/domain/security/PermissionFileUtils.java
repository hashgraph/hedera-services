package com.hedera.services.context.domain.security;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.exceptions.UnknownHederaFunctionality;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.TRANSACTIONGETFASTRECORD;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.UncheckedSubmit;

import java.util.EnumMap;

import static com.hedera.services.utils.MiscUtils.functionalityOfQuery;
import static com.hedera.services.utils.MiscUtils.functionOf;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;

public class PermissionFileUtils {
	private static final EnumMap<HederaFunctionality, String> permissionKeys = new EnumMap<>(HederaFunctionality.class);

	public static String permissionFileKeyForTxn(TransactionBody txn) {
		try {
			return permissionKeys.get(functionOf(txn));
		} catch (UnknownHederaFunctionality ignore) {
			return "";
		}
	}

	public static String permissionFileKeyForQuery(Query query) {
		if (query.getQueryCase() == TRANSACTIONGETFASTRECORD) {
			return "getFastTransactionRecord";
		} else {
			return functionalityOfQuery(query).map(permissionKeys::get).orElse("");
		}
	}

	static {
		/* Transactions */
		permissionKeys.put(CryptoCreate, "createAccount");
		permissionKeys.put(CryptoTransfer, "cryptoTransfer");
		permissionKeys.put(CryptoUpdate, "updateAccount");
		permissionKeys.put(CryptoDelete, "cryptoDelete");
		permissionKeys.put(CryptoAddLiveHash, "addLiveHash");
		permissionKeys.put(CryptoDeleteLiveHash, "deleteLiveHash");
		permissionKeys.put(FileCreate, "createFile");
		permissionKeys.put(FileUpdate, "updateFile");
		permissionKeys.put(FileDelete, "deleteFile");
		permissionKeys.put(FileAppend, "appendContent");
		permissionKeys.put(ContractCreate, "createContract");
		permissionKeys.put(ContractUpdate, "updateContract");
		permissionKeys.put(ContractCall, "contractCallMethod");
		permissionKeys.put(ContractDelete, "deleteContract");
		permissionKeys.put(ConsensusCreateTopic, "createTopic");
		permissionKeys.put(ConsensusUpdateTopic, "updateTopic");
		permissionKeys.put(ConsensusDeleteTopic, "deleteTopic");
		permissionKeys.put(ConsensusSubmitMessage, "submitMessage");
		permissionKeys.put(TokenCreate, "tokenCreate");
		permissionKeys.put(SystemDelete, "systemDelete");
		permissionKeys.put(SystemUndelete, "systemUndelete");
		permissionKeys.put(Freeze, "freeze");
		permissionKeys.put(UncheckedSubmit, "uncheckedSubmit");
		/* Queries */
		permissionKeys.put(ConsensusGetTopicInfo, "getTopicInfo");
		permissionKeys.put(GetBySolidityID, "getBySolidityID");
		permissionKeys.put(ContractCallLocal, "contractCallLocalMethod");
		permissionKeys.put(ContractGetInfo, "getContractInfo");
		permissionKeys.put(ContractGetBytecode, "contractGetBytecode");
		permissionKeys.put(ContractGetRecords, "getTxRecordByContractID");
		permissionKeys.put(CryptoGetAccountBalance, "cryptoGetBalance");
		permissionKeys.put(CryptoGetAccountRecords, "getAccountRecords");
		permissionKeys.put(CryptoGetInfo, "getAccountInfo");
		permissionKeys.put(CryptoGetLiveHash, "getLiveHash");
		permissionKeys.put(FileGetContents, "getFileContent");
		permissionKeys.put(FileGetInfo, "getFileInfo");
		permissionKeys.put(TransactionGetReceipt, "getTransactionReceipts");
		permissionKeys.put(TransactionGetRecord, "getTxRecordByTxID");
		permissionKeys.put(GetVersionInfo, "getVersionInfo");
	}
}
