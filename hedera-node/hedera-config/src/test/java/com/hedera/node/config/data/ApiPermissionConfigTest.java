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

package com.hedera.node.config.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.node.config.types.PermissionedAccountsRange;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;

final class ApiPermissionConfigTest {

    @ParameterizedTest
    @EnumSource(
            value = HederaFunctionality.class,
            mode = EnumSource.Mode.EXCLUDE,
            names = {
                "NONE",
                "CRYPTO_ADD_LIVE_HASH",
                "CRYPTO_DELETE_LIVE_HASH",
                "GET_BY_SOLIDITY_ID",
                "GET_BY_KEY",
                "CRYPTO_GET_LIVE_HASH",
                "CRYPTO_GET_STAKERS",
                "CREATE_TRANSACTION_RECORD",
                "CRYPTO_ACCOUNT_AUTO_RENEW",
                "CONTRACT_AUTO_RENEW",
                "UNCHECKED_SUBMIT",
                "NODE_STAKE_UPDATE"
            })
    void testHederaFunctionalityUsage(final HederaFunctionality hederaFunctionality) {
        // given
        final var configuration = HederaTestConfigBuilder.create()
                .withConfigDataType(ApiPermissionConfig.class)
                .getOrCreateConfig();
        final ApiPermissionConfig config = configuration.getConfigData(ApiPermissionConfig.class);

        // when
        PermissionedAccountsRange permission = config.getPermission(hederaFunctionality);

        // then
        assertThat(permission).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(
            value = HederaFunctionality.class,
            mode = Mode.INCLUDE,
            names = {
                "NONE",
                "CRYPTO_ADD_LIVE_HASH",
                "CRYPTO_DELETE_LIVE_HASH",
                "GET_BY_SOLIDITY_ID",
                "GET_BY_KEY",
                "CRYPTO_GET_LIVE_HASH",
                "CRYPTO_GET_STAKERS",
                "CREATE_TRANSACTION_RECORD",
                "CRYPTO_ACCOUNT_AUTO_RENEW",
                "CONTRACT_AUTO_RENEW",
                "UNCHECKED_SUBMIT",
                "NODE_STAKE_UPDATE"
            })
    void testNotSupportedHederaFunctionalityUsage(final HederaFunctionality hederaFunctionality) {
        // given
        final var configuration = HederaTestConfigBuilder.create()
                .withConfigDataType(ApiPermissionConfig.class)
                .getOrCreateConfig();
        final ApiPermissionConfig config = configuration.getConfigData(ApiPermissionConfig.class);

        // then
        assertThatThrownBy(() -> config.getPermission(hederaFunctionality))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
