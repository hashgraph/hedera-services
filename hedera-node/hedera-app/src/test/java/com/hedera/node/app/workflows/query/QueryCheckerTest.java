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
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.authorization.Authorizer;
import com.hedera.node.app.service.mono.queries.validation.QueryFeeCheck;
import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
import com.hedera.node.app.spi.info.CurrentPlatformStatus;
import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.workflows.InsufficientBalanceException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.system.PlatformStatus;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryCheckerTest {

    @Mock
    private NodeInfo nodeInfo;

    @Mock(strictness = LENIENT)
    private CurrentPlatformStatus currentPlatformStatus;

    @Mock
    private TransactionChecker transactionChecker;

    @Mock
    private HederaAccountNumbers accountNumbers;

    @Mock(strictness = LENIENT)
    private QueryFeeCheck queryFeeCheck;

    @Mock
    private Authorizer authorizer;

    @Mock
    private CryptoTransferHandler cryptoTransferHandler;

    private QueryChecker checker;

    @BeforeEach
    void setup() {
        when(currentPlatformStatus.get()).thenReturn(PlatformStatus.ACTIVE);

        ctx = new SessionContext();

        checker = new QueryChecker(
                nodeInfo,
                currentPlatformStatus,
                transactionChecker,
                accountNumbers,
                queryFeeCheck,
                authorizer,
                cryptoTransferHandler);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithIllegalArguments() {
        assertThatThrownBy(() -> new QueryChecker(
                        null,
                        currentPlatformStatus,
                        transactionChecker,
                        accountNumbers,
                        queryFeeCheck,
                        authorizer,
                        cryptoTransferHandler))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryChecker(
                        nodeInfo,
                        null,
                        transactionChecker,
                        accountNumbers,
                        queryFeeCheck,
                        authorizer,
                        cryptoTransferHandler))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryChecker(
                        nodeInfo,
                        currentPlatformStatus,
                        null,
                        accountNumbers,
                        queryFeeCheck,
                        authorizer,
                        cryptoTransferHandler))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryChecker(
                        nodeInfo,
                        currentPlatformStatus,
                        transactionChecker,
                        null,
                        queryFeeCheck,
                        authorizer,
                        cryptoTransferHandler))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryChecker(
                        nodeInfo,
                        currentPlatformStatus,
                        transactionChecker,
                        accountNumbers,
                        null,
                        authorizer,
                        cryptoTransferHandler))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryChecker(
                        nodeInfo,
                        currentPlatformStatus,
                        transactionChecker,
                        accountNumbers,
                        queryFeeCheck,
                        null,
                        cryptoTransferHandler))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new QueryChecker(
                        nodeInfo,
                        currentPlatformStatus,
                        transactionChecker,
                        accountNumbers,
                        queryFeeCheck,
                        authorizer,
                        null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testNodeStateSucceeds() {
        assertThatCode(() -> checker.checkNodeState()).doesNotThrowAnyException();
    }

    @Test
    void testZeroStakeNodeFails(@Mock NodeInfo localNodeInfo) {
        // given
        when(localNodeInfo.isSelfZeroStake()).thenReturn(true);
        checker = new QueryChecker(
                localNodeInfo,
                currentPlatformStatus,
                transactionChecker,
                accountNumbers,
                queryFeeCheck,
                authorizer,
                cryptoTransferHandler);

        // then
        assertThatThrownBy(() -> checker.checkNodeState())
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", ResponseCodeEnum.INVALID_NODE_ACCOUNT);
    }

    @Test
    void testInactivePlatformFails(@Mock CurrentPlatformStatus localCurrentPlatformStatus) {
        // given
        when(localCurrentPlatformStatus.get()).thenReturn(PlatformStatus.MAINTENANCE);
        checker = new QueryChecker(
                nodeInfo,
                localCurrentPlatformStatus,
                transactionChecker,
                accountNumbers,
                queryFeeCheck,
                authorizer,
                cryptoTransferHandler);

        // then
        assertThatThrownBy(() -> checker.checkNodeState())
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", ResponseCodeEnum.PLATFORM_NOT_ACTIVE);
    }

    @Test
    void testNodeStateSucceeds() {
        assertThatCode(() -> checker.checkNodeState()).doesNotThrowAnyException();
    }

    @Test
    void testZeroStakeNodeFails(@Mock NodeInfo localNodeInfo) {
        // given
        when(localNodeInfo.isSelfZeroStake()).thenReturn(true);
        checker = new QueryChecker(
                localNodeInfo,
                currentPlatformStatus,
                transactionChecker,
                accountNumbers,
                queryFeeCheck,
                authorizer,
                cryptoTransferHandler);

        // then
        assertThatThrownBy(() -> checker.checkNodeState())
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", ResponseCodeEnum.INVALID_NODE_ACCOUNT);
    }

    @Test
    void testInactivePlatformFails(@Mock CurrentPlatformStatus localCurrentPlatformStatus) {
        // given
        when(localCurrentPlatformStatus.get()).thenReturn(PlatformStatus.MAINTENANCE);
        checker = new QueryChecker(
                nodeInfo,
                localCurrentPlatformStatus,
                transactionChecker,
                accountNumbers,
                queryFeeCheck,
                authorizer,
                cryptoTransferHandler);

        // then
        assertThatThrownBy(() -> checker.checkNodeState())
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", ResponseCodeEnum.PLATFORM_NOT_ACTIVE);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testValidateCryptoTransferWithIllegalArguments() {
        assertThatThrownBy(() -> checker.validateCryptoTransfer(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testValidateCryptoTransferSucceeds() throws PreCheckException {
        // given
        final var txBody = TransactionBody.newBuilder().build();
        final var signatureMap = SignatureMap.newBuilder().build();
        final var onsetResult =
                new TransactionInfo(Transaction.newBuilder().build(), txBody, signatureMap, CRYPTO_TRANSFER);
        final var transaction = Transaction.newBuilder().build();
        when(transactionChecker.check(transaction)).thenReturn(onsetResult);

        // when
        final var result = checker.validateCryptoTransfer(transaction);

        // then
        Assertions.assertThat(result).isEqualTo(txBody);
    }

    @Test
    void testValidateCryptoTransferWithFailingParser() throws PreCheckException {
        // given
        final var transaction = Transaction.newBuilder().build();
        when(transactionChecker.check(transaction)).thenThrow(new PreCheckException(INVALID_TRANSACTION));
        final var checker = new QueryChecker(
                nodeInfo,
                currentPlatformStatus,
                transactionChecker,
                accountNumbers,
                queryFeeCheck,
                authorizer,
                cryptoTransferHandler);

        // then
        assertThatThrownBy(() -> checker.validateCryptoTransfer(transaction))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION);
    }

    @Test
    void testValidateCryptoTransferWithWrongTransactionType() throws PreCheckException {
        // given
        final var txBody = TransactionBody.newBuilder().build();
        final var signatureMap = SignatureMap.newBuilder().build();
        final var onsetResult =
                new TransactionInfo(Transaction.newBuilder().build(), txBody, signatureMap, CONSENSUS_CREATE_TOPIC);
        final var transaction = Transaction.newBuilder().build();
        when(transactionChecker.check(transaction)).thenReturn(onsetResult);
        final var checker = new QueryChecker(
                nodeInfo,
                currentPlatformStatus,
                transactionChecker,
                accountNumbers,
                queryFeeCheck,
                authorizer,
                cryptoTransferHandler);

        // then
        assertThatThrownBy(() -> checker.validateCryptoTransfer(transaction))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INSUFFICIENT_TX_FEE);
    }

    @Test
    void testValidateCryptoTransferWithFailingValidation() throws PreCheckException {
        // given
        final var txBody = TransactionBody.newBuilder().build();
        final var signatureMap = SignatureMap.newBuilder().build();
        final var onsetResult =
                new TransactionInfo(Transaction.newBuilder().build(), txBody, signatureMap, CRYPTO_TRANSFER);
        final var transaction = Transaction.newBuilder().build();
        when(transactionChecker.check(transaction)).thenReturn(onsetResult);
        doThrow(new PreCheckException(INVALID_ACCOUNT_AMOUNTS))
                .when(cryptoTransferHandler)
                .validate(txBody);
        final var checker = new QueryChecker(
                nodeInfo,
                currentPlatformStatus,
                transactionChecker,
                accountNumbers,
                queryFeeCheck,
                authorizer,
                cryptoTransferHandler);

        // then
        assertThatThrownBy(() -> checker.validateCryptoTransfer(transaction))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", INVALID_ACCOUNT_AMOUNTS);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testValidateAccountBalancesWithIllegalArguments() {
        // given
        final var payer = AccountID.newBuilder().build();
        final var txBody = TransactionBody.newBuilder().build();

        // then
        assertThatThrownBy(() -> checker.validateAccountBalances(null, txBody, 0L))
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
        when(queryFeeCheck.validateQueryPaymentTransfers2(txBody)).thenReturn(OK);
        when(queryFeeCheck.nodePaymentValidity2(List.of(accountAmount), fee, nodeAccountId))
                .thenReturn(OK);

        // when
        assertDoesNotThrow(() -> checker.validateAccountBalances(payer, txBody, fee));
    }

    @Test
    void testValidateAccountBalancesWithFailingPaymentTransfers() {
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
        when(queryFeeCheck.validateQueryPaymentTransfers2(txBody)).thenReturn(INSUFFICIENT_PAYER_BALANCE);

        // when
        assertThatThrownBy(() -> checker.validateAccountBalances(payer, txBody, fee))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasFieldOrPropertyWithValue("responseCode", INSUFFICIENT_PAYER_BALANCE)
                .hasFieldOrPropertyWithValue("estimatedFee", fee);
    }

    @Test
    void testValidateAccountBalancesWithFailingNodePayment() {
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
        when(queryFeeCheck.validateQueryPaymentTransfers2(txBody)).thenReturn(OK);
        when(queryFeeCheck.nodePaymentValidity2(List.of(accountAmount), fee, nodeAccountId))
                .thenReturn(INSUFFICIENT_TX_FEE);

        // when
        assertThatThrownBy(() -> checker.validateAccountBalances(payer, txBody, fee))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasFieldOrPropertyWithValue("responseCode", INSUFFICIENT_TX_FEE)
                .hasFieldOrPropertyWithValue("estimatedFee", fee);
    }

    @Test
    void testValidateAccountBalancesWithSuperuserAndFailingNodePayment() {
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
        when(queryFeeCheck.validateQueryPaymentTransfers2(txBody)).thenReturn(OK);
        when(accountNumbers.isSuperuser(4711L)).thenReturn(true);
        when(queryFeeCheck.nodePaymentValidity2(List.of(accountAmount), fee, nodeAccountId))
                .thenReturn(INSUFFICIENT_TX_FEE);

        // when
        assertDoesNotThrow(() -> checker.validateAccountBalances(payer, txBody, fee));
    }

    @Test
    void onlyAccountNumCanBeSuperuserInValidateAccountBalances() {
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
        when(queryFeeCheck.validateQueryPaymentTransfers2(txBody)).thenReturn(OK);
        when(queryFeeCheck.nodePaymentValidity2(List.of(accountAmount), fee, nodeAccountId))
                .thenReturn(INSUFFICIENT_TX_FEE);

        // when
        assertThatThrownBy(() -> checker.validateAccountBalances(payer, txBody, fee))
                .isInstanceOf(InsufficientBalanceException.class)
                .hasFieldOrPropertyWithValue("responseCode", INSUFFICIENT_TX_FEE);
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
                .hasFieldOrPropertyWithValue("responseCode", NOT_SUPPORTED);
    }
}
