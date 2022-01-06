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

import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.sigs.metadata.SigMetadataLookup;
import com.hedera.services.state.StateAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hedera.services.sigs.order.SignedStateSigReqs.TOKEN_META_TRANSFORM;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

@ExtendWith(MockitoExtension.class)
class SignedStateSigReqsTest {
	@Mock
	private FileNumbers fileNumbers;
	@Mock
	private AliasManager aliasManager;
	@Mock
	private StateAccessor workingState;
	@Mock
	private StateAccessor latestSignedState;
	@Mock
	private SignatureWaivers signatureWaivers;
	@Mock
	private GlobalDynamicProperties properties;
	@Mock
	private SigMetadataLookup sigMetadataLookup;
	@Mock
	private SigRequirements sigRequirements;
	@Mock
	private SigRequirements otherSigRequirements;
	@Mock
	private SignedStateSigReqs.SigReqsFactory sigReqsFactory;
	@Mock
	private SignedStateSigReqs.StateChildrenLookupsFactory lookupsFactory;

	private SignedStateSigReqs subject;

	@BeforeEach
	void setUp() {
		subject = new SignedStateSigReqs(
				fileNumbers, aliasManager, signatureWaivers, workingState, latestSignedState);
	}

	@Test
	void usesWorkingStateLookupIfNoSignedState() {
		given(latestSignedState.children()).willReturn(unsignedChildren);
		given(workingState.children()).willReturn(workingChildren);
		given(lookupsFactory.from(fileNumbers, aliasManager, workingChildren, TOKEN_META_TRANSFORM))
				.willReturn(sigMetadataLookup);
		given(sigReqsFactory.from(sigMetadataLookup, signatureWaivers)).willReturn(sigRequirements);
		subject.setLookupsFactory(lookupsFactory);
		subject.setSigReqsFactory(sigReqsFactory);

		final var firstAns = subject.getBestAvailable();
		final var secondAns = subject.getBestAvailable();

		assertSame(sigRequirements, firstAns);
		assertSame(sigRequirements, secondAns);
		verify(sigReqsFactory, times(1))
				.from(sigMetadataLookup, signatureWaivers);
	}

	@Test
	void usesLatestSignedStateChildrenIfChanged() {
		given(latestSignedState.children()).willReturn(firstSignedChildren);
		given(lookupsFactory.from(fileNumbers, aliasManager, firstSignedChildren, TOKEN_META_TRANSFORM))
				.willReturn(sigMetadataLookup);
		given(sigReqsFactory.from(sigMetadataLookup, signatureWaivers))
				.willReturn(sigRequirements);
		subject.setLookupsFactory(lookupsFactory);
		subject.setSigReqsFactory(sigReqsFactory);

		final var firstAns = subject.getBestAvailable();
		final var secondAns = subject.getBestAvailable();
		assertSame(sigRequirements, firstAns);
		assertSame(sigRequirements, secondAns);

		given(latestSignedState.children()).willReturn(secondSignedChildren);
		given(lookupsFactory.from(fileNumbers, aliasManager, secondSignedChildren, TOKEN_META_TRANSFORM))
				.willReturn(sigMetadataLookup);
		given(sigReqsFactory.from(sigMetadataLookup, signatureWaivers))
				.willReturn(otherSigRequirements);
		final var thirdAns = subject.getBestAvailable();
		assertSame(otherSigRequirements, thirdAns);

		verify(sigReqsFactory, times(2)).from(sigMetadataLookup, signatureWaivers);
	}

	private static final Instant signedAt = Instant.ofEpochSecond(1_234_567, 890);
	private static final MutableStateChildren unsignedChildren = new MutableStateChildren();
	private static final MutableStateChildren workingChildren = new MutableStateChildren();
	private static final MutableStateChildren firstSignedChildren = new MutableStateChildren(signedAt);
	private static final MutableStateChildren secondSignedChildren = new MutableStateChildren(signedAt.plusSeconds(3));
}
