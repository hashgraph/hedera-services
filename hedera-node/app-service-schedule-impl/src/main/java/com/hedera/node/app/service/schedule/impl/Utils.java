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
    private Utils() {}

    public static TransactionBody asOrdinary(
            final SchedulableTransactionBody scheduledTxn, final TransactionID scheduledTxnTransactionId) {
        final var ordinary = TransactionBody.newBuilder();
        ordinary.transactionFee(scheduledTxn.transactionFee())
                .memo(scheduledTxn.memo())
                .transactionID(scheduledTxnTransactionId.copyBuilder().scheduled(true));

        switch (scheduledTxn.data().kind()) {
            case CONSENSUS_CREATE_TOPIC -> ordinary.consensusCreateTopic(scheduledTxn.consensusCreateTopicOrThrow());
            case CONSENSUS_UPDATE_TOPIC -> ordinary.consensusUpdateTopic(scheduledTxn.consensusUpdateTopicOrThrow());
            case CONSENSUS_DELETE_TOPIC -> ordinary.consensusDeleteTopic(scheduledTxn.consensusDeleteTopicOrThrow());
            case CONSENSUS_SUBMIT_MESSAGE -> ordinary.consensusSubmitMessage(
                    scheduledTxn.consensusSubmitMessageOrThrow());
            case CRYPTO_CREATE_ACCOUNT -> ordinary.cryptoCreateAccount(scheduledTxn.cryptoCreateAccountOrThrow());
            case CRYPTO_UPDATE_ACCOUNT -> ordinary.cryptoUpdateAccount(scheduledTxn.cryptoUpdateAccountOrThrow());
            case CRYPTO_TRANSFER -> ordinary.cryptoTransfer(scheduledTxn.cryptoTransferOrThrow());
            case CRYPTO_DELETE -> ordinary.cryptoDelete(scheduledTxn.cryptoDeleteOrThrow());
            case FILE_CREATE -> ordinary.fileCreate(scheduledTxn.fileCreateOrThrow());
            case FILE_APPEND -> ordinary.fileAppend(scheduledTxn.fileAppendOrThrow());
            case FILE_UPDATE -> ordinary.fileUpdate(scheduledTxn.fileUpdateOrThrow());
            case FILE_DELETE -> ordinary.fileDelete(scheduledTxn.fileDeleteOrThrow());
            case CONTRACT_CREATE_INSTANCE -> ordinary.contractCreateInstance(
                    scheduledTxn.contractCreateInstanceOrThrow());
            case CONTRACT_UPDATE_INSTANCE -> ordinary.contractUpdateInstance(
                    scheduledTxn.contractUpdateInstanceOrThrow());
            case CONTRACT_CALL -> ordinary.contractCall(scheduledTxn.contractCallOrThrow());
            case CONTRACT_DELETE_INSTANCE -> ordinary.contractDeleteInstance(
                    scheduledTxn.contractDeleteInstanceOrThrow());
            case SYSTEM_DELETE -> ordinary.systemDelete(scheduledTxn.systemDeleteOrThrow());
            case SYSTEM_UNDELETE -> ordinary.systemUndelete(scheduledTxn.systemUndeleteOrThrow());
            case FREEZE -> ordinary.freeze(scheduledTxn.freezeOrThrow());
            case TOKEN_CREATION -> ordinary.tokenCreation(scheduledTxn.tokenCreationOrThrow());
            case TOKEN_FREEZE -> ordinary.tokenFreeze(scheduledTxn.tokenFreezeOrThrow());
            case TOKEN_UNFREEZE -> ordinary.tokenUnfreeze(scheduledTxn.tokenUnfreezeOrThrow());
            case TOKEN_GRANT_KYC -> ordinary.tokenGrantKyc(scheduledTxn.tokenGrantKycOrThrow());
            case TOKEN_REVOKE_KYC -> ordinary.tokenRevokeKyc(scheduledTxn.tokenRevokeKycOrThrow());
            case TOKEN_DELETION -> ordinary.tokenDeletion(scheduledTxn.tokenDeletionOrThrow());
            case TOKEN_UPDATE -> ordinary.tokenUpdate(scheduledTxn.tokenUpdateOrThrow());
            case TOKEN_MINT -> ordinary.tokenMint(scheduledTxn.tokenMintOrThrow());
            case TOKEN_BURN -> ordinary.tokenBurn(scheduledTxn.tokenBurnOrThrow());
            case TOKEN_WIPE -> ordinary.tokenWipe(scheduledTxn.tokenWipeOrThrow());
            case TOKEN_ASSOCIATE -> ordinary.tokenAssociate(scheduledTxn.tokenAssociateOrThrow());
            case TOKEN_DISSOCIATE -> ordinary.tokenDissociate(scheduledTxn.tokenDissociateOrThrow());
            case SCHEDULE_DELETE -> ordinary.scheduleDelete(scheduledTxn.scheduleDeleteOrThrow());
            case TOKEN_PAUSE -> ordinary.tokenPause(scheduledTxn.tokenPauseOrThrow());
            case TOKEN_UNPAUSE -> ordinary.tokenUnpause(scheduledTxn.tokenUnpauseOrThrow());
            case CRYPTO_APPROVE_ALLOWANCE -> ordinary.cryptoApproveAllowance(
                    scheduledTxn.cryptoApproveAllowanceOrThrow());
            case CRYPTO_DELETE_ALLOWANCE -> ordinary.cryptoDeleteAllowance(scheduledTxn.cryptoDeleteAllowanceOrThrow());
            case TOKEN_FEE_SCHEDULE_UPDATE -> ordinary.tokenFeeScheduleUpdate(
                    scheduledTxn.tokenFeeScheduleUpdateOrThrow());
            case UTIL_PRNG -> ordinary.utilPrng(scheduledTxn.utilPrngOrThrow());
            default -> throw new RuntimeException(new UnknownHederaFunctionality());
        }

        return ordinary.build();
    }
}
