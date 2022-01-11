package com.hedera.services.sigs.order;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.ServicesState;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.StateChildren;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.sigs.ExpansionHelper;
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.state.StateAccessor;
import com.hedera.services.state.migration.StateVersions;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.swirlds.common.AutoCloseableWrapper;
import com.swirlds.common.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.services.sigs.order.SigReqsManager.TOKEN_META_TRANSFORM;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@ExtendWith(MockitoExtension.class)
class SigReqsManagerTest {
	@Mock
	private Platform platform;
	@Mock
	private FileNumbers fileNumbers;
	@Mock
	private AliasManager aliasManager;
	@Mock
	private StateAccessor workingState;
	@Mock
	private SignatureWaivers signatureWaivers;
	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private SigMetadataLookup lookup;
	@Mock
	private SigRequirements workingStateSigReqs;
	@Mock
	private SigRequirements signedStateSigReqs;
	@Mock
	private SigReqsManager.SigReqsFactory sigReqsFactory;
	@Mock
	private SigReqsManager.StateChildrenLookupsFactory lookupsFactory;
	@Mock
	private ExpansionHelper expansionHelper;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private PubKeyToSigBytes pubKeyToSigBytes;
	@Mock
	private ServicesState firstSignedState;
	@Mock
	private ServicesState nextSignedState;

	private SigReqsManager subject;

	@BeforeEach
	void setUp() {
		subject = new SigReqsManager(
				platform,
				fileNumbers, aliasManager, expansionHelper,
				signatureWaivers, workingState, dynamicProperties);
		given(accessor.getPkToSigsFn()).willReturn(pubKeyToSigBytes);
	}

	@Test
	void usesWorkingStateLookupIfNoSignedState() {
		given(workingState.children()).willReturn(workingChildren);
		given(lookupsFactory.from(fileNumbers, aliasManager, workingChildren, TOKEN_META_TRANSFORM))
				.willReturn(lookup);
		given(sigReqsFactory.from(lookup, signatureWaivers)).willReturn(workingStateSigReqs);
		given(dynamicProperties.expandSigsFromLastSignedState()).willReturn(true);
		given(platform.getLastCompleteSwirldState())
				.willReturn(new AutoCloseableWrapper<>(null, () -> {
				}));
		subject.setLookupsFactory(lookupsFactory);
		subject.setSigReqsFactory(sigReqsFactory);

		subject.expandSigsInto(accessor);
		subject.expandSigsInto(accessor);

		verify(sigReqsFactory, times(1))
				.from(lookup, signatureWaivers);
		verify(expansionHelper, times(2))
				.expandIn(accessor, workingStateSigReqs, pubKeyToSigBytes);
	}

	@Test
	void usesWorkingStateLookupIfLastHandleTimeIsNull() {
		given(workingState.children()).willReturn(workingChildren);
		given(lookupsFactory.from(fileNumbers, aliasManager, workingChildren, TOKEN_META_TRANSFORM))
				.willReturn(lookup);
		given(sigReqsFactory.from(lookup, signatureWaivers)).willReturn(workingStateSigReqs);
		given(dynamicProperties.expandSigsFromLastSignedState()).willReturn(true);
		given(platform.getLastCompleteSwirldState())
				.willReturn(new AutoCloseableWrapper<>(firstSignedState, () -> {
				}));
		subject.setLookupsFactory(lookupsFactory);
		subject.setSigReqsFactory(sigReqsFactory);

		subject.expandSigsInto(accessor);

		verify(expansionHelper).expandIn(accessor, workingStateSigReqs, pubKeyToSigBytes);
	}

	@Test
	void usesWorkingStateLookupIfStateVersionIsDifferent() {
		given(workingState.children()).willReturn(workingChildren);
		given(lookupsFactory.from(fileNumbers, aliasManager, workingChildren, TOKEN_META_TRANSFORM))
				.willReturn(lookup);
		given(sigReqsFactory.from(lookup, signatureWaivers)).willReturn(workingStateSigReqs);
		given(dynamicProperties.expandSigsFromLastSignedState()).willReturn(true);
		given(platform.getLastCompleteSwirldState())
				.willReturn(new AutoCloseableWrapper<>(firstSignedState, () -> {
				}));
		given(firstSignedState.getTimeOfLastHandledTxn()).willReturn(lastHandleTime);
		subject.setLookupsFactory(lookupsFactory);
		subject.setSigReqsFactory(sigReqsFactory);

		subject.expandSigsInto(accessor);

		verify(expansionHelper).expandIn(accessor, workingStateSigReqs, pubKeyToSigBytes);
	}

	@Test
	void usesWorkingStateLookupIfPropertiesInsist() {
		given(workingState.children()).willReturn(workingChildren);
		given(lookupsFactory.from(fileNumbers, aliasManager, workingChildren, TOKEN_META_TRANSFORM))
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
		given(platform.getLastCompleteSwirldState())
				.willReturn(new AutoCloseableWrapper<>(firstSignedState, () -> {
				}))
				.willReturn(new AutoCloseableWrapper<>(nextSignedState, () -> {
				}));
		given(firstSignedState.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
		given(nextSignedState.getStateVersion()).willReturn(StateVersions.CURRENT_VERSION);
		given(firstSignedState.getTimeOfLastHandledTxn()).willReturn(lastHandleTime);
		given(nextSignedState.getTimeOfLastHandledTxn()).willReturn(nextLastHandleTime);
		given(lookupsFactory.from(
				eq(fileNumbers), eq(aliasManager), captor.capture(), eq(TOKEN_META_TRANSFORM)))
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
	private static final MutableStateChildren workingChildren = new MutableStateChildren();
}
