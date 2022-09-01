/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.SchedulableTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

public class ScheduleUtils {
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
}
