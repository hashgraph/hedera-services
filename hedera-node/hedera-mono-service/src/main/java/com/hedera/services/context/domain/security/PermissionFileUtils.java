/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.context.domain.security;

import static com.hedera.services.utils.MiscUtils.functionOf;
import static com.hedera.services.utils.MiscUtils.functionalityOfQuery;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetBytecode;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoAddLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetLiveHash;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetContents;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetAccountDetails;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetBySolidityID;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetVersionInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NetworkGetExecutionTime;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemUndelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetAccountNftInfos;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetNftInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetNftInfos;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetReceipt;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetRecord;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UncheckedSubmit;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UtilPrng;
import static com.hederahashgraph.api.proto.java.Query.QueryCase.TRANSACTIONGETFASTRECORD;

import com.hedera.services.exceptions.UnknownHederaFunctionality;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class PermissionFileUtils {
    private static final EnumMap<HederaFunctionality, String> permissionKeys =
            new EnumMap<>(HederaFunctionality.class);
    static final Map<String, HederaFunctionality> legacyKeys;

    private PermissionFileUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static String permissionFileKeyForTxn(final TransactionBody txn) {
        try {
            return permissionKeys.get(functionOf(txn));
        } catch (UnknownHederaFunctionality ignore) {
            return "";
        }
    }

    public static String permissionFileKeyForQuery(final Query query) {
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
        permissionKeys.put(CryptoApproveAllowance, "approveAllowances");
        permissionKeys.put(CryptoDeleteAllowance, "deleteAllowances");
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
        permissionKeys.put(EthereumTransaction, "ethereumTransaction");
        permissionKeys.put(ConsensusCreateTopic, "createTopic");
        permissionKeys.put(ConsensusUpdateTopic, "updateTopic");
        permissionKeys.put(ConsensusDeleteTopic, "deleteTopic");
        permissionKeys.put(ConsensusSubmitMessage, "submitMessage");
        permissionKeys.put(TokenCreate, "tokenCreate");
        permissionKeys.put(TokenFreezeAccount, "tokenFreezeAccount");
        permissionKeys.put(TokenUnfreezeAccount, "tokenUnfreezeAccount");
        permissionKeys.put(TokenGrantKycToAccount, "tokenGrantKycToAccount");
        permissionKeys.put(TokenRevokeKycFromAccount, "tokenRevokeKycFromAccount");
        permissionKeys.put(TokenDelete, "tokenDelete");
        permissionKeys.put(TokenMint, "tokenMint");
        permissionKeys.put(TokenBurn, "tokenBurn");
        permissionKeys.put(TokenAccountWipe, "tokenAccountWipe");
        permissionKeys.put(TokenUpdate, "tokenUpdate");
        permissionKeys.put(TokenAssociateToAccount, "tokenAssociateToAccount");
        permissionKeys.put(TokenDissociateFromAccount, "tokenDissociateFromAccount");
        permissionKeys.put(TokenPause, "tokenPause");
        permissionKeys.put(TokenUnpause, "tokenUnpause");
        permissionKeys.put(SystemDelete, "systemDelete");
        permissionKeys.put(SystemUndelete, "systemUndelete");
        permissionKeys.put(Freeze, "freeze");
        permissionKeys.put(UncheckedSubmit, "uncheckedSubmit");
        permissionKeys.put(ScheduleCreate, "scheduleCreate");
        permissionKeys.put(ScheduleDelete, "scheduleDelete");
        permissionKeys.put(ScheduleSign, "scheduleSign");
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
        permissionKeys.put(NetworkGetExecutionTime, "networkGetExecutionTime");
        permissionKeys.put(GetAccountDetails, "getAccountDetails");
        permissionKeys.put(TokenGetInfo, "tokenGetInfo");
        permissionKeys.put(ScheduleGetInfo, "scheduleGetInfo");
        permissionKeys.put(TokenGetNftInfo, "tokenGetNftInfo");
        permissionKeys.put(TokenGetNftInfos, "tokenGetNftInfos");
        permissionKeys.put(TokenGetAccountNftInfos, "tokenGetAccountNftInfos");
        permissionKeys.put(TokenFeeScheduleUpdate, "tokenFeeScheduleUpdate");
        permissionKeys.put(UtilPrng, "utilPrng");

        legacyKeys =
                permissionKeys.entrySet().stream()
                        .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
    }
}
