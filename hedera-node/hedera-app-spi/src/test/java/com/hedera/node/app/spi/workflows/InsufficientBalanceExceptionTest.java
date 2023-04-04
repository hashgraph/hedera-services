/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.workflows;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import org.junit.jupiter.api.Test;

class InsufficientBalanceExceptionTest {

    @Test
    void testConstructor() {
        // when
        final InsufficientBalanceException exception =
                new InsufficientBalanceException(ResponseCodeEnum.UNAUTHORIZED, 42L);

        // then
        assertThat(exception.responseCode()).isEqualTo(ResponseCodeEnum.UNAUTHORIZED);
        assertThat(exception.getEstimatedFee()).isEqualTo(42L);
        assertThat(exception.getMessage()).isNull();
    }

    @SuppressWarnings({"ThrowableNotThrown", "ConstantConditions"})
    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new InsufficientBalanceException(null, 42L)).isInstanceOf(NullPointerException.class);
    }
}
