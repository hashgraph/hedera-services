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

package com.hedera.node.app.authorization;

import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_CREATE_TOPIC;
import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.service.mono.context.domain.security.HapiOpPermissions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

final class AuthorizerTest {
    private HapiOpPermissions hapiOpPermissions;
    private AccountID accountID;
    private HederaFunctionality hapiFunction;

    @BeforeEach
    void setUp() {
        hapiOpPermissions = mock(HapiOpPermissions.class);
        accountID = AccountID.newBuilder().build();
        hapiFunction = CONSENSUS_CREATE_TOPIC;
    }

    @Test
    @DisplayName("Account ID is null throws")
    void accountIdIsNullThrows() {
        // given:
        final var authorizer = new AuthorizerImpl(hapiOpPermissions);

        // expect:
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> authorizer.isAuthorized(null, hapiFunction)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Hapi function is null throws")
    void hapiFunctionIsNullThrows() {
        // given:
        final var authorizer = new AuthorizerImpl(hapiOpPermissions);

        // expect:
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> authorizer.isAuthorized(accountID, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Account is not permitted")
    void accountIsNotPermitted() {
        // given:
        final var authorizer = new AuthorizerImpl(hapiOpPermissions);
        given(hapiOpPermissions.permissibilityOf2(any(), any())).willReturn(AUTHORIZATION_FAILED);

        // expect:
        final var authorized = authorizer.isAuthorized(accountID, hapiFunction);
        assertThat(authorized).isFalse();
    }

    @Test
    @DisplayName("Account is permitted")
    void accountIsPermitted() {
        // given:
        final var authorizer = new AuthorizerImpl(hapiOpPermissions);
        given(hapiOpPermissions.permissibilityOf2(any(), any())).willReturn(OK);

        // expect:
        final var authorized = authorizer.isAuthorized(accountID, hapiFunction);
        assertThat(authorized).isTrue();
    }
}
