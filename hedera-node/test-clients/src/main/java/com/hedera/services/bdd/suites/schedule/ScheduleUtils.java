/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.schedule;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ScheduleUtils {
    public static final String SENDER_TXN = "senderTxn";
    public static final String STAKING_FEES_NODE_REWARD_PERCENTAGE = "staking.fees.nodeRewardPercentage";
    public static final String STAKING_FEES_STAKING_REWARD_PERCENTAGE = "staking.fees.stakingRewardPercentage";
    public static final String SCHEDULING_MAX_TXN_PER_SECOND = "scheduling.maxTxnPerSecond";
    public static final String SCHEDULING_LONG_TERM_ENABLED = "scheduling.longTermEnabled";
    public static final String LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS = "ledger.schedule.txExpiryTimeSecs";
    public static final String SCHEDULING_WHITELIST = "scheduling.whitelist";
    /**
     * Whitelist containing all of the non-query type transactions so we don't hit whitelist failures
     * everywhere.  Recommended for most specs that override whitelist.
     */
    public static final String FULL_WHITELIST =
            """
            ConsensusCreateTopic,ConsensusDeleteTopic,ConsensusSubmitMessage,ConsensusUpdateTopic,\
            ContractAutoRenew,ContractCall,ContractCallLocal,ContractCreate,ContractDelete,\
            ContractUpdate,CreateTransactionRecord,CryptoAccountAutoRenew,CryptoAddLiveHash,\
            CryptoApproveAllowance,CryptoCreate,CryptoDelete,CryptoDeleteAllowance,CryptoDeleteLiveHash,\
            CryptoTransfer,CryptoUpdate,EthereumTransaction,FileAppend,FileCreate,FileDelete,FileUpdate,\
            Freeze,SystemDelete,SystemUndelete,TokenAccountWipe,TokenAssociateToAccount,TokenBurn,\
            TokenCreate,TokenDelete,TokenDissociateFromAccount,TokenFeeScheduleUpdate,TokenFreezeAccount,\
            TokenGrantKycToAccount,TokenMint,TokenPause,TokenRevokeKycFromAccount,TokenUnfreezeAccount,\
            TokenUnpause,TokenUpdate,UtilPrng""";
    /**
     * A very small whitelist containing just the transactions needed for SecheduleExecutionSpecs because
     * that suite has to override the whitelist on every single spec due to some sort of ordering issue.
     */
    public static final String WHITELIST_MINIMUM =
            "ConsensusSubmitMessage,ContractCall,CryptoCreate,CryptoTransfer,FileUpdate,SystemDelete,TokenBurn,TokenMint,Freeze";
    /**
     * A whitelist guaranteed to contain every transaction type possible.  Useful for specs that need to test scheduling
     * a transaction that shouldn't work (e.g. a query).
     */
    public static final String WHITELIST_ALL = getWhitelistAll();

    public static SchedulableTransactionBody fromOrdinary(TransactionBody txn) {
        var scheduleBuilder = SchedulableTransactionBody.newBuilder();

        scheduleBuilder.setTransactionFee(txn.getTransactionFee());
        scheduleBuilder.setMemo(txn.getMemo());

        if (txn.hasContractCall()) {
            scheduleBuilder.setContractCall(txn.getContractCall());
        } else if (txn.hasContractCreateInstance()) {
            scheduleBuilder.setContractCreateInstance(txn.getContractCreateInstance());
        } else if (txn.hasContractUpdateInstance()) {
            scheduleBuilder.setContractUpdateInstance(txn.getContractUpdateInstance());
        } else if (txn.hasContractDeleteInstance()) {
            scheduleBuilder.setContractDeleteInstance(txn.getContractDeleteInstance());
        } else if (txn.hasCryptoCreateAccount()) {
            scheduleBuilder.setCryptoCreateAccount(txn.getCryptoCreateAccount());
        } else if (txn.hasCryptoDelete()) {
            scheduleBuilder.setCryptoDelete(txn.getCryptoDelete());
        } else if (txn.hasCryptoTransfer()) {
            scheduleBuilder.setCryptoTransfer(txn.getCryptoTransfer());
        } else if (txn.hasCryptoUpdateAccount()) {
            scheduleBuilder.setCryptoUpdateAccount(txn.getCryptoUpdateAccount());
        } else if (txn.hasFileAppend()) {
            scheduleBuilder.setFileAppend(txn.getFileAppend());
        } else if (txn.hasFileCreate()) {
            scheduleBuilder.setFileCreate(txn.getFileCreate());
        } else if (txn.hasFileDelete()) {
            scheduleBuilder.setFileDelete(txn.getFileDelete());
        } else if (txn.hasFileUpdate()) {
            scheduleBuilder.setFileUpdate(txn.getFileUpdate());
        } else if (txn.hasSystemDelete()) {
            scheduleBuilder.setSystemDelete(txn.getSystemDelete());
        } else if (txn.hasSystemUndelete()) {
            scheduleBuilder.setSystemUndelete(txn.getSystemUndelete());
        } else if (txn.hasFreeze()) {
            scheduleBuilder.setFreeze(txn.getFreeze());
        } else if (txn.hasConsensusCreateTopic()) {
            scheduleBuilder.setConsensusCreateTopic(txn.getConsensusCreateTopic());
        } else if (txn.hasConsensusUpdateTopic()) {
            scheduleBuilder.setConsensusUpdateTopic(txn.getConsensusUpdateTopic());
        } else if (txn.hasConsensusDeleteTopic()) {
            scheduleBuilder.setConsensusDeleteTopic(txn.getConsensusDeleteTopic());
        } else if (txn.hasConsensusSubmitMessage()) {
            scheduleBuilder.setConsensusSubmitMessage(txn.getConsensusSubmitMessage());
        } else if (txn.hasTokenCreation()) {
            scheduleBuilder.setTokenCreation(txn.getTokenCreation());
        } else if (txn.hasTokenFreeze()) {
            scheduleBuilder.setTokenFreeze(txn.getTokenFreeze());
        } else if (txn.hasTokenUnfreeze()) {
            scheduleBuilder.setTokenUnfreeze(txn.getTokenUnfreeze());
        } else if (txn.hasTokenGrantKyc()) {
            scheduleBuilder.setTokenGrantKyc(txn.getTokenGrantKyc());
        } else if (txn.hasTokenRevokeKyc()) {
            scheduleBuilder.setTokenRevokeKyc(txn.getTokenRevokeKyc());
        } else if (txn.hasTokenDeletion()) {
            scheduleBuilder.setTokenDeletion(txn.getTokenDeletion());
        } else if (txn.hasTokenUpdate()) {
            scheduleBuilder.setTokenUpdate(txn.getTokenUpdate());
        } else if (txn.hasTokenMint()) {
            scheduleBuilder.setTokenMint(txn.getTokenMint());
        } else if (txn.hasTokenBurn()) {
            scheduleBuilder.setTokenBurn(txn.getTokenBurn());
        } else if (txn.hasTokenWipe()) {
            scheduleBuilder.setTokenWipe(txn.getTokenWipe());
        } else if (txn.hasTokenAssociate()) {
            scheduleBuilder.setTokenAssociate(txn.getTokenAssociate());
        } else if (txn.hasTokenDissociate()) {
            scheduleBuilder.setTokenDissociate(txn.getTokenDissociate());
        } else if (txn.hasScheduleDelete()) {
            scheduleBuilder.setScheduleDelete(txn.getScheduleDelete());
        } else if (txn.hasCryptoApproveAllowance()) {
            scheduleBuilder.setCryptoApproveAllowance(txn.getCryptoApproveAllowance());
        }

        return scheduleBuilder.build();
    }

    private static String getWhitelistAll() {
        final List<String> whitelistNames = new LinkedList<>();
        for (final HederaFunctionality enumValue : HederaFunctionality.values()) {
            if (enumValue != HederaFunctionality.NONE) whitelistNames.add(enumValue.protoName());
        }
        Collections.sort(whitelistNames); // make things easier to read
        final String whitelistAll = String.join(",", whitelistNames);
        return whitelistAll;
    }
}
