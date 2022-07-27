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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.hedera.services.ServicesState;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.config.NetworkInfo;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.StateChildren;
import com.hedera.services.context.primitives.SignedStateViewFactory;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.sigs.ExpansionHelper;
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.state.migration.StateVersions;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.swirlds.common.system.Platform;
import com.swirlds.common.utility.AutoCloseableWrapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SigReqsManagerTest {
    @Mock private Platform platform;
    @Mock private FileNumbers fileNumbers;
    @Mock private SignatureWaivers signatureWaivers;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private SigMetadataLookup lookup;
    @Mock private SigRequirements workingStateSigReqs;
    @Mock private SigRequirements signedStateSigReqs;
    @Mock private SigReqsManager.SigReqsFactory sigReqsFactory;
    @Mock private SigReqsManager.StateChildrenLookupsFactory lookupsFactory;
    @Mock private ExpansionHelper expansionHelper;
    @Mock private PlatformTxnAccessor accessor;
    @Mock private PubKeyToSigBytes pubKeyToSigBytes;
    @Mock private ServicesState firstSignedState;
    @Mock private ServicesState nextSignedState;
    @Mock private ScheduleStore scheduleStore;
    @Mock private NetworkInfo networkInfo;

    private SignedStateViewFactory stateViewFactory;

    private SigReqsManager subject;

    @BeforeEach
    void setUp() {
        stateViewFactory = new SignedStateViewFactory(platform, scheduleStore, networkInfo);
        subject =
                new SigReqsManager(
                        fileNumbers,
                        expansionHelper,
                        signatureWaivers,
                        workingState,
                        dynamicProperties,
                        stateViewFactory);
        given(accessor.getPkToSigsFn()).willReturn(pubKeyToSigBytes);
    }

    @Test
    void usesWorkingStateLookupIfNoSignedState() {
        given(lookupsFactory.from(fileNumbers, workingState, TOKEN_META_TRANSFORM))
                .willReturn(lookup);
        given(sigReqsFactory.from(lookup, signatureWaivers)).willReturn(workingStateSigReqs);
        given(dynamicProperties.expandSigsFromLastSignedState()).willReturn(true);
        given(platform.getLastCompleteSwirldState())
                .willReturn(new AutoCloseableWrapper<>(null, () -> {}));
        subject.setLookupsFactory(lookupsFactory);
        subject.setSigReqsFactory(sigReqsFactory);

        subject.expandSigsInto(accessor);
        subject.expandSigsInto(accessor);

        verify(sigReqsFactory, times(1)).from(lookup, signatureWaivers);
        verify(expansionHelper, times(2)).expandIn(accessor, workingStateSigReqs, pubKeyToSigBytes);
    }

    @Test
    void usesWorkingStateLookupIfLastHandleTimeIsNull() {
        given(lookupsFactory.from(fileNumbers, workingState, TOKEN_META_TRANSFORM))
                .willReturn(lookup);
        given(sigReqsFactory.from(lookup, signatureWaivers)).willReturn(workingStateSigReqs);
        given(dynamicProperties.expandSigsFromLastSignedState()).willReturn(true);
        given(platform.getLastCompleteSwirldState())
                .willReturn(new AutoCloseableWrapper<>(firstSignedState, () -> {}));
        subject.setLookupsFactory(lookupsFactory);
        subject.setSigReqsFactory(sigReqsFactory);

        subject.expandSigsInto(accessor);

        verify(expansionHelper).expandIn(accessor, workingStateSigReqs, pubKeyToSigBytes);
    }

    @Test
    void usesWorkingStateLookupIfStateVersionIsDifferent() {
        given(lookupsFactory.from(fileNumbers, workingState, TOKEN_META_TRANSFORM))
                .willReturn(lookup);
        given(sigReqsFactory.from(lookup, signatureWaivers)).willReturn(workingStateSigReqs);
        given(dynamicProperties.expandSigsFromLastSignedState()).willReturn(true);
        given(platform.getLastCompleteSwirldState())
                .willReturn(new AutoCloseableWrapper<>(firstSignedState, () -> {}));
        given(firstSignedState.getTimeOfLastHandledTxn()).willReturn(lastHandleTime);
        subject.setLookupsFactory(lookupsFactory);
        subject.setSigReqsFactory(sigReqsFactory);

        subject.expandSigsInto(accessor);

        verify(expansionHelper).expandIn(accessor, workingStateSigReqs, pubKeyToSigBytes);
    }

    @Test
    void usesWorkingStateLookupIfStateIsUninitialized() {
        given(lookupsFactory.from(fileNumbers, workingState, TOKEN_META_TRANSFORM))
                .willReturn(lookup);
        given(sigReqsFactory.from(lookup, signatureWaivers)).willReturn(workingStateSigReqs);
        given(dynamicProperties.expandSigsFromLastSignedState()).willReturn(true);
        given(platform.getLastCompleteSwirldState())
                .willReturn(new AutoCloseableWrapper<>(firstSignedState, () -> {}));
        given(firstSignedState.getTimeOfLastHandledTxn()).willReturn(lastHandleTime);
        given(firstSignedState.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        subject.setLookupsFactory(lookupsFactory);
        subject.setSigReqsFactory(sigReqsFactory);

        subject.expandSigsInto(accessor);

        verify(expansionHelper).expandIn(accessor, workingStateSigReqs, pubKeyToSigBytes);
    }

    @Test
    void usesWorkingStateLookupIfPropertiesInsist() {
        given(lookupsFactory.from(fileNumbers, workingState, TOKEN_META_TRANSFORM))
                .willReturn(lookup);
        given(sigReqsFactory.from(lookup, signatureWaivers)).willReturn(workingStateSigReqs);
        subject.setLookupsFactory(lookupsFactory);
        subject.setSigReqsFactory(sigReqsFactory);

        subject.expandSigsInto(accessor);

        verify(expansionHelper).expandIn(accessor, workingStateSigReqs, pubKeyToSigBytes);
    }

    @Test
    void usesLatestSignedStateChildrenIfChanged() {
        final ArgumentCaptor<StateChildren> captor = ArgumentCaptor.forClass(StateChildren.class);

        given(dynamicProperties.expandSigsFromLastSignedState()).willReturn(true);
        given(firstSignedState.isInitialized()).willReturn(true);
        given(nextSignedState.isInitialized()).willReturn(true);
        given(platform.getLastCompleteSwirldState())
                .willReturn(new AutoCloseableWrapper<>(firstSignedState, () -> {}))
                .willReturn(new AutoCloseableWrapper<>(nextSignedState, () -> {}));
        given(firstSignedState.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        given(nextSignedState.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
        given(firstSignedState.getTimeOfLastHandledTxn()).willReturn(lastHandleTime);
        given(nextSignedState.getTimeOfLastHandledTxn()).willReturn(nextLastHandleTime);
        given(lookupsFactory.from(eq(fileNumbers), captor.capture(), eq(TOKEN_META_TRANSFORM)))
                .willReturn(lookup);
        given(sigReqsFactory.from(lookup, signatureWaivers)).willReturn(signedStateSigReqs);
        subject.setLookupsFactory(lookupsFactory);
        subject.setSigReqsFactory(sigReqsFactory);

        subject.expandSigsInto(accessor);
        subject.expandSigsInto(accessor);
        subject.expandSigsInto(accessor);

        verify(sigReqsFactory).from(lookup, signatureWaivers);
        verify(expansionHelper, times(3)).expandIn(accessor, signedStateSigReqs, pubKeyToSigBytes);
        final var capturedStateChildren = captor.getValue();
        assertSame(nextLastHandleTime, capturedStateChildren.signedAt());
    }

    private static final Instant lastHandleTime = Instant.ofEpochSecond(1_234_567, 890);
    private static final Instant nextLastHandleTime = lastHandleTime.plusSeconds(2);
    private static final MutableStateChildren workingState = new MutableStateChildren();
}
