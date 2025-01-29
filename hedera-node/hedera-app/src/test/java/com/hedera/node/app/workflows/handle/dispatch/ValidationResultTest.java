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

package com.hedera.node.app.workflows.handle.dispatch;

import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static com.hedera.node.app.workflows.handle.dispatch.DispatchValidator.DuplicateStatus.DUPLICATE;
import static com.hedera.node.app.workflows.handle.dispatch.DispatchValidator.DuplicateStatus.NO_DUPLICATE;
import static com.hedera.node.app.workflows.handle.dispatch.DispatchValidator.ServiceFeeStatus.CAN_PAY_SERVICE_FEE;
import static com.hedera.node.app.workflows.handle.dispatch.DispatchValidator.ServiceFeeStatus.UNABLE_TO_PAY_SERVICE_FEE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import org.junit.jupiter.api.Test;

public class ValidationResultTest {
    private static final AccountID CREATOR_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(10L).build();
    private static final Account PAYER_ACCOUNT_ID = Account.newBuilder()
            .accountId(AccountID.newBuilder().accountNum(200L).build())
            .build();

    @Test
    public void testCreatorErrorReport() {
        ValidationResult report = ValidationResult.newCreatorError(CREATOR_ACCOUNT_ID, INVALID_TRANSACTION_DURATION);

        assertEquals(CREATOR_ACCOUNT_ID, report.creatorId());
        assertEquals(INVALID_TRANSACTION_DURATION, report.creatorError());
        assertNull(report.payer());
        assertNull(report.payerError());
        assertEquals(CAN_PAY_SERVICE_FEE, report.serviceFeeStatus());
        assertEquals(NO_DUPLICATE, report.duplicateStatus());
        assertTrue(report.isCreatorError());
        assertFalse(report.isPayerError());
    }

    @Test
    public void testPayerDuplicateErrorReport() {
        ValidationResult report = ValidationResult.newPayerDuplicateError(CREATOR_ACCOUNT_ID, PAYER_ACCOUNT_ID);

        assertEquals(CREATOR_ACCOUNT_ID, report.creatorId());
        assertNull(report.creatorError());
        assertEquals(PAYER_ACCOUNT_ID, report.payer());
        assertEquals(DUPLICATE_TRANSACTION, report.payerError());
        assertEquals(CAN_PAY_SERVICE_FEE, report.serviceFeeStatus());
        assertEquals(DUPLICATE, report.duplicateStatus());
        assertFalse(report.isCreatorError());
        assertTrue(report.isPayerError());
    }

    @Test
    public void testPayerUniqueErrorReport() {
        ValidationResult report =
                ValidationResult.newPayerUniqueError(CREATOR_ACCOUNT_ID, PAYER_ACCOUNT_ID, INVALID_PAYER_SIGNATURE);

        assertEquals(CREATOR_ACCOUNT_ID, report.creatorId());
        assertNull(report.creatorError());
        assertEquals(PAYER_ACCOUNT_ID, report.payer());
        assertEquals(INVALID_PAYER_SIGNATURE, report.payerError());
        assertEquals(CAN_PAY_SERVICE_FEE, report.serviceFeeStatus());
        assertEquals(NO_DUPLICATE, report.duplicateStatus());
        assertFalse(report.isCreatorError());
        assertTrue(report.isPayerError());
    }

    @Test
    public void testPayerErrorReport() {
        ValidationResult report = ValidationResult.newPayerError(
                CREATOR_ACCOUNT_ID, PAYER_ACCOUNT_ID, INVALID_PAYER_SIGNATURE, UNABLE_TO_PAY_SERVICE_FEE, DUPLICATE);

        assertEquals(CREATOR_ACCOUNT_ID, report.creatorId());
        assertNull(report.creatorError());
        assertEquals(PAYER_ACCOUNT_ID, report.payer());
        assertEquals(INVALID_PAYER_SIGNATURE, report.payerError());
        assertEquals(UNABLE_TO_PAY_SERVICE_FEE, report.serviceFeeStatus());
        assertEquals(DUPLICATE, report.duplicateStatus());
        assertFalse(report.isCreatorError());
        assertTrue(report.isPayerError());
    }

    @Test
    public void testErrorFreeReport() {
        ValidationResult report = ValidationResult.newSuccess(CREATOR_ACCOUNT_ID, PAYER_ACCOUNT_ID);

        assertEquals(CREATOR_ACCOUNT_ID, report.creatorId());
        assertNull(report.creatorError());
        assertEquals(PAYER_ACCOUNT_ID, report.payer());
        assertNull(report.payerError());
        assertEquals(CAN_PAY_SERVICE_FEE, report.serviceFeeStatus());
        assertEquals(NO_DUPLICATE, report.duplicateStatus());
        assertFalse(report.isCreatorError());
        assertFalse(report.isPayerError());
    }

    @Test
    public void testIsCreatorError() {
        ValidationResult report = new ValidationResult(
                CREATOR_ACCOUNT_ID, INVALID_TRANSACTION_DURATION, null, null, CAN_PAY_SERVICE_FEE, NO_DUPLICATE);

        assertTrue(report.isCreatorError());
    }

    @Test
    public void testIsPayerError() {
        ValidationResult report = new ValidationResult(
                CREATOR_ACCOUNT_ID, null, PAYER_ACCOUNT_ID, INVALID_PAYER_SIGNATURE, CAN_PAY_SERVICE_FEE, NO_DUPLICATE);

        assertTrue(report.isPayerError());
    }

    @Test
    public void testPayerErrorOrThrow() {
        ValidationResult report = new ValidationResult(
                CREATOR_ACCOUNT_ID, null, PAYER_ACCOUNT_ID, INVALID_PAYER_SIGNATURE, CAN_PAY_SERVICE_FEE, NO_DUPLICATE);

        assertEquals(INVALID_PAYER_SIGNATURE, report.payerErrorOrThrow());
    }

    @Test
    public void testCreatorErrorOrThrow() {
        ValidationResult report = new ValidationResult(
                CREATOR_ACCOUNT_ID, INVALID_TRANSACTION_DURATION, null, null, CAN_PAY_SERVICE_FEE, NO_DUPLICATE);

        assertEquals(INVALID_TRANSACTION_DURATION, report.creatorErrorOrThrow());
    }

    @Test
    public void testPayerOrThrow() {
        ValidationResult report = new ValidationResult(
                CREATOR_ACCOUNT_ID, null, PAYER_ACCOUNT_ID, null, CAN_PAY_SERVICE_FEE, NO_DUPLICATE);

        assertEquals(PAYER_ACCOUNT_ID, report.payerOrThrow());
    }

    @Test
    public void testWithoutServiceFee() {
        ValidationResult report = new ValidationResult(
                CREATOR_ACCOUNT_ID, null, PAYER_ACCOUNT_ID, null, CAN_PAY_SERVICE_FEE, NO_DUPLICATE);
        ValidationResult reportWithoutServiceFee = report.withoutServiceFee();

        assertEquals(UNABLE_TO_PAY_SERVICE_FEE, reportWithoutServiceFee.serviceFeeStatus());
    }
}
