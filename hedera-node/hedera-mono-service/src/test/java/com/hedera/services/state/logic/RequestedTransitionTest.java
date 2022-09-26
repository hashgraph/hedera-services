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
package com.hedera.services.state.logic;

import static com.hedera.services.txns.auth.SystemOpAuthorization.IMPERMISSIBLE;
import static com.hedera.services.txns.auth.SystemOpAuthorization.UNNECESSARY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.domain.security.HapiOpPermissions;
import com.hedera.services.txns.TransitionRunner;
import com.hedera.services.txns.auth.SystemOpPolicies;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestedTransitionTest {
    @Mock private TransitionRunner transitionRunner;
    @Mock private SystemOpPolicies opPolicies;
    @Mock private TransactionContext txnCtx;
    @Mock private NetworkCtxManager networkCtxManager;
    @Mock private AccountID payer;
    @Mock private TxnAccessor accessor;
    @Mock private HapiOpPermissions hapiOpPermissions;

    private RequestedTransition subject;

    @BeforeEach
    void setUp() {
        subject =
                new RequestedTransition(
                        transitionRunner, opPolicies, txnCtx, networkCtxManager, hapiOpPermissions);
    }

    @Test
    void finishesTransitionWithAuthFailure() {
        given(accessor.getFunction()).willReturn(HederaFunctionality.CryptoTransfer);
        given(accessor.getPayer()).willReturn(payer);
        given(hapiOpPermissions.permissibilityOf(HederaFunctionality.CryptoTransfer, payer))
                .willReturn(OK);
        given(opPolicies.checkAccessor(accessor)).willReturn(IMPERMISSIBLE);

        // when:
        subject.finishFor(accessor);

        // then:
        verify(txnCtx).setStatus(IMPERMISSIBLE.asStatus());
        verify(transitionRunner, never()).tryTransition(accessor);
    }

    @Test
    void finishesTransitionWithPermissionFailure() {
        given(accessor.getFunction()).willReturn(HederaFunctionality.CryptoTransfer);
        given(accessor.getPayer()).willReturn(payer);
        given(hapiOpPermissions.permissibilityOf(HederaFunctionality.CryptoTransfer, payer))
                .willReturn(AUTHORIZATION_FAILED);

        // when:
        subject.finishFor(accessor);

        // then:
        verify(txnCtx).setStatus(AUTHORIZATION_FAILED);
        verify(transitionRunner, never()).tryTransition(accessor);
    }

    @Test
    void incorporatesAfterFinishingWithSuccess() {
        given(accessor.getFunction()).willReturn(HederaFunctionality.CryptoTransfer);
        given(accessor.getPayer()).willReturn(payer);
        given(hapiOpPermissions.permissibilityOf(HederaFunctionality.CryptoTransfer, payer))
                .willReturn(OK);
        given(opPolicies.checkAccessor(accessor)).willReturn(UNNECESSARY);
        given(transitionRunner.tryTransition(accessor)).willReturn(true);

        // when:
        subject.finishFor(accessor);

        // then:
        verify(transitionRunner).tryTransition(accessor);
        verify(networkCtxManager).finishIncorporating(HederaFunctionality.CryptoTransfer);
    }

    @Test
    void doesntIncorporateAfterFailedTransition() {
        given(accessor.getFunction()).willReturn(HederaFunctionality.CryptoTransfer);
        given(accessor.getPayer()).willReturn(payer);
        given(hapiOpPermissions.permissibilityOf(HederaFunctionality.CryptoTransfer, payer))
                .willReturn(OK);
        given(opPolicies.checkAccessor(accessor)).willReturn(UNNECESSARY);

        // when:
        subject.finishFor(accessor);

        // then:
        verify(networkCtxManager, never()).finishIncorporating(any());
    }
}
