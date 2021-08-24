package com.hedera.services.state.org;

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

import com.hedera.services.ServicesApp;
import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.swirlds.fchashmap.FCOneToManyRelation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StateMetadataTest {
	@Mock
	private ServicesApp app;
	@Mock
	private FCOneToManyRelation<PermHashInteger, Long> uniqueTokenAssociations;
	@Mock
	private FCOneToManyRelation<PermHashInteger, Long> uniqueOwnershipAssociations;
	@Mock
	private FCOneToManyRelation<PermHashInteger, Long> uniqueTreasuryOwnershipAssociations;

	private StateMetadata subject;

	@BeforeEach
	void setUp() {
		subject = new StateMetadata(app);
	}

	@Test
	void copyAsExpected() {
		setupWithMockFcotmr();

		given(uniqueTokenAssociations.copy()).willReturn(uniqueTokenAssociations);
		given(uniqueOwnershipAssociations.copy()).willReturn(uniqueOwnershipAssociations);
		given(uniqueTreasuryOwnershipAssociations.copy()).willReturn(uniqueTreasuryOwnershipAssociations);

		// when:
		final var copy = subject.copy();

		// then:
		assertSame(app, copy.app());
		// and:
		assertSame(uniqueTokenAssociations, copy.getUniqueTokenAssociations());
		verify(uniqueTokenAssociations).copy();
		assertSame(uniqueOwnershipAssociations, copy.getUniqueOwnershipAssociations());
		verify(uniqueOwnershipAssociations).copy();
		assertSame(uniqueTreasuryOwnershipAssociations, copy.getUniqueTreasuryOwnershipAssociations());
		verify(uniqueTreasuryOwnershipAssociations).copy();
	}

	@Test
	void releaseOnArchival() {
		setupWithMockFcotmr();

		// when:
		subject.archive();

		// then:
		verify(uniqueTokenAssociations).release();
		verify(uniqueOwnershipAssociations).release();
		verify(uniqueTreasuryOwnershipAssociations).release();
	}

	@Test
	void releaseOnRelease() {
		setupWithMockFcotmr();

		// when:
		subject.release();

		// then:
		verify(uniqueTokenAssociations).release();
		verify(uniqueOwnershipAssociations).release();
		verify(uniqueTreasuryOwnershipAssociations).release();
	}

	private void setupWithMockFcotmr() {
		subject.setUniqueTokenAssociations(uniqueTokenAssociations);
		subject.setUniqueOwnershipAssociations(uniqueOwnershipAssociations);
		subject.setUniqueTreasuryOwnershipAssociations(uniqueTreasuryOwnershipAssociations);
	}

	@Test
	void gettersWork() {
		// expect:
		assertSame(app, subject.app());
		assertNotNull(subject.getUniqueTokenAssociations());
		assertNotNull(subject.getUniqueOwnershipAssociations());
		assertNotNull(subject.getUniqueTreasuryOwnershipAssociations());
	}
}
