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

package com.hedera.node.app.service.token.impl.test.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;

import com.hedera.node.app.service.token.impl.validators.ExpiryValidator;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ExpiryValidatorTest {

    @Test
    void expiryStatus_balanceGreaterThan0() {
        final var result = ExpiryValidator.getAccountOrContractExpiryStatus(10, false, false, false, false);
        Assertions.assertThat(result).isEqualTo(OK);
    }

    @Test
    void expiryStatus_isNotExpired() {
        final var result = ExpiryValidator.getAccountOrContractExpiryStatus(0, false, false, false, false);
        Assertions.assertThat(result).isEqualTo(OK);
    }

    @Test
    void expiryStatus_isContractAndExpiryDisabled() {
        final var result = ExpiryValidator.getAccountOrContractExpiryStatus(0, true, false, false, false);
        Assertions.assertThat(result).isEqualTo(OK);
    }

    @Test
    void expiryStatus_isContractAndExpiryEnabled() {
        final var result = ExpiryValidator.getAccountOrContractExpiryStatus(0, true, true, true, false);
        Assertions.assertThat(result).isEqualTo(CONTRACT_EXPIRED_AND_PENDING_REMOVAL);
    }

    @Test
    void expiryStatus_isAccountAndExpiryDisabled() {
        final var result = ExpiryValidator.getAccountOrContractExpiryStatus(0, true, false, false, false);
        Assertions.assertThat(result).isEqualTo(OK);
    }

    @Test
    void expiryStatus_isAccountAndExpiryEnabled() {
        final var result = ExpiryValidator.getAccountOrContractExpiryStatus(0, true, false, false, true);
        Assertions.assertThat(result).isEqualTo(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
    }

    @Test
    void expiryStatus_isAccountAndExpiryEnabledAndAccountExpired() {
        final var result = ExpiryValidator.getAccountOrContractExpiryStatus(0, true, false, true, true);
        Assertions.assertThat(result).isEqualTo(ACCOUNT_EXPIRED_AND_PENDING_REMOVAL);
    }

    @Test
    void expiryStatus_isContractAndExpiryEnabledAndContractExpired() {
        final var result = ExpiryValidator.getAccountOrContractExpiryStatus(0, true, true, true, true);
        Assertions.assertThat(result).isEqualTo(CONTRACT_EXPIRED_AND_PENDING_REMOVAL);
    }
}
