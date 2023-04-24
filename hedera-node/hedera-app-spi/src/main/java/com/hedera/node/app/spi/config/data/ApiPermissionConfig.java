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

package com.hedera.node.app.spi.config.data;

/**
 * This class contains the properties that are part of the api-permission properties
 */
public record ApiPermissionConfig(
        String createAccount,
        String cryptoTransfer,
        String updateAccount,
        String cryptoGetBalance,
        String getAccountInfo,
        String cryptoDelete,
        String getAccountRecords,
        String getTxRecordByTxID,
        String getTransactionReceipts,
        String approveAllowances,
        String utilPrng,
        String createFile,
        String updateFile,
        String deleteFile,
        String appendContent,
        String getFileContent,
        String getFileInfo,
        String createContract,
        String updateContract,
        String contractCallMethod,
        String getContractInfo,
        String contractCallLocalMethod,
        String contractGetBytecode,
        String getTxRecordByContractID,
        String deleteContract,
        String createTopic,
        String updateTopic,
        String deleteTopic,
        String submitMessage,
        String getTopicInfo,
        String ethereumTransaction,
        String scheduleCreate,
        String scheduleSign,
        String scheduleDelete,
        String scheduleGetInfo,
        String tokenCreate,
        String tokenFreezeAccount,
        String tokenUnfreezeAccount,
        String tokenGrantKycToAccount,
        String tokenRevokeKycFromAccount,
        String tokenDelete,
        String tokenMint,
        String tokenBurn,
        String tokenAccountWipe,
        String tokenUpdate,
        String tokenGetInfo,
        String tokenGetAccountNftInfos,
        String tokenAssociateToAccount,
        String tokenDissociateFromAccount,
        String tokenGetNftInfo,
        String tokenFeeScheduleUpdate,
        String tokenPause,
        String tokenUnpause,
        String tokenGetNftInfos,
        String getVersionInfo,
        String systemDelete,
        String systemUndelete,
        String freeze,
        String uncheckedSubmit,
        String networkGetExecutionTime) {}
