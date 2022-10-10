/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.context.domain.process;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TxnValidityAndFeeReqTest {
    @Test
    void defaultFeeReqIsZero() {
        final var subject = new TxnValidityAndFeeReq(INVALID_ACCOUNT_AMOUNTS);

        assertEquals(INVALID_ACCOUNT_AMOUNTS, subject.getValidity());
        assertEquals(0L, subject.getRequiredFee());
    }

    @Test
    void beanWorks() {
        final var subject = new TxnValidityAndFeeReq(OK, 123L);

        assertEquals(OK, subject.getValidity());
        assertEquals(123L, subject.getRequiredFee());
    }

    @Test
    void toStringWorks() {
        final var subject = new TxnValidityAndFeeReq(OK, 123L);

        assertEquals(
                TxnValidityAndFeeReq.class.getSimpleName()
                        + "{validity="
                        + OK
                        + ", requiredFee="
                        + 123
                        + "}",
                subject.toString());
    }
}
