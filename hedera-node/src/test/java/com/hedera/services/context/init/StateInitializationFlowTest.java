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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleScheduledTransactions;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.stream.RecordStreamManager;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
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
    @Mock private MerkleMap<EntityNum, MerkleAccount> accounts;
    @Mock private MerkleMap<EntityNum, MerkleTopic> topics;
    @Mock private MerkleMap<EntityNum, MerkleToken> tokens;
    @Mock private MerkleMap<EntityNumPair, MerkleUniqueToken> uniqueTokens;
    @Mock private MerkleScheduledTransactions schedules;
    @Mock private VirtualMap<VirtualBlobKey, VirtualBlobValue> storage;
    @Mock private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenAssociations;
    @Mock private VirtualMap<ContractKey, IterableContractValue> contractStorage;

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

    private void givenMockMerkleMaps() {
        given(activeState.accounts()).willReturn(accounts);
        given(activeState.uniqueTokens()).willReturn(uniqueTokens);
        given(activeState.tokenAssociations()).willReturn(tokenAssociations);
        given(activeState.topics()).willReturn(topics);
        given(activeState.tokens()).willReturn(tokens);
        given(activeState.scheduleTxs()).willReturn(schedules);
        given(activeState.storage()).willReturn(storage);
        given(activeState.contractStorage()).willReturn(contractStorage);
    }

    private void setupMockNumInitialization() {
        StateInitializationFlow.setNumberConfigurer(numberConfigurer);
    }

    private void cleanupMockNumInitialization() {
        StateInitializationFlow.setNumberConfigurer(STATIC_PROPERTIES::configureNumbers);
    }
}
