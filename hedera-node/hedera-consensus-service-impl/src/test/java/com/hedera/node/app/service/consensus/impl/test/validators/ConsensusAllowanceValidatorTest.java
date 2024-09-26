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

package com.hedera.node.app.service.consensus.impl.test.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.EMPTY_ALLOWANCES;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NEGATIVE_ALLOWANCE_AMOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.REPEATED_ALLOWANCE_IN_TRANSACTION_BODY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ConsensusCryptoFeeScheduleAllowance;
import com.hedera.hapi.node.base.ConsensusTokenFeeScheduleAllowance;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.token.ConsensusApproveAllowanceTransactionBody;
import com.hedera.node.app.service.consensus.impl.validators.ConsensusAllowancesValidator;
import com.hedera.node.app.spi.workflows.PreCheckException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsensusAllowancesValidatorTest {

    private ConsensusAllowancesValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ConsensusAllowancesValidator();
    }

    @Test
    void validAllowancesPasses() throws PreCheckException {
        // Arrange
        var cryptoAllowance = ConsensusCryptoFeeScheduleAllowance.newBuilder()
                .owner(AccountID.DEFAULT)
                .topicId(TopicID.DEFAULT)
                .amount(100L)
                .amountPerMessage(10L)
                .build();

        var tokenAllowance = ConsensusTokenFeeScheduleAllowance.newBuilder()
                .owner(AccountID.DEFAULT)
                .topicId(TopicID.DEFAULT)
                .amount(200L)
                .amountPerMessage(20L)
                .tokenId(TokenID.DEFAULT)
                .build();

        var op = ConsensusApproveAllowanceTransactionBody.newBuilder()
                .consensusCryptoFeeScheduleAllowances(cryptoAllowance)
                .consensusTokenFeeScheduleAllowances(tokenAllowance)
                .build();

        // Act & Assert: Should pass without exception
        validator.pureChecks(op);
    }

    @Test
    void emptyAllowancesThrows() {
        // Arrange: Create an operation with no allowances
        var op = ConsensusApproveAllowanceTransactionBody.newBuilder().build();

        // Act & Assert: Should throw PreCheckException with EMPTY_ALLOWANCES response
        var exception = assertThrows(PreCheckException.class, () -> validator.pureChecks(op));
        assertEquals(exception.responseCode(), EMPTY_ALLOWANCES);
    }

    @Test
    void repeatedTokenAllowancesThrows() {
        // Arrange: Create two token allowances with the same owner and topicId
        var tokenAllowance1 = ConsensusTokenFeeScheduleAllowance.newBuilder()
                .owner(AccountID.DEFAULT)
                .topicId(TopicID.DEFAULT)
                .amount(100L)
                .amountPerMessage(10L)
                .tokenId(TokenID.DEFAULT)
                .build();

        var tokenAllowance2 = ConsensusTokenFeeScheduleAllowance.newBuilder()
                .owner(AccountID.DEFAULT)
                .topicId(TopicID.DEFAULT) // Same AccountID and TopicID as tokenAllowance1
                .amount(200L)
                .amountPerMessage(20L)
                .tokenId(TokenID.DEFAULT)
                .build();

        // Build the operation containing the token allowances
        var op = ConsensusApproveAllowanceTransactionBody.newBuilder()
                .consensusTokenFeeScheduleAllowances(tokenAllowance1, tokenAllowance2)
                .build();

        // Act & Assert: Should throw PreCheckException with REPEATED_ALLOWANCE_IN_TRANSACTION_BODY
        var exception = assertThrows(PreCheckException.class, () -> validator.pureChecks(op));
        assertEquals(REPEATED_ALLOWANCE_IN_TRANSACTION_BODY, exception.responseCode());
    }

    @Test
    void repeatedCryptoAllowancesThrows() {
        // Arrange: Create two crypto allowances with the same owner and topicId
        var cryptoAllowance1 = ConsensusCryptoFeeScheduleAllowance.newBuilder()
                .owner(AccountID.DEFAULT)
                .topicId(TopicID.DEFAULT)
                .amount(100L)
                .amountPerMessage(10L)
                .build();

        var cryptoAllowance2 = ConsensusCryptoFeeScheduleAllowance.newBuilder()
                .owner(AccountID.DEFAULT)
                .topicId(TopicID.DEFAULT) // Same AccountID and TopicID as cryptoAllowance1
                .amount(200L)
                .amountPerMessage(20L)
                .build();

        // Build the operation containing the crypto allowances
        var op = ConsensusApproveAllowanceTransactionBody.newBuilder()
                .consensusCryptoFeeScheduleAllowances(cryptoAllowance1, cryptoAllowance2)
                .build();

        // Act & Assert: Should throw PreCheckException with REPEATED_ALLOWANCE_IN_TRANSACTION_BODY
        var exception = assertThrows(PreCheckException.class, () -> validator.pureChecks(op));
        assertEquals(REPEATED_ALLOWANCE_IN_TRANSACTION_BODY, exception.responseCode());
    }

    @Test
    void invalidTokenIdThrows() {
        // Arrange
        var tokenAllowance = ConsensusTokenFeeScheduleAllowance.newBuilder()
                .owner(AccountID.DEFAULT)
                .topicId(TopicID.DEFAULT)
                .amount(100L)
                .amountPerMessage(10L)
                .tokenId((TokenID) null) // Invalid token ID
                .build();

        var op = ConsensusApproveAllowanceTransactionBody.newBuilder()
                .consensusTokenFeeScheduleAllowances(tokenAllowance)
                .build();

        // Act & Assert: Should throw PreCheckException with INVALID_TOKEN_ID code
        var exception = assertThrows(PreCheckException.class, () -> validator.pureChecks(op));
        assertEquals(INVALID_TOKEN_ID, exception.responseCode());
    }

    @Test
    void negativeAmountsThrows() {
        // Arrange
        var cryptoAllowance = ConsensusCryptoFeeScheduleAllowance.newBuilder()
                .owner(AccountID.DEFAULT)
                .topicId(TopicID.DEFAULT)
                .amount(-100L) // Negative allowance
                .amountPerMessage(10L)
                .build();

        var op = ConsensusApproveAllowanceTransactionBody.newBuilder()
                .consensusCryptoFeeScheduleAllowances(cryptoAllowance)
                .build();

        // Act & Assert: Should throw PreCheckException with NEGATIVE_ALLOWANCE_AMOUNT code
        var exception = assertThrows(PreCheckException.class, () -> validator.pureChecks(op));
        assertEquals(NEGATIVE_ALLOWANCE_AMOUNT, exception.responseCode());
    }
}
