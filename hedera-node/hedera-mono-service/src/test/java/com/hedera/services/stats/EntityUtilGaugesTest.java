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
package com.hedera.services.stats;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hedera.services.state.validation.UsageLimits;
import com.swirlds.common.metrics.DoubleGauge;
import com.swirlds.common.system.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EntityUtilGaugesTest {

    @Mock private UsageLimits usageLimits;
    @Mock private Platform platform;
    @Mock private DoubleGauge pretendGauge;

    private EntityUtilGauges subject;

    @BeforeEach
    void setUp() {
        subject = new EntityUtilGauges(usageLimits);
    }

    @Test
    void registersAndUpdatesExpectedGauges() {
        given(platform.getOrCreateMetric(any())).willReturn(pretendGauge);
        given(usageLimits.percentAccountsUsed()).willReturn(2.0);
        given(usageLimits.percentContractsUsed()).willReturn(3.0);
        given(usageLimits.percentFilesUsed()).willReturn(4.0);
        given(usageLimits.percentNftsUsed()).willReturn(5.0);
        given(usageLimits.percentTokensUsed()).willReturn(6.0);
        given(usageLimits.percentTopicsUsed()).willReturn(7.0);
        given(usageLimits.percentStorageSlotsUsed()).willReturn(8.0);
        given(usageLimits.percentTokenRelsUsed()).willReturn(9.0);
        given(usageLimits.percentSchedulesUsed()).willReturn(10.0);

        subject.registerWith(platform);
        subject.updateAll();

        verify(platform, times(9)).getOrCreateMetric(any(DoubleGauge.Config.class));
    }
}
