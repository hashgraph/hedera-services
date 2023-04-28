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

package com.hedera.node.app.workflows.query;

import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_CREATE_TOPIC;
import static com.hedera.hapi.node.base.HederaFunctionality.CRYPTO_TRANSFER;
import static com.hedera.hapi.node.base.HederaFunctionality.GET_ACCOUNT_DETAILS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.estimatedFee;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.authorization.Authorizer;
import com.hedera.node.app.fees.QueryFeeCheck;
import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
import com.hedera.node.app.solvency.SolvencyPreCheck;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryCheckerTest {

    @Mock
    private HederaAccountNumbers accountNumbers;

    @Mock(strictness = LENIENT)
    private QueryFeeCheck queryFeeCheck;

    @Mock
    private Authorizer authorizer;

    @Mock
    private CryptoTransferHandler cryptoTransferHandler;

    @Mock
    private SolvencyPreCheck solvencyPreCheck;

    private QueryChecker checker;

    @BeforeEach
    void setup() {
        checker = new QueryChecker(accountNumbers, queryFeeCheck, authorizer, cryptoTransferHandler, solvencyPreCheck);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithIllegalArguments() {
        assertThatThrownBy(() ->
                        new QueryChecker(null, queryFeeCheck, authorizer, cryptoTransferHandler, solvencyPreCheck))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        new QueryChecker(accountNumbers, null, authorizer, cryptoTransferHandler, solvencyPreCheck))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() ->
                        new QueryChecker(accountNumbers, queryFeeCheck, null, cryptoTransferHandler, solvencyPreCheck))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryChecker(accountNumbers, queryFeeCheck, authorizer, null, solvencyPreCheck))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testValidateCryptoTransferWithIllegalArguments() {
        assertThatThrownBy(() -> checker.validateCryptoTransfer(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testValidateCryptoTransferSucceeds() {
        // given
        final var txBody = TransactionBody.newBuilder().build();
        final var signatureMap = SignatureMap.newBuilder().build();
        final var transaction = Transaction.newBuilder().build();
        final var transactionInfo = new TransactionInfo(transaction, txBody, signatureMap, CRYPTO_TRANSFER);

        // when
        assertThatCode(() -> checker.validateCryptoTransfer(transactionInfo)).doesNotThrowAnyException();
    }

    @Test
    void testValidateCryptoTransferWithWrongTransactionType() {
        // given
        final var txBody = TransactionBody.newBuilder().build();
        final var signatureMap = SignatureMap.newBuilder().build();
        final var transaction = Transaction.newBuilder().build();
        final var transactionInfo = new TransactionInfo(transaction, txBody, signatureMap, CONSENSUS_CREATE_TOPIC);

        // then
        assertThatThrownBy(() -> checker.validateCryptoTransfer(transactionInfo))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INSUFFICIENT_TX_FEE));
    }

    @Test
    void testValidateCryptoTransferWithFailingValidation() throws PreCheckException {
        // given
        final var txBody = TransactionBody.newBuilder().build();
        final var signatureMap = SignatureMap.newBuilder().build();
        final var transaction = Transaction.newBuilder().build();
        final var transactionInfo = new TransactionInfo(transaction, txBody, signatureMap, CRYPTO_TRANSFER);
        doThrow(new PreCheckException(INVALID_ACCOUNT_AMOUNTS))
                .when(cryptoTransferHandler)
                .validate(txBody);

        // then
        assertThatThrownBy(() -> checker.validateCryptoTransfer(transactionInfo))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_ACCOUNT_AMOUNTS));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testValidateAccountBalancesWithIllegalArguments() {
        // given
        final var payer = AccountID.newBuilder().build();
        final var txBody = TransactionBody.newBuilder().build();
        final var signatureMap = SignatureMap.newBuilder().build();
        final var transaction = Transaction.newBuilder().build();
        final var transactionInfo = new TransactionInfo(transaction, txBody, signatureMap, CONSENSUS_CREATE_TOPIC);

        // then
        assertThatThrownBy(() -> checker.validateAccountBalances(null, transactionInfo, 0L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> checker.validateAccountBalances(payer, null, 0L))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testValidateAccountBalancesSucceeds() {
        // given
        final var fee = 42L;
        final var payer = AccountID.newBuilder().build();
        final var accountAmount = AccountAmount.newBuilder().build();
        final var transferList =
                TransferList.newBuilder().accountAmounts(accountAmount).build();
        final var cryptoTransfer = CryptoTransferTransactionBody.newBuilder()
                .transfers(transferList)
                .build();
        final var nodeAccountId = AccountID.newBuilder().build();
        final var txBody = TransactionBody.newBuilder()
                .cryptoTransfer(cryptoTransfer)
                .nodeAccountID(nodeAccountId)
                .build();
        final var signatureMap = SignatureMap.newBuilder().build();
        final var transaction = Transaction.newBuilder().build();
        final var transactionInfo = new TransactionInfo(transaction, txBody, signatureMap, CONSENSUS_CREATE_TOPIC);

        // when
        assertDoesNotThrow(() -> checker.validateAccountBalances(payer, transactionInfo, fee));
    }

    @Test
    void testValidateAccountBalancesWithFailingSolvencyPreCheck() throws PreCheckException {
        // given
        final var fee = 42L;
        final var payer = AccountID.newBuilder().build();
        final var accountAmount = AccountAmount.newBuilder().build();
        final var transferList =
                TransferList.newBuilder().accountAmounts(accountAmount).build();
        final var cryptoTransfer = CryptoTransferTransactionBody.newBuilder()
                .transfers(transferList)
                .build();
        final var nodeAccountId = AccountID.newBuilder().build();
        final var txBody = TransactionBody.newBuilder()
                .cryptoTransfer(cryptoTransfer)
                .nodeAccountID(nodeAccountId)
                .build();
        final var signatureMap = SignatureMap.newBuilder().build();
        final var transaction = Transaction.newBuilder().build();
        final var transactionInfo = new TransactionInfo(transaction, txBody, signatureMap, CONSENSUS_CREATE_TOPIC);
        doThrow(new PreCheckException(PAYER_ACCOUNT_NOT_FOUND))
                .when(solvencyPreCheck)
                .assessWithSvcFees(transaction);

        // when
        assertThatThrownBy(() -> checker.validateAccountBalances(payer, transactionInfo, fee))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(PAYER_ACCOUNT_NOT_FOUND));
    }

    @Test
    void testValidateAccountBalancesWithFailingPaymentTransfers() throws InsufficientBalanceException {
        // given
        final var fee = 42L;
        final var payer = AccountID.newBuilder().build();
        final var accountAmount = AccountAmount.newBuilder().build();
        final var transferList =
                TransferList.newBuilder().accountAmounts(accountAmount).build();
        final var cryptoTransfer = CryptoTransferTransactionBody.newBuilder()
                .transfers(transferList)
                .build();
        final var nodeAccountId = AccountID.newBuilder().build();
        final var txBody = TransactionBody.newBuilder()
                .cryptoTransfer(cryptoTransfer)
                .nodeAccountID(nodeAccountId)
                .build();
        final var signatureMap = SignatureMap.newBuilder().build();
        final var transaction = Transaction.newBuilder().build();
        final var transactionInfo = new TransactionInfo(transaction, txBody, signatureMap, CONSENSUS_CREATE_TOPIC);
        doThrow(new InsufficientBalanceException(INSUFFICIENT_PAYER_BALANCE, fee))
                .when(queryFeeCheck)
                .validateQueryPaymentTransfers(txBody, fee);

        // when
        assertThatThrownBy(() -> checker.validateAccountBalances(payer, transactionInfo, fee))
                .isInstanceOf(InsufficientBalanceException.class)
                .has(responseCode(INSUFFICIENT_PAYER_BALANCE))
                .has(estimatedFee(fee));
    }

    @Test
    void testValidateAccountBalancesWithFailingNodePayment() throws InsufficientBalanceException {
        // given
        final var fee = 42L;
        final var payer = AccountID.newBuilder().build();
        final var accountAmount = AccountAmount.newBuilder().build();
        final var transferList =
                TransferList.newBuilder().accountAmounts(accountAmount).build();
        final var cryptoTransfer = CryptoTransferTransactionBody.newBuilder()
                .transfers(transferList)
                .build();
        final var nodeAccountId = AccountID.newBuilder().build();
        final var txBody = TransactionBody.newBuilder()
                .cryptoTransfer(cryptoTransfer)
                .nodeAccountID(nodeAccountId)
                .build();
        final var signatureMap = SignatureMap.newBuilder().build();
        final var transaction = Transaction.newBuilder().build();
        final var transactionInfo = new TransactionInfo(transaction, txBody, signatureMap, CONSENSUS_CREATE_TOPIC);
        doThrow(new InsufficientBalanceException(INSUFFICIENT_TX_FEE, fee))
                .when(queryFeeCheck)
                .nodePaymentValidity(List.of(accountAmount), fee, nodeAccountId);

        // when
        assertThatThrownBy(() -> checker.validateAccountBalances(payer, transactionInfo, fee))
                .isInstanceOf(InsufficientBalanceException.class)
                .has(responseCode(INSUFFICIENT_TX_FEE))
                .has(estimatedFee(fee));
    }

    @Test
    void testValidateAccountBalancesWithSuperuserAndFailingNodePayment() throws InsufficientBalanceException {
        // given
        final var fee = 42L;
        final var payer = AccountID.newBuilder().accountNum(4711L).build();
        final var accountAmount = AccountAmount.newBuilder().build();
        final var transferList =
                TransferList.newBuilder().accountAmounts(accountAmount).build();
        final var cryptoTransfer = CryptoTransferTransactionBody.newBuilder()
                .transfers(transferList)
                .build();
        final var nodeAccountId = AccountID.newBuilder().build();
        final var txBody = TransactionBody.newBuilder()
                .cryptoTransfer(cryptoTransfer)
                .nodeAccountID(nodeAccountId)
                .build();
        final var signatureMap = SignatureMap.newBuilder().build();
        final var transaction = Transaction.newBuilder().build();
        final var transactionInfo = new TransactionInfo(transaction, txBody, signatureMap, CONSENSUS_CREATE_TOPIC);
        when(accountNumbers.isSuperuser(4711L)).thenReturn(true);
        doThrow(new InsufficientBalanceException(INSUFFICIENT_TX_FEE, fee))
                .when(queryFeeCheck)
                .nodePaymentValidity(List.of(accountAmount), fee, nodeAccountId);

        // when
        assertDoesNotThrow(() -> checker.validateAccountBalances(payer, transactionInfo, fee));
    }

    @Test
    void onlyAccountNumCanBeSuperuserInValidateAccountBalances() throws InsufficientBalanceException {
        // given
        final var fee = 42L;
        final var payer = AccountID.newBuilder()
                .alias(Bytes.wrap("acct alias".getBytes()))
                .build();
        final var accountAmount = AccountAmount.newBuilder().build();
        final var transferList =
                TransferList.newBuilder().accountAmounts(accountAmount).build();
        final var cryptoTransfer = CryptoTransferTransactionBody.newBuilder()
                .transfers(transferList)
                .build();
        final var nodeAccountId = AccountID.newBuilder().build();
        final var txBody = TransactionBody.newBuilder()
                .cryptoTransfer(cryptoTransfer)
                .nodeAccountID(nodeAccountId)
                .build();
        final var signatureMap = SignatureMap.newBuilder().build();
        final var transaction = Transaction.newBuilder().build();
        final var transactionInfo = new TransactionInfo(transaction, txBody, signatureMap, CONSENSUS_CREATE_TOPIC);
        doThrow(new InsufficientBalanceException(INSUFFICIENT_TX_FEE, fee))
                .when(queryFeeCheck)
                .nodePaymentValidity(List.of(accountAmount), fee, nodeAccountId);

        // when
        assertThatThrownBy(() -> checker.validateAccountBalances(payer, transactionInfo, fee))
                .isInstanceOf(InsufficientBalanceException.class)
                .has(responseCode(INSUFFICIENT_TX_FEE));
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testCheckPermissionWithIllegalArguments() {
        // given
        final var payer = AccountID.newBuilder().build();

        // then
        assertThatThrownBy(() -> checker.checkPermissions(null, GET_ACCOUNT_DETAILS))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> checker.checkPermissions(payer, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testCheckPermissionSucceeds() {
        // given
        final var payer = AccountID.newBuilder().build();
        when(authorizer.isAuthorized(payer, GET_ACCOUNT_DETAILS)).thenReturn(true);

        // then
        assertDoesNotThrow(() -> checker.checkPermissions(payer, GET_ACCOUNT_DETAILS));
    }

    @Test
    void testCheckPermissionFails() {
        // given
        final var payer = AccountID.newBuilder().build();
        when(authorizer.isAuthorized(payer, GET_ACCOUNT_DETAILS)).thenReturn(false);

        // then
        assertThatThrownBy(() -> checker.checkPermissions(payer, GET_ACCOUNT_DETAILS))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(NOT_SUPPORTED));
    }
}
