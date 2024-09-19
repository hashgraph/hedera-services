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

package com.hedera.node.app.service.consensus.impl.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.spi.validation.Validations.mustExist;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ConsensusCryptoFeeScheduleAllowance;
import com.hedera.hapi.node.base.ConsensusTokenFeeScheduleAllowance;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.token.ConsensusApproveAllowanceTransactionBody;
import com.hedera.node.app.spi.workflows.PreCheckException;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;

public class ConsensusAllowancesValidator {

    /**
     * Constructs a {@link ConsensusAllowancesValidator} instance.
     */
    @Inject
    public ConsensusAllowancesValidator() {
        // Needed for Dagger injection
    }

    public void pureChecks(ConsensusApproveAllowanceTransactionBody op) throws PreCheckException {
        // The transaction must have at least one type of allowance.
        final var cryptoAllowances = op.consensusCryptoFeeScheduleAllowances();
        final var tokenAllowances = op.consensusTokenFeeScheduleAllowances();
        final var totalAllowancesSize = cryptoAllowances.size() + tokenAllowances.size();
        validateTruePreCheck(totalAllowancesSize != 0, EMPTY_ALLOWANCES);
        validateCryptoAllowances(cryptoAllowances);
        validateTokenAllowances(tokenAllowances);
    }

    private static void validateCryptoAllowances(List<ConsensusCryptoFeeScheduleAllowance> cryptoAllowances)
            throws PreCheckException {
        final var uniqueMap = new HashMap<AccountID, TopicID>();
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
    }

    private static void validateTokenAllowances(List<ConsensusTokenFeeScheduleAllowance> tokenAllowances)
            throws PreCheckException {
        final var uniqueMap = new HashMap<AccountID, TopicID>();
        for (var tokenAllowance : tokenAllowances) {
            // Check if a given AccountId/TopicId pair already exists in the token allowances list
            validateFalsePreCheck(
                    uniqueMap.containsKey(tokenAllowance.owner())
                            && uniqueMap.get(tokenAllowance.owner()).equals(tokenAllowance.topicId()),
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
}
