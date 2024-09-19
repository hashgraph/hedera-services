/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.consensus.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONSENSUS_APPROVE_ALLOWANCE}.
 */
public class ConsensusApproveAllowanceHandler implements TransactionHandler {
    @Override
    public void preHandle(@NonNull PreHandleContext context) throws PreCheckException {
        // TODO: Implement this method
    }

    @Override
    public void pureChecks(@NonNull TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        final var op = txn.consensusApproveAllowanceOrThrow();

        // The transaction must have at least one type of allowance.
        final var cryptoAllowances = op.consensusCryptoFeeScheduleAllowances();
        final var tokenAllowances = op.consensusTokenFeeScheduleAllowances();
        final var totalAllowancesSize = cryptoAllowances.size() + tokenAllowances.size();
        validateTruePreCheck(totalAllowancesSize != 0, EMPTY_ALLOWANCES);

        // validate hbar allowances
        final var uniqueMap = new HashMap<>();
        for (var hbarAllowance : cryptoAllowances) {
            // Check if a given AccountId/TopicId pair already exists in the crypto allowances list
            validateFalsePreCheck(
                    uniqueMap.containsKey(hbarAllowance.owner())
                            && uniqueMap.get(hbarAllowance.owner()).equals(hbarAllowance.topicId()),
                    ResponseCodeEnum.REPEATED_ALLOWANCE_IN_TRANSACTION_BODY);
            // Validate the allowance amount and amount per message
            validateTruePreCheck(hbarAllowance.amount() >= 0, ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT);
            validateTruePreCheck(hbarAllowance.amountPerMessage() >= 0, ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT);
            validateTruePreCheck(
                    hbarAllowance.amount() > hbarAllowance.amountPerMessage(),
                    ResponseCodeEnum.ALLOWANCE_PER_MESSAGE_EXCEEDS_TOTAL_ALLOWANCE);
            // Add the unique (AccountID, TopicID) pair to the map
            uniqueMap.put(hbarAllowance.owner(), hbarAllowance.topicId());
        }
        // validate token allowances
        uniqueMap.clear();
        for (var tokenAllowance : tokenAllowances) {
            // Check if a given AccountId/TopicId pair already exists in the token allowances list
            validateFalsePreCheck(
                    uniqueMap.containsKey(tokenAllowance.owner())
                            && uniqueMap.get(tokenAllowance.owner()).equals(tokenAllowance.tokenId()),
                    ResponseCodeEnum.REPEATED_ALLOWANCE_IN_TRANSACTION_BODY);
            // Validate the allowance amount and amount per message
            validateTruePreCheck(tokenAllowance.amount() >= 0, ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT);
            validateTruePreCheck(tokenAllowance.amountPerMessage() >= 0, ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT);
            validateTruePreCheck(
                    tokenAllowance.amount() > tokenAllowance.amountPerMessage(),
                    ResponseCodeEnum.ALLOWANCE_PER_MESSAGE_EXCEEDS_TOTAL_ALLOWANCE);
            mustExist(tokenAllowance.tokenId(), INVALID_TOKEN_ID);
            // Add the unique (AccountID, TopicID) pair to the map
            uniqueMap.put(tokenAllowance.owner(), tokenAllowance.topicId());
        }
    }

    @Override
    public void handle(@NonNull HandleContext context) throws HandleException {
        // TODO: Implement this method
    }
}
