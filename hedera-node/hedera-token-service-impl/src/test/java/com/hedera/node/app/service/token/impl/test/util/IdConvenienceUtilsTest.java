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

package com.hedera.node.app.service.token.impl.test.util;

import static com.hedera.node.app.service.token.impl.util.IdConvenienceUtils.fromAccountNum;
import static com.hedera.node.app.service.token.impl.util.IdConvenienceUtils.fromTokenNum;
import static com.hedera.node.app.service.token.impl.util.IdConvenienceUtils.isValidAccountNum;
import static com.hedera.node.app.service.token.impl.util.IdConvenienceUtils.isValidTokenNum;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class IdConvenienceUtilsTest {

    @Test
    void fromAccountNum_invalidAccountNumThrowsException() {
        Assertions.assertThatThrownBy(() -> fromAccountNum(-1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromAccountNum_validAccountNumReturnsAccountId() {
        final var result = fromAccountNum(1L);
        Assertions.assertThat(fromAccountNum(1L))
                .isEqualTo(AccountID.newBuilder().accountNum(1L).build());
    }

    @Test
    void fromTokenNum_invalidTokenNumThrowsException() {
        Assertions.assertThatThrownBy(() -> fromTokenNum(-1L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fromTokenNum_validTokenNumReturnsTokenId() {
        Assertions.assertThat(fromTokenNum(1L))
                .isEqualTo(TokenID.newBuilder().tokenNum(1L).build());
    }

    @Test
    void isValidTokenNum_invalidTokenNum() {
        Assertions.assertThat(isValidTokenNum(-1L)).isFalse();
        Assertions.assertThat(isValidTokenNum(0L)).isFalse();
    }

    @Test
    void isValidTokenNum_validTokenNum() {
        Assertions.assertThat(isValidTokenNum(1L)).isTrue();
    }

    @Test
    void isValidAccountNum_invalidAccountNum() {
        Assertions.assertThat(isValidAccountNum(-1L)).isFalse();
        Assertions.assertThat(isValidAccountNum(0L)).isFalse();
    }

    @Test
    void isValidAccountNum_validAccountNum() {
        Assertions.assertThat(isValidAccountNum(1L)).isTrue();
    }
}
