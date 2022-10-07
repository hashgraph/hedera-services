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
package com.hedera.services.files;

import static org.mockito.Mockito.inOrder;

import com.hedera.services.files.sysfiles.ConfigCallbacks;
import com.hedera.services.files.sysfiles.CurrencyCallbacks;
import com.hedera.services.files.sysfiles.ThrottlesCallback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SysFileCallbacksTest {
    @Mock ConfigCallbacks configCallbacks;
    @Mock ThrottlesCallback throttlesCallback;
    @Mock CurrencyCallbacks currencyCallbacks;

    SysFileCallbacks subject;

    @BeforeEach
    void setUp() {
        subject = new SysFileCallbacks(configCallbacks, throttlesCallback, currencyCallbacks);
    }

    @Test
    void delegatesAsExpected() {
        var inOrder = inOrder(configCallbacks, throttlesCallback, currencyCallbacks);

        // when:
        subject.permissionsCb();
        subject.propertiesCb();
        subject.throttlesCb();
        subject.exchangeRatesCb();
        subject.feeSchedulesCb();

        // verify:
        inOrder.verify(configCallbacks).permissionsCb();
        inOrder.verify(configCallbacks).propertiesCb();
        inOrder.verify(throttlesCallback).throttlesCb();
        inOrder.verify(currencyCallbacks).exchangeRatesCb();
        inOrder.verify(currencyCallbacks).feeSchedulesCb();
    }
}
