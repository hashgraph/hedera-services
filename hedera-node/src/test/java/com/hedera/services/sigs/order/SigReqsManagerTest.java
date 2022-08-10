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
package com.hedera.services.sigs.order;

import static com.hedera.services.sigs.order.SigReqsManager.TOKEN_META_TRANSFORM;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

import com.hedera.services.ServicesState;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.sigs.ExpansionHelper;
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.state.migration.StateVersions;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SigReqsManagerTest {
    @Mock private FileNumbers fileNumbers;
    @Mock private SignatureWaivers signatureWaivers;
    @Mock private ServicesState sourceState;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private SigMetadataLookup workingStateLookup;
    @Mock private SigMetadataLookup immutableStateLookup;
    @Mock private SigRequirements workingStateSigReqs;
    @Mock private SigRequirements immutableStateSigReqs;
    @Mock private SigReqsManager.SigReqsFactory sigReqsFactory;
    @Mock private SigReqsManager.StateChildrenLookupsFactory lookupsFactory;
    @Mock private ExpansionHelper expansionHelper;
    @Mock private PlatformTxnAccessor accessor;
    @Mock private PubKeyToSigBytes pubKeyToSigBytes;

    private SigReqsManager subject;

    @BeforeEach
    void setUp() {
        subject =
                new SigReqsManager(
                        fileNumbers,
                        expansionHelper,
                        signatureWaivers,
                        workingState,
                        dynamicProperties);
        given(accessor.getPkToSigsFn()).willReturn(pubKeyToSigBytes);
    }

    @Test
    void usesWorkingStateLookupIfLastHandleTimeIsNull() {
        given(lookupsFactory.from(fileNumbers, workingState, TOKEN_META_TRANSFORM))
                .willReturn(workingStateLookup);
        given(sigReqsFactory.from(workingStateLookup, signatureWaivers))
                .willReturn(workingStateSigReqs);
        given(dynamicProperties.expandSigsFromImmutableState()).willReturn(true);
        subject.setLookupsFactory(lookupsFactory);
        subject.setSigReqsFactory(sigReqsFactory);

        subject.expandSigs(sourceState, accessor);

        verify(expansionHelper).expandIn(accessor, workingStateSigReqs, pubKeyToSigBytes);
    }

    @Test
    void usesWorkingStateLookupIfStateVersionIsDifferent() {
        given(lookupsFactory.from(fileNumbers, workingState, TOKEN_META_TRANSFORM))
                .willReturn(workingStateLookup);
        given(sigReqsFactory.from(workingStateLookup, signatureWaivers))
                .willReturn(workingStateSigReqs);
        given(dynamicProperties.expandSigsFromImmutableState()).willReturn(true);
        given(sourceState.getTimeOfLastHandledTxn()).willReturn(lastHandleTime);
        subject.setLookupsFactory(lookupsFactory);
        subject.setSigReqsFactory(sigReqsFactory);

        subject.expandSigs(sourceState, accessor);

        verify(expansionHelper).expandIn(accessor, workingStateSigReqs, pubKeyToSigBytes);
    }

    @Test
    void usesWorkingStateLookupIfStateIsUninitialized() {
        given(lookupsFactory.from(fileNumbers, workingState, TOKEN_META_TRANSFORM))
                .willReturn(workingStateLookup);
        given(sigReqsFactory.from(workingStateLookup, signatureWaivers))
                .willReturn(workingStateSigReqs);
        given(dynamicProperties.expandSigsFromImmutableState()).willReturn(true);
        given(sourceState.getTimeOfLastHandledTxn()).willReturn(lastHandleTime);
        given(sourceState.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        subject.setLookupsFactory(lookupsFactory);
        subject.setSigReqsFactory(sigReqsFactory);

        subject.expandSigs(sourceState, accessor);

        verify(expansionHelper).expandIn(accessor, workingStateSigReqs, pubKeyToSigBytes);
    }

    @Test
    void usesWorkingStateLookupIfImmutableStateExpansionFailsUnexpectedly() {
        given(lookupsFactory.from(fileNumbers, workingState, TOKEN_META_TRANSFORM))
                .willReturn(workingStateLookup);
        given(
                        lookupsFactory.from(
                                fileNumbers, subject.getImmutableChildren(), TOKEN_META_TRANSFORM))
                .willReturn(immutableStateLookup);
        given(sigReqsFactory.from(workingStateLookup, signatureWaivers))
                .willReturn(workingStateSigReqs);
        given(sigReqsFactory.from(immutableStateLookup, signatureWaivers))
                .willReturn(immutableStateSigReqs);
        given(dynamicProperties.expandSigsFromImmutableState()).willReturn(true);
        given(sourceState.getTimeOfLastHandledTxn()).willReturn(lastHandleTime);
        given(sourceState.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        given(sourceState.isInitialized()).willReturn(true);
        // and:
        final var shouldThrow = new AtomicBoolean(true);
        willAnswer(
                        invocationOnMock -> {
                            if (shouldThrow.get()) {
                                shouldThrow.set(false);
                                throw new IllegalStateException();
                            }
                            return null;
                        })
                .given(expansionHelper)
                .expandIn(any(), any(), any());
        subject.setLookupsFactory(lookupsFactory);
        subject.setSigReqsFactory(sigReqsFactory);

        subject.expandSigs(sourceState, accessor);

        verify(expansionHelper).expandIn(accessor, workingStateSigReqs, pubKeyToSigBytes);
    }

    @Test
    void usesImmutableStateLookupIfEverythingIsSane() {
        given(
                        lookupsFactory.from(
                                fileNumbers, subject.getImmutableChildren(), TOKEN_META_TRANSFORM))
                .willReturn(immutableStateLookup);
        given(sigReqsFactory.from(immutableStateLookup, signatureWaivers))
                .willReturn(immutableStateSigReqs);
        given(dynamicProperties.expandSigsFromImmutableState()).willReturn(true);
        given(sourceState.getTimeOfLastHandledTxn()).willReturn(lastHandleTime);
        given(sourceState.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        given(sourceState.isInitialized()).willReturn(true);
        // and:
        subject.setLookupsFactory(lookupsFactory);
        subject.setSigReqsFactory(sigReqsFactory);

        subject.expandSigs(sourceState, accessor);

        verify(expansionHelper).expandIn(accessor, immutableStateSigReqs, pubKeyToSigBytes);
    }

    @Test
    void usesWorkingStateLookupIfPropertiesInsist() {
        given(lookupsFactory.from(fileNumbers, workingState, TOKEN_META_TRANSFORM))
                .willReturn(workingStateLookup);
        given(sigReqsFactory.from(workingStateLookup, signatureWaivers))
                .willReturn(workingStateSigReqs);
        subject.setLookupsFactory(lookupsFactory);
        subject.setSigReqsFactory(sigReqsFactory);

        subject.expandSigs(sourceState, accessor);

        verify(expansionHelper).expandIn(accessor, workingStateSigReqs, pubKeyToSigBytes);
    }

    private static final Instant lastHandleTime = Instant.ofEpochSecond(1_234_567, 890);
    private static final Instant nextLastHandleTime = lastHandleTime.plusSeconds(2);
    private static final MutableStateChildren workingState = new MutableStateChildren();
}
