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
package com.hedera.services.context.init;

import static com.hedera.services.context.properties.PropertyNames.ACCOUNTS_LAST_THROTTLE_EXEMPT;
import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hedera.services.ServicesState;
import com.hedera.services.config.HederaNumbers;
import com.hedera.services.config.MockHederaNumbers;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.files.FileUpdateInterceptor;
import com.hedera.services.files.HederaFs;
import com.hedera.services.stream.RecordStreamManager;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StateInitializationFlowTest {
    private final HederaNumbers defaultNumbers = new MockHederaNumbers();

    @Mock private Hash hash;
    @Mock private HederaFs hfs;
    @Mock private RunningHash runningHash;
    @Mock private ServicesState activeState;
    @Mock private BootstrapProperties bootstrapProperties;
    @Mock private RecordsRunningHashLeaf runningHashLeaf;
    @Mock private MutableStateChildren workingState;
    @Mock private RecordStreamManager recordStreamManager;
    @Mock private FileUpdateInterceptor aFileInterceptor;
    @Mock private FileUpdateInterceptor bFileInterceptor;
    @Mock private StateInitializationFlow.NumberConfigurer numberConfigurer;

    private StateInitializationFlow subject;

    @BeforeEach
    void setUp() {
        subject =
                new StateInitializationFlow(
                        hfs,
                        defaultNumbers,
                        recordStreamManager,
                        workingState,
                        Set.of(aFileInterceptor, bFileInterceptor));
    }

    @Test
    void performsAsExpectedWithNoInterceptorsRegistered() {
        setupMockNumInitialization();

        given(runningHash.getHash()).willReturn(hash);
        given(runningHashLeaf.getRunningHash()).willReturn(runningHash);
        given(activeState.runningHashLeaf()).willReturn(runningHashLeaf);
        given(bootstrapProperties.getLongProperty(ACCOUNTS_LAST_THROTTLE_EXEMPT)).willReturn(100L);

        // when:
        subject.runWith(activeState, bootstrapProperties);

        // then:
        verify(numberConfigurer).configureNumbers(defaultNumbers, 100L);
        verify(workingState).updateFrom(activeState);
        verify(recordStreamManager).setInitialHash(hash);
        verify(hfs).register(aFileInterceptor);
        verify(hfs).register(bFileInterceptor);

        cleanupMockNumInitialization();
    }

    @Test
    void performsAsExpectedWithInterceptorsRegistered() {
        setupMockNumInitialization();

        given(runningHash.getHash()).willReturn(hash);
        given(runningHashLeaf.getRunningHash()).willReturn(runningHash);
        given(activeState.runningHashLeaf()).willReturn(runningHashLeaf);
        given(hfs.numRegisteredInterceptors()).willReturn(5);
        given(bootstrapProperties.getLongProperty(ACCOUNTS_LAST_THROTTLE_EXEMPT)).willReturn(100L);

        // when:
        subject.runWith(activeState, bootstrapProperties);

        // then:
        verify(workingState).updateFrom(activeState);
        verify(recordStreamManager).setInitialHash(hash);
        verify(hfs, never()).register(any());
        verify(numberConfigurer).configureNumbers(defaultNumbers, 100L);

        cleanupMockNumInitialization();
    }

    private void setupMockNumInitialization() {
        StateInitializationFlow.setNumberConfigurer(numberConfigurer);
    }

    private void cleanupMockNumInitialization() {
        StateInitializationFlow.setNumberConfigurer(STATIC_PROPERTIES::configureNumbers);
    }
}
