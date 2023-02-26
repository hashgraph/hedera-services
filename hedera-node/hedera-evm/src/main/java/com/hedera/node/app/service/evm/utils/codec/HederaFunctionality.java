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

package com.hedera.node.app.service.evm.utils.codec;

@SuppressWarnings("java:S115")
public enum HederaFunctionality {
    NONE,
    CryptoTransfer,
    CryptoUpdate,
    CryptoDelete,
    CryptoAddLiveHash,
    CryptoDeleteLiveHash,
    ContractCall,
    ContractCreate,
    ContractUpdate,
    FileCreate,
    FileAppend,
    FileUpdate,
    FileDelete,
    CryptoGetAccountBalance,
    CryptoGetAccountRecords,
    CryptoGetInfo,
    ContractCallLocal,
    ContractGetInfo,
    ContractGetBytecode,
    GetBySolidityID,
    GetByKey,
    CryptoGetLiveHash,
    CryptoGetStakers,
    FileGetContents,
    FileGetInfo,
    TransactionGetRecord,
    ContractGetRecords,
    CryptoCreate,
    SystemDelete,
    SystemUndelete,
    ContractDelete,
    Freeze,
    CreateTransactionRecord,
    CryptoAccountAutoRenew,
    ContractAutoRenew,
    GetVersionInfo,
    TransactionGetReceipt,
    ConsensusCreateTopic,
    ConsensusUpdateTopic,
    ConsensusDeleteTopic,
    ConsensusGetTopicInfo,
    ConsensusSubmitMessage,
    UncheckedSubmit,
    TokenCreate,
    TokenGetInfo,
    TokenFreezeAccount,
    TokenUnfreezeAccount,
    TokenGrantKycToAccount,
    TokenRevokeKycFromAccount,
    TokenDelete,
    TokenUpdate,
    TokenMint,
    TokenBurn,
    TokenAccountWipe,
    TokenAssociateToAccount,
    TokenDissociateFromAccount,
    ScheduleCreate,
    ScheduleDelete,
    ScheduleSign,
    ScheduleGetInfo,
    TokenGetAccountNftInfos,
    TokenGetNftInfo,
    TokenGetNftInfos,
    TokenFeeScheduleUpdate,
    NetworkGetExecutionTime,
    TokenPause,
    TokenUnpause,
    CryptoApproveAllowance,
    CryptoDeleteAllowance,
    GetAccountDetails,
    EthereumTransaction,
    NodeStakeUpdate,
    UtilPrng
}
