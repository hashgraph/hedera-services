/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.VersionedConfigImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class AuthorizerTest {
    private ConfigProvider configProvider;

    @Mock
    private PrivilegesVerifier privilegesVerifier;

    private AccountID accountID;
    private HederaFunctionality hapiFunction;

    @BeforeEach
    void setUp() {
        configProvider = () -> new VersionedConfigImpl(HederaTestConfigBuilder.createConfig(), 1);

        accountID = AccountID.newBuilder().build();
        hapiFunction = CONSENSUS_CREATE_TOPIC;
    }

    @Test
    @DisplayName("Account ID is null throws")
    void accountIdIsNullThrows() {
        // given:
        final var authorizer = new AuthorizerImpl(configProvider, privilegesVerifier);

        // expect:
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> authorizer.isAuthorized(null, hapiFunction)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Hapi function is null throws")
    void hapiFunctionIsNullThrows() {
        // given:
        final var authorizer = new AuthorizerImpl(configProvider, privilegesVerifier);

        // expect:
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> authorizer.isAuthorized(accountID, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Account is not permitted")
    void accountIsNotPermitted() {
        // given:
        configProvider = () -> new VersionedConfigImpl(
                HederaTestConfigBuilder.create()
                        .withValue("createTopic", "1-1000")
                        .getOrCreateConfig(),
                1);

        final var authorizer = new AuthorizerImpl(configProvider, privilegesVerifier);
        accountID = AccountID.newBuilder().accountNum(1234L).build();

        // expect:
        final var authorized = authorizer.isAuthorized(accountID, hapiFunction);
        assertThat(authorized).isFalse();
    }

    @Test
    @DisplayName("Account is permitted")
    void accountIsPermitted() {
        // given:
        configProvider = () -> new VersionedConfigImpl(
                HederaTestConfigBuilder.create()
                        .withValue("createTopic", "1-1234")
                        .getOrCreateConfig(),
                1);

        final var authorizer = new AuthorizerImpl(configProvider, privilegesVerifier);
        accountID = AccountID.newBuilder().accountNum(1234L).build();

        // expect:
        final var authorized = authorizer.isAuthorized(accountID, hapiFunction);
        assertThat(authorized).isTrue();
    }
}
