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

package com.hedera.node.app.workflows.handle.flow.dispatch.logic;

import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_DURATION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import org.junit.jupiter.api.Test;

public class ErrorReportTest {
    private static final AccountID CREATOR_ACCOUNT_ID =
            AccountID.newBuilder().accountNum(10L).build();
    private static final Account PAYER_ACCOUNT_ID = Account.newBuilder()
            .accountId(AccountID.newBuilder().accountNum(200L).build())
            .build();

    @Test
    public void testCreatorErrorReport() {
        ErrorReport report = ErrorReport.creatorErrorReport(CREATOR_ACCOUNT_ID, INVALID_TRANSACTION_DURATION);

        assertEquals(CREATOR_ACCOUNT_ID, report.creatorId());
        assertEquals(INVALID_TRANSACTION_DURATION, report.creatorError());
        assertNull(report.payer());
        assertNull(report.payerError());
        assertFalse(report.unableToPayServiceFee());
        assertEquals(IsDuplicate.NO, report.isDuplicate());
        assertTrue(report.isCreatorError());
        assertFalse(report.isPayerError());
    }

    @Test
    public void testPayerDuplicateErrorReport() {
        ErrorReport report = ErrorReport.payerDuplicateErrorReport(CREATOR_ACCOUNT_ID, PAYER_ACCOUNT_ID);

        assertEquals(CREATOR_ACCOUNT_ID, report.creatorId());
        assertNull(report.creatorError());
        assertEquals(PAYER_ACCOUNT_ID, report.payer());
        assertEquals(DUPLICATE_TRANSACTION, report.payerError());
        assertFalse(report.unableToPayServiceFee());
        assertEquals(IsDuplicate.YES, report.isDuplicate());
        assertFalse(report.isCreatorError());
        assertTrue(report.isPayerError());
    }

    @Test
    public void testPayerUniqueErrorReport() {
        ErrorReport report =
                ErrorReport.payerUniqueErrorReport(CREATOR_ACCOUNT_ID, PAYER_ACCOUNT_ID, INVALID_PAYER_SIGNATURE);

        assertEquals(CREATOR_ACCOUNT_ID, report.creatorId());
        assertNull(report.creatorError());
        assertEquals(PAYER_ACCOUNT_ID, report.payer());
        assertEquals(INVALID_PAYER_SIGNATURE, report.payerError());
        assertFalse(report.unableToPayServiceFee());
        assertEquals(IsDuplicate.NO, report.isDuplicate());
        assertFalse(report.isCreatorError());
        assertTrue(report.isPayerError());
    }

    @Test
    public void testPayerErrorReport() {
        ErrorReport report = ErrorReport.payerErrorReport(
                CREATOR_ACCOUNT_ID, PAYER_ACCOUNT_ID, INVALID_PAYER_SIGNATURE, true, IsDuplicate.YES);

        assertEquals(CREATOR_ACCOUNT_ID, report.creatorId());
        assertNull(report.creatorError());
        assertEquals(PAYER_ACCOUNT_ID, report.payer());
        assertEquals(INVALID_PAYER_SIGNATURE, report.payerError());
        assertTrue(report.unableToPayServiceFee());
        assertEquals(IsDuplicate.YES, report.isDuplicate());
        assertFalse(report.isCreatorError());
        assertTrue(report.isPayerError());
    }

    @Test
    public void testErrorFreeReport() {
        ErrorReport report = ErrorReport.errorFreeReport(CREATOR_ACCOUNT_ID, PAYER_ACCOUNT_ID);

        assertEquals(CREATOR_ACCOUNT_ID, report.creatorId());
        assertNull(report.creatorError());
        assertEquals(PAYER_ACCOUNT_ID, report.payer());
        assertNull(report.payerError());
        assertFalse(report.unableToPayServiceFee());
        assertEquals(IsDuplicate.NO, report.isDuplicate());
        assertFalse(report.isCreatorError());
        assertFalse(report.isPayerError());
    }

    @Test
    public void testIsCreatorError() {
        ErrorReport report =
                new ErrorReport(CREATOR_ACCOUNT_ID, INVALID_TRANSACTION_DURATION, null, null, false, IsDuplicate.NO);

        assertTrue(report.isCreatorError());
    }

    @Test
    public void testIsPayerError() {
        ErrorReport report = new ErrorReport(
                CREATOR_ACCOUNT_ID, null, PAYER_ACCOUNT_ID, INVALID_PAYER_SIGNATURE, false, IsDuplicate.NO);

        assertTrue(report.isPayerError());
    }

    @Test
    public void testPayerErrorOrThrow() {
        ErrorReport report = new ErrorReport(
                CREATOR_ACCOUNT_ID, null, PAYER_ACCOUNT_ID, INVALID_PAYER_SIGNATURE, false, IsDuplicate.NO);

        assertEquals(INVALID_PAYER_SIGNATURE, report.payerErrorOrThrow());
    }

    @Test
    public void testCreatorErrorOrThrow() {
        ErrorReport report =
                new ErrorReport(CREATOR_ACCOUNT_ID, INVALID_TRANSACTION_DURATION, null, null, false, IsDuplicate.NO);

        assertEquals(INVALID_TRANSACTION_DURATION, report.creatorErrorOrThrow());
    }

    @Test
    public void testPayerOrThrow() {
        ErrorReport report = new ErrorReport(CREATOR_ACCOUNT_ID, null, PAYER_ACCOUNT_ID, null, false, IsDuplicate.NO);

        assertEquals(PAYER_ACCOUNT_ID, report.payerOrThrow());
    }

    @Test
    public void testWithoutServiceFee() {
        ErrorReport report = new ErrorReport(CREATOR_ACCOUNT_ID, null, PAYER_ACCOUNT_ID, null, false, IsDuplicate.NO);
        ErrorReport reportWithoutServiceFee = report.withoutServiceFee();

        assertTrue(reportWithoutServiceFee.unableToPayServiceFee());
    }
}
