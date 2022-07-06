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

import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.verify;

import com.swirlds.common.statistics.StatEntry;
import com.swirlds.common.statistics.StatsSpeedometer;
import com.swirlds.common.system.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MiscSpeedometersTest {
    private static final double halfLife = 10.0;

    private Platform platform;
    private SpeedometerFactory factory;

    private MiscSpeedometers subject;

    @BeforeEach
    void setup() {
        factory = mock(SpeedometerFactory.class);
        platform = mock(Platform.class);

        subject = new MiscSpeedometers(factory, halfLife);
    }

    @Test
    void registersExpectedStatEntries() {
        final var sync = mock(StatEntry.class);
        final var rejections = mock(StatEntry.class);
        given(
                        factory.from(
                                argThat(MiscSpeedometers.Names.SYNC_VERIFICATIONS::equals),
                                argThat(MiscSpeedometers.Descriptions.SYNC_VERIFICATIONS::equals),
                                any()))
                .willReturn(sync);
        given(
                        factory.from(
                                argThat(MiscSpeedometers.Names.PLATFORM_TXN_REJECTIONS::equals),
                                argThat(
                                        MiscSpeedometers.Descriptions.PLATFORM_TXN_REJECTIONS
                                                ::equals),
                                any()))
                .willReturn(rejections);

        subject.registerWith(platform);

        verify(platform).addAppStatEntry(sync);
        verify(platform).addAppStatEntry(rejections);
    }

    @Test
    void cyclesExpectedSpeedometers() {
        final var sync = mock(StatsSpeedometer.class);
        final var rejections = mock(StatsSpeedometer.class);
        subject.syncVerifications = sync;
        subject.platformTxnRejections = rejections;

        subject.cycleSyncVerifications();
        subject.cyclePlatformTxnRejections();

        verify(rejections).update(1.0);
        verify(sync).update(1.0);
    }
}
