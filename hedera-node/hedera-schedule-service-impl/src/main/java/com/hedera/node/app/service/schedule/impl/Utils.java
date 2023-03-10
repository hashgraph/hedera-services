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

package com.hedera.node.app.service.schedule.impl;

import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.scheduled.SchedulableTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.UnknownHederaFunctionality;

public class Utils {
    public static TransactionBody asOrdinary(
            final SchedulableTransactionBody scheduledTxn, final TransactionID scheduledTxnTransactionId) {
        final var ordinary = TransactionBody.newBuilder();
        ordinary.transactionFee(scheduledTxn.transactionFee())
                .memo(scheduledTxn.memo())
                .transactionID(scheduledTxnTransactionId.copyBuilder().scheduled(true));

        switch (scheduledTxn.data().kind()) {
            case CONSENSUS_CREATE_TOPIC -> ordinary.consensusCreateTopic(
                    scheduledTxn.consensusCreateTopic().orElseThrow());
            case CONSENSUS_UPDATE_TOPIC -> ordinary.consensusUpdateTopic(
                    scheduledTxn.consensusUpdateTopic().orElseThrow());
            case CONSENSUS_DELETE_TOPIC -> ordinary.consensusDeleteTopic(
                    scheduledTxn.consensusDeleteTopic().orElseThrow());
            case CONSENSUS_SUBMIT_MESSAGE -> ordinary.consensusSubmitMessage(
                    scheduledTxn.consensusSubmitMessage().orElseThrow());
            case CRYPTO_CREATE_ACCOUNT -> ordinary.cryptoCreateAccount(
                    scheduledTxn.cryptoCreateAccount().orElseThrow());
            case CRYPTO_UPDATE_ACCOUNT -> ordinary.cryptoUpdateAccount(
                    scheduledTxn.cryptoUpdateAccount().orElseThrow());
            case CRYPTO_TRANSFER -> ordinary.cryptoTransfer(
                    scheduledTxn.cryptoTransfer().orElseThrow());
            case CRYPTO_DELETE -> ordinary.cryptoDelete(
                    scheduledTxn.cryptoDelete().orElseThrow());
            case FILE_CREATE -> ordinary.fileCreate(scheduledTxn.fileCreate().orElseThrow());
            case FILE_APPEND -> ordinary.fileAppend(scheduledTxn.fileAppend().orElseThrow());
            case FILE_UPDATE -> ordinary.fileUpdate(scheduledTxn.fileUpdate().orElseThrow());
            case FILE_DELETE -> ordinary.fileDelete(scheduledTxn.fileDelete().orElseThrow());
            case CONTRACT_CREATE_INSTANCE -> ordinary.contractCreateInstance(
                    scheduledTxn.contractCreateInstance().orElseThrow());
            case CONTRACT_UPDATE_INSTANCE -> ordinary.contractUpdateInstance(
                    scheduledTxn.contractUpdateInstance().orElseThrow());
            case CONTRACT_CALL -> ordinary.contractCall(
                    scheduledTxn.contractCall().orElseThrow());
            case CONTRACT_DELETE_INSTANCE -> ordinary.contractDeleteInstance(
                    scheduledTxn.contractDeleteInstance().orElseThrow());
            case SYSTEM_DELETE -> ordinary.systemDelete(
                    scheduledTxn.systemDelete().orElseThrow());
            case SYSTEM_UNDELETE -> ordinary.systemUndelete(
                    scheduledTxn.systemUndelete().orElseThrow());
            case FREEZE -> ordinary.freeze(scheduledTxn.freeze().orElseThrow());
            case TOKEN_CREATION -> ordinary.tokenCreation(
                    scheduledTxn.tokenCreation().orElseThrow());
            case TOKEN_FREEZE -> ordinary.tokenFreeze(scheduledTxn.tokenFreeze().orElseThrow());
            case TOKEN_UNFREEZE -> ordinary.tokenUnfreeze(
                    scheduledTxn.tokenUnfreeze().orElseThrow());
            case TOKEN_GRANT_KYC -> ordinary.tokenGrantKyc(
                    scheduledTxn.tokenGrantKyc().orElseThrow());
            case TOKEN_REVOKE_KYC -> ordinary.tokenRevokeKyc(
                    scheduledTxn.tokenRevokeKyc().orElseThrow());
            case TOKEN_DELETION -> ordinary.tokenDeletion(
                    scheduledTxn.tokenDeletion().orElseThrow());
            case TOKEN_UPDATE -> ordinary.tokenUpdate(scheduledTxn.tokenUpdate().orElseThrow());
            case TOKEN_MINT -> ordinary.tokenMint(scheduledTxn.tokenMint().orElseThrow());
            case TOKEN_BURN -> ordinary.tokenBurn(scheduledTxn.tokenBurn().orElseThrow());
            case TOKEN_WIPE -> ordinary.tokenWipe(scheduledTxn.tokenWipe().orElseThrow());
            case TOKEN_ASSOCIATE -> ordinary.tokenAssociate(
                    scheduledTxn.tokenAssociate().orElseThrow());
            case TOKEN_DISSOCIATE -> ordinary.tokenDissociate(
                    scheduledTxn.tokenDissociate().orElseThrow());
            case SCHEDULE_DELETE -> ordinary.scheduleDelete(
                    scheduledTxn.scheduleDelete().orElseThrow());
            case TOKEN_PAUSE -> ordinary.tokenPause(scheduledTxn.tokenPause().orElseThrow());
            case TOKEN_UNPAUSE -> ordinary.tokenUnpause(
                    scheduledTxn.tokenUnpause().orElseThrow());
            case CRYPTO_APPROVE_ALLOWANCE -> ordinary.cryptoApproveAllowance(
                    scheduledTxn.cryptoApproveAllowance().orElseThrow());
            case CRYPTO_DELETE_ALLOWANCE -> ordinary.cryptoDeleteAllowance(
                    scheduledTxn.cryptoDeleteAllowance().orElseThrow());
            case TOKEN_FEE_SCHEDULE_UPDATE -> ordinary.tokenFeeScheduleUpdate(
                    scheduledTxn.tokenFeeScheduleUpdate().orElseThrow());
            case UTIL_PRNG -> ordinary.utilPrng(scheduledTxn.utilPrng().orElseThrow());
            default -> throw new RuntimeException(new UnknownHederaFunctionality());
        }

        return ordinary.build();
    }
}
