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
package com.hedera.services.txns.submission;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.services.context.domain.security.HapiOpPermissions;
import com.hedera.services.throttling.TransactionThrottling;
import com.hedera.services.txns.auth.SystemOpAuthorization;
import com.hedera.services.txns.auth.SystemOpPolicies;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemPrecheckTest {
    private final AccountID systemPayer = IdUtils.asAccount("0.0.50");
    private final AccountID civilianPayer = IdUtils.asAccount("0.0.1234");
    private final SignedTxnAccessor civilianXferAccessor =
            SignedTxnAccessor.uncheckedFrom(
                    Transaction.newBuilder()
                            .setBodyBytes(
                                    TransactionBody.newBuilder()
                                            .setTransactionID(
                                                    TransactionID.newBuilder()
                                                            .setAccountID(civilianPayer))
                                            .setCryptoTransfer(
                                                    CryptoTransferTransactionBody
                                                            .getDefaultInstance())
                                            .build()
                                            .toByteString())
                            .build());
    private final SignedTxnAccessor systemXferAccessor =
            SignedTxnAccessor.uncheckedFrom(
                    Transaction.newBuilder()
                            .setBodyBytes(
                                    TransactionBody.newBuilder()
                                            .setTransactionID(
                                                    TransactionID.newBuilder()
                                                            .setAccountID(systemPayer))
                                            .setCryptoTransfer(
                                                    CryptoTransferTransactionBody
                                                            .getDefaultInstance())
                                            .build()
                                            .toByteString())
                            .build());

    @Mock private SystemOpPolicies systemOpPolicies;
    @Mock private HapiOpPermissions hapiOpPermissions;
    @Mock private TransactionThrottling txnThrottling;

    private SystemPrecheck subject;

    @BeforeEach
    void setUp() {
        subject = new SystemPrecheck(systemOpPolicies, hapiOpPermissions, txnThrottling);
    }

    @Test
    void rejectsUnsupportedOp() {
        given(hapiOpPermissions.permissibilityOf(CryptoTransfer, civilianPayer))
                .willReturn(NOT_SUPPORTED);

        // when:
        var actual = subject.screen(civilianXferAccessor);

        // then:
        assertEquals(NOT_SUPPORTED, actual);
    }

    @Test
    void rejectsUnprivilegedPayer() {
        givenPermissible(civilianPayer);
        given(systemOpPolicies.checkAccessor(civilianXferAccessor))
                .willReturn(SystemOpAuthorization.IMPERMISSIBLE);

        // when:
        var actual = subject.screen(civilianXferAccessor);

        // then:
        assertEquals(SystemOpAuthorization.IMPERMISSIBLE.asStatus(), actual);
    }

    @Test
    void throttlesCivilianIfBusy() {
        givenPermissible(civilianPayer);
        givenPriviliged();
        given(txnThrottling.shouldThrottle(civilianXferAccessor)).willReturn(true);

        // when:
        var actual = subject.screen(civilianXferAccessor);

        // then:
        assertEquals(BUSY, actual);
    }

    @Test
    void throttlesSystemAccounts() {
        givenNoSystemCapacity();
        givenPermissible(systemPayer);
        givenPriviliged();

        // when:
        var actual = subject.screen(systemXferAccessor);

        // then:
        assertEquals(BUSY, actual);
    }

    @Test
    void okIfAllScreensPass() {
        givenPermissible(civilianPayer);
        givenPriviliged();
        givenCivilianCapacity();

        // when:
        var actual = subject.screen(civilianXferAccessor);

        // then:
        assertEquals(OK, actual);
    }

    private void givenCivilianCapacity() {
        given(txnThrottling.shouldThrottle(civilianXferAccessor)).willReturn(false);
    }

    private void givenNoSystemCapacity() {
        given(txnThrottling.shouldThrottle(systemXferAccessor)).willReturn(true);
    }

    private void givenPermissible(AccountID payer) {
        given(hapiOpPermissions.permissibilityOf(CryptoTransfer, payer)).willReturn(OK);
    }

    private void givenPriviliged() {
        given(systemOpPolicies.checkAccessor(any())).willReturn(SystemOpAuthorization.UNNECESSARY);
    }
}
