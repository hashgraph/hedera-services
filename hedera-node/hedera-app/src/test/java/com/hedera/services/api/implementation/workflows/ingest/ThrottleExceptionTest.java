/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.api.implementation.workflows.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.Test;

class ThrottleExceptionTest {

    @Test
    void testConstructor() {
        // given
        final String msg = "Hello World";

        // when
        final ThrottleException exception = new ThrottleException(msg);

        // then
        assertThat(exception.responseCode()).isEqualTo(ResponseCodeEnum.BUSY);
        assertThat(exception.getMessage()).isEqualTo(msg);
    }

    @Test
    void testConstructorWithNullMessage() {
        // when
        final ThrottleException exception = new ThrottleException(null);

        // then
        assertThat(exception.responseCode()).isEqualTo(ResponseCodeEnum.BUSY);
        assertThat(exception.getMessage()).isNull();
    }
}
