package com.hedera.services.state.validation;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.test.utils.TxnUtils.assertFailsWith;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_CONTRACT_STORAGE_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UsageLimitsTest {
	@Mock
	private MutableStateChildren stateChildren;
	@Mock
	private GlobalDynamicProperties dynamicProperties;

	private UsageLimits subject;

	@BeforeEach
	void setUp() {
		subject = new UsageLimits(stateChildren, dynamicProperties);
	}

	@Test
	void refreshesThingsAsExpected() {
		given(stateChildren.numAccountAndContracts()).willReturn(3L);
		given(stateChildren.numBlobs()).willReturn(8L);
		given(stateChildren.numSchedules()).willReturn(4L);
		given(stateChildren.numTokens()).willReturn(5L);
		given(stateChildren.numTokenRels()).willReturn(6L);
		given(stateChildren.numTopics()).willReturn(7L);
		given(stateChildren.numStorageSlots()).willReturn(8L);
		given(stateChildren.numNfts()).willReturn(9L);

		subject.recordContracts(2);
		subject.refreshAccounts();
		subject.refreshFiles();
		subject.refreshNfts();
		subject.refreshSchedules();
		subject.refreshStorageSlots();
		subject.refreshTokens();
		subject.refreshTokenRels();
		subject.refreshTopics();

		assertEquals(1L, subject.getNumAccounts());
		assertEquals(2L, subject.getNumContracts());
		assertEquals(3L, subject.getNumFiles());
		assertEquals(4L, subject.getNumSchedules());
		assertEquals(5L, subject.getNumTokens());
		assertEquals(6L, subject.getNumTokenRels());
		assertEquals(7L, subject.getNumTopics());
		assertEquals(8L, subject.getNumStorageSlots());
		assertEquals(9L, subject.getNumNfts());
	}

	@Test
	void initializesThingsAsExpected() {
		subject.recordContracts(2);
		given(stateChildren.numAccountAndContracts()).willReturn(3L);
		given(stateChildren.numBlobs()).willReturn(8L);
		given(stateChildren.numSchedules()).willReturn(4L);
		given(stateChildren.numTokens()).willReturn(5L);
		given(stateChildren.numTokenRels()).willReturn(6L);
		given(stateChildren.numTopics()).willReturn(7L);
		given(stateChildren.numStorageSlots()).willReturn(8L);
		given(stateChildren.numNfts()).willReturn(9L);

		subject.updateCounts();

		assertEquals(1L, subject.getNumAccounts());
		assertEquals(2L, subject.getNumContracts());
		assertEquals(3L, subject.getNumFiles());
		assertEquals(4L, subject.getNumSchedules());
		assertEquals(5L, subject.getNumTokens());
		assertEquals(6L, subject.getNumTokenRels());
		assertEquals(7L, subject.getNumTopics());
		assertEquals(8L, subject.getNumStorageSlots());
		assertEquals(9L, subject.getNumNfts());
	}

	@Test
	void limitsNumAccounts() {
		given(dynamicProperties.maxNumAccounts()).willReturn(5L);
		subject.recordContracts(2);
		given(stateChildren.numAccountAndContracts()).willReturn(4L);

		assertDoesNotThrow(() -> subject.assertCreatableAccounts(3));
		assertTrue(subject.areCreatableAccounts(3));
		assertEquals(2L, subject.getNumAccounts());
		assertFailsWith(() -> subject.assertCreatableAccounts(4), MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
		assertFalse(subject.areCreatableAccounts(4));
	}

	@Test
	void limitsNumContracts() {
		given(dynamicProperties.maxNumContracts()).willReturn(5L);
		subject.recordContracts(2);

		assertDoesNotThrow(() -> subject.assertCreatableContracts(3));
		assertEquals(2L, subject.getNumContracts());
		assertFailsWith(() -> subject.assertCreatableContracts(4), MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
	}

	@Test
	void limitsNumNonBytecodeFiles() {
		given(dynamicProperties.maxNumFiles()).willReturn(6L);
		subject.recordContracts(2);
		given(stateChildren.numBlobs()).willReturn(8L);

		assertTrue(subject.areCreatableFiles(3));
		assertFalse(subject.areCreatableFiles(4));
	}

	@Test
	void limitsNumSchedules() {
		given(dynamicProperties.maxNumSchedules()).willReturn(6L);
		given(stateChildren.numSchedules()).willReturn(3L);

		assertTrue(subject.areCreatableSchedules(3));
		assertEquals(3L, subject.getNumSchedules());
		assertFalse(subject.areCreatableSchedules(4));
	}
	@Test
	void limitsNumTokens() {
		given(dynamicProperties.maxNumTokens()).willReturn(6L);
		given(stateChildren.numTokens()).willReturn(3L);

		assertDoesNotThrow(() -> subject.assertCreatableTokens(3));
		assertEquals(3L, subject.getNumTokens());
		assertFailsWith(() -> subject.assertCreatableTokens(4), MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
	}

	@Test
	void limitsNumTokenRels() {
		given(dynamicProperties.maxNumTokenRels()).willReturn(6L);
		given(stateChildren.numTokenRels()).willReturn(3L);

		assertTrue(subject.areCreatableTokenRels(3));
		assertDoesNotThrow(() -> subject.assertCreatableTokenRels(3));
		assertEquals(3L, subject.getNumTokenRels());
		assertFailsWith(() -> subject.assertCreatableTokenRels(4), MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
		assertFalse(subject.areCreatableTokenRels(4));
	}

	@Test
	void limitsNumTopics() {
		given(dynamicProperties.maxNumTopics()).willReturn(6L);
		given(stateChildren.numTopics()).willReturn(3L);

		assertDoesNotThrow(() -> subject.assertCreatableTopics(3));
		assertEquals(3L, subject.getNumTopics());
		assertFailsWith(() -> subject.assertCreatableTopics(4), MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
	}

	@Test
	void limitsTotalNumNfts() {
		given(stateChildren.numNfts()).willReturn(3L);
		given(dynamicProperties.maxNftMints()).willReturn(6L);

		assertDoesNotThrow(() -> subject.assertMintableNfts(3));
		assertEquals(3L, subject.getNumNfts());
		assertFailsWith(() -> subject.assertMintableNfts(4), MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED);
	}

	@Test
	void limitsTotalNumSlots() {
		given(dynamicProperties.maxAggregateContractKvPairs()).willReturn(6L);

		assertDoesNotThrow(() -> subject.assertUsableTotalSlots(6L));
		assertFailsWith(() -> subject.assertUsableTotalSlots(7L), MAX_STORAGE_IN_PRICE_REGIME_HAS_BEEN_USED);
	}

	@Test
	void limitsPerContractNumSlots() {
		given(dynamicProperties.maxIndividualContractKvPairs()).willReturn(6);

		assertDoesNotThrow(() -> subject.assertUsableContractSlots(6));
		assertFailsWith(() -> subject.assertUsableContractSlots(7L), MAX_CONTRACT_STORAGE_EXCEEDED);
	}
}
