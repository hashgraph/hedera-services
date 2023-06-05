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

import com.hedera.node.app.service.mono.context.domain.security.PermissionedAccountsRange;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

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
        @ConfigProperty(defaultValue = "2-50") PermissionedAccountsRange getAccountDetails) {}
