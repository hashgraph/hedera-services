/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.config.data;

import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_CREATE_TOPIC;
import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_DELETE_TOPIC;
import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_GET_TOPIC_INFO;
import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_SUBMIT_MESSAGE;
import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_UPDATE_TOPIC;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL_LOCAL;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_DELETE;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_GET_BYTECODE;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_GET_INFO;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_GET_RECORDS;
import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_UPDATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_APPROVE_ALLOWANCE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_DELETE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_DELETE_ALLOWANCE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_GET_ACCOUNT_BALANCE;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_GET_ACCOUNT_RECORDS;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_GET_INFO;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_UPDATE;
import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.HederaFunctionality.FILE_APPEND;
import static com.hedera.hapi.node.base.HederaFunctionality.FILE_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.FILE_DELETE;
import static com.hedera.hapi.node.base.HederaFunctionality.FILE_GET_CONTENTS;
import static com.hedera.hapi.node.base.HederaFunctionality.FILE_GET_INFO;
import static com.hedera.hapi.node.base.HederaFunctionality.FILE_UPDATE;
import static com.hedera.hapi.node.base.HederaFunctionality.FREEZE;
import static com.hedera.hapi.node.base.HederaFunctionality.GET_ACCOUNT_DETAILS;
import static com.hedera.hapi.node.base.HederaFunctionality.GET_VERSION_INFO;
import static com.hedera.hapi.node.base.HederaFunctionality.NETWORK_GET_EXECUTION_TIME;
import static com.hedera.hapi.node.base.HederaFunctionality.SCHEDULE_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.SCHEDULE_DELETE;
import static com.hedera.hapi.node.base.HederaFunctionality.SCHEDULE_GET_INFO;
import static com.hedera.hapi.node.base.HederaFunctionality.SCHEDULE_SIGN;
import static com.hedera.hapi.node.base.HederaFunctionality.SYSTEM_DELETE;
import static com.hedera.hapi.node.base.HederaFunctionality.SYSTEM_UNDELETE;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_ACCOUNT_WIPE;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_ASSOCIATE_TO_ACCOUNT;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_BURN;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_CREATE;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_DELETE;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_DISSOCIATE_FROM_ACCOUNT;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_FEE_SCHEDULE_UPDATE;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_FREEZE_ACCOUNT;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_GET_ACCOUNT_NFT_INFOS;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_GET_INFO;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_GET_NFT_INFO;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_GET_NFT_INFOS;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_GRANT_KYC_TO_ACCOUNT;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_MINT;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_PAUSE;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_REVOKE_KYC_FROM_ACCOUNT;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_UNFREEZE_ACCOUNT;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_UNPAUSE;
import static com.hedera.hapi.node.base.HederaFunctionality.TOKEN_UPDATE;
import static com.hedera.hapi.node.base.HederaFunctionality.TRANSACTION_GET_RECEIPT;
import static com.hedera.hapi.node.base.HederaFunctionality.TRANSACTION_GET_RECORD;
import static com.hedera.hapi.node.base.HederaFunctionality.UTIL_PRNG;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.service.mono.context.domain.security.PermissionedAccountsRange;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumMap;
import java.util.Objects;
import java.util.function.Function;

@ConfigData
public record ApiPermissionConfig(
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange createAccount,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange cryptoTransfer,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange updateAccount,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange cryptoGetBalance,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange getAccountInfo,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange cryptoDelete,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange getAccountRecords,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange getTxRecordByTxID,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange getTransactionReceipts,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange approveAllowances,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange deleteAllowances,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange utilPrng,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange createFile,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange updateFile,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange deleteFile,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange appendContent,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange getFileContent,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange getFileInfo,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange createContract,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange updateContract,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange contractCallMethod,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange getContractInfo,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange contractCallLocalMethod,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange contractGetBytecode,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange getTxRecordByContractID,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange deleteContract,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange createTopic,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange updateTopic,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange deleteTopic,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange submitMessage,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange getTopicInfo,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange ethereumTransaction,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange scheduleCreate,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange scheduleSign,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange scheduleDelete,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange scheduleGetInfo,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenCreate,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenFreezeAccount,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenUnfreezeAccount,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenGrantKycToAccount,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenRevokeKycFromAccount,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenDelete,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenMint,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenBurn,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenAccountWipe,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenUpdate,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenGetInfo,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenAssociateToAccount,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenDissociateFromAccount,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenGetNftInfo,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenGetNftInfos,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenGetAccountNftInfos,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenFeeScheduleUpdate,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenPause,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange tokenUnpause,
        @ConfigProperty(defaultValue = "0-*") PermissionedAccountsRange getVersionInfo,
        @ConfigProperty(defaultValue = "2-50") PermissionedAccountsRange networkGetExecutionTime,
        @ConfigProperty(defaultValue = "2-59") PermissionedAccountsRange systemDelete,
        @ConfigProperty(defaultValue = "2-60") PermissionedAccountsRange systemUndelete,
        @ConfigProperty(defaultValue = "2-58") PermissionedAccountsRange freeze,
        @ConfigProperty(defaultValue = "2-50") PermissionedAccountsRange getAccountDetails) {

    private static final EnumMap<HederaFunctionality, Function<ApiPermissionConfig, PermissionedAccountsRange>>
            permissionKeys = new EnumMap<>(HederaFunctionality.class);

    static {
        /* Transactions */
        permissionKeys.put(CRYPTO_CREATE, c -> c.createAccount);
        permissionKeys.put(CRYPTO_TRANSFER, c -> c.cryptoTransfer);
        permissionKeys.put(CRYPTO_UPDATE, c -> c.updateAccount);
        permissionKeys.put(CRYPTO_DELETE, c -> c.cryptoDelete);
        permissionKeys.put(CRYPTO_APPROVE_ALLOWANCE, c -> c.approveAllowances);
        permissionKeys.put(CRYPTO_DELETE_ALLOWANCE, c -> c.deleteAllowances);
        permissionKeys.put(FILE_CREATE, c -> c.createFile);
        permissionKeys.put(FILE_UPDATE, c -> c.updateFile);
        permissionKeys.put(FILE_DELETE, c -> c.deleteFile);
        permissionKeys.put(FILE_APPEND, c -> c.appendContent);
        permissionKeys.put(CONTRACT_CREATE, c -> c.createContract);
        permissionKeys.put(CONTRACT_UPDATE, c -> c.updateContract);
        permissionKeys.put(CONTRACT_CALL, c -> c.contractCallMethod);
        permissionKeys.put(CONTRACT_DELETE, c -> c.deleteContract);
        permissionKeys.put(ETHEREUM_TRANSACTION, c -> c.ethereumTransaction);
        permissionKeys.put(CONSENSUS_CREATE_TOPIC, c -> c.createTopic);
        permissionKeys.put(CONSENSUS_UPDATE_TOPIC, c -> c.updateTopic);
        permissionKeys.put(CONSENSUS_DELETE_TOPIC, c -> c.deleteTopic);
        permissionKeys.put(CONSENSUS_SUBMIT_MESSAGE, c -> c.submitMessage);
        permissionKeys.put(TOKEN_CREATE, c -> c.tokenCreate);
        permissionKeys.put(TOKEN_FREEZE_ACCOUNT, c -> c.tokenFreezeAccount);
        permissionKeys.put(TOKEN_UNFREEZE_ACCOUNT, c -> c.tokenUnfreezeAccount);
        permissionKeys.put(TOKEN_GRANT_KYC_TO_ACCOUNT, c -> c.tokenGrantKycToAccount);
        permissionKeys.put(TOKEN_REVOKE_KYC_FROM_ACCOUNT, c -> c.tokenRevokeKycFromAccount);
        permissionKeys.put(TOKEN_DELETE, c -> c.tokenDelete);
        permissionKeys.put(TOKEN_MINT, c -> c.tokenMint);
        permissionKeys.put(TOKEN_BURN, c -> c.tokenBurn);
        permissionKeys.put(TOKEN_ACCOUNT_WIPE, c -> c.tokenAccountWipe);
        permissionKeys.put(TOKEN_UPDATE, c -> c.tokenUpdate);
        permissionKeys.put(TOKEN_ASSOCIATE_TO_ACCOUNT, c -> c.tokenAssociateToAccount);
        permissionKeys.put(TOKEN_DISSOCIATE_FROM_ACCOUNT, c -> c.tokenDissociateFromAccount);
        permissionKeys.put(TOKEN_PAUSE, c -> c.tokenPause);
        permissionKeys.put(TOKEN_UNPAUSE, c -> c.tokenUnpause);
        permissionKeys.put(SYSTEM_DELETE, c -> c.systemDelete);
        permissionKeys.put(SYSTEM_UNDELETE, c -> c.systemUndelete);
        permissionKeys.put(FREEZE, c -> c.freeze);
        permissionKeys.put(SCHEDULE_CREATE, c -> c.scheduleCreate);
        permissionKeys.put(SCHEDULE_DELETE, c -> c.scheduleDelete);
        permissionKeys.put(SCHEDULE_SIGN, c -> c.scheduleSign);
        /* Queries */
        permissionKeys.put(CONSENSUS_GET_TOPIC_INFO, c -> c.getTopicInfo);
        permissionKeys.put(CONTRACT_CALL_LOCAL, c -> c.contractCallLocalMethod);
        permissionKeys.put(CONTRACT_GET_INFO, c -> c.getContractInfo);
        permissionKeys.put(CONTRACT_GET_BYTECODE, c -> c.contractGetBytecode);
        permissionKeys.put(CONTRACT_GET_RECORDS, c -> c.getTxRecordByContractID);
        permissionKeys.put(CRYPTO_GET_ACCOUNT_BALANCE, c -> c.cryptoGetBalance);
        permissionKeys.put(CRYPTO_GET_ACCOUNT_RECORDS, c -> c.getAccountRecords);
        permissionKeys.put(CRYPTO_GET_INFO, c -> c.getAccountInfo);
        permissionKeys.put(FILE_GET_CONTENTS, c -> c.getFileContent);
        permissionKeys.put(FILE_GET_INFO, c -> c.getFileInfo);
        permissionKeys.put(TRANSACTION_GET_RECEIPT, c -> c.getTransactionReceipts);
        permissionKeys.put(TRANSACTION_GET_RECORD, c -> c.getTxRecordByTxID);
        permissionKeys.put(GET_VERSION_INFO, c -> c.getVersionInfo);
        permissionKeys.put(NETWORK_GET_EXECUTION_TIME, c -> c.networkGetExecutionTime);
        permissionKeys.put(GET_ACCOUNT_DETAILS, c -> c.getAccountDetails);
        permissionKeys.put(TOKEN_GET_INFO, c -> c.tokenGetInfo);
        permissionKeys.put(SCHEDULE_GET_INFO, c -> c.scheduleGetInfo);
        permissionKeys.put(TOKEN_GET_NFT_INFO, c -> c.tokenGetNftInfo);
        permissionKeys.put(TOKEN_GET_NFT_INFOS, c -> c.tokenGetNftInfos);
        permissionKeys.put(TOKEN_GET_ACCOUNT_NFT_INFOS, c -> c.tokenGetAccountNftInfos);
        permissionKeys.put(TOKEN_FEE_SCHEDULE_UPDATE, c -> c.tokenFeeScheduleUpdate);
        permissionKeys.put(UTIL_PRNG, c -> c.utilPrng);
    }

    public PermissionedAccountsRange getPermission(@NonNull HederaFunctionality functionality) {
        Objects.requireNonNull(functionality, "functionality cannot be null");
        var function = permissionKeys.get(functionality);
        if (function == null) {
            throw new IllegalArgumentException("Can not get permission for functionality " + functionality);
        }
        return function.apply(this);
    }
}
