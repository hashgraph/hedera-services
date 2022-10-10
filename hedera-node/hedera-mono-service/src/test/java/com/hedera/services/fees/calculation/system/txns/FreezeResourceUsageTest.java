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
package com.hedera.services.fees.calculation.system.txns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FreezeResourceUsageTest {
    private FreezeResourceUsage subject;

    @BeforeEach
    void setup() {
        subject = new FreezeResourceUsage();
    }

    @Test
    void recognizesApplicability() {
        final var txn = mock(TransactionBody.class);
        given(txn.hasFreeze()).willReturn(true, false);

        assertTrue(subject.applicableTo(txn));
        assertFalse(subject.applicableTo(txn));
        verify(txn, times(2)).hasFreeze();
    }

    @Test
    void delegatesToCorrectEstimate() throws InvalidTxBodyException {
        assertEquals(FeeData.getDefaultInstance(), subject.usageGiven(null, null, null));
    }
}
