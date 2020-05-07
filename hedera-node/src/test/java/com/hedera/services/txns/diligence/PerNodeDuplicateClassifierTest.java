package com.hedera.services.txns.diligence;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.*;
import static com.hedera.services.txns.diligence.DuplicateClassification.*;

@RunWith(JUnitPlatform.class)
class PerNodeDuplicateClassifierTest {
	final private AccountID payer1 = asAccount("0.0.1111");
	final private AccountID payer2 = asAccount("0.0.2222");
	final private AccountID nodeA = asAccount("0.0.3");
	final private AccountID nodeB = asAccount("0.0.4");
	final private TransactionID txnId1 = TransactionID.newBuilder().setAccountID(payer1).build();
	final private TransactionID txnId2 = TransactionID.newBuilder().setAccountID(payer2).build();
	final long at = 1_234_567L;

	DuplicateClassifier aClassifier;
	DuplicateClassifier bClassifier;
	PerNodeDuplicateClassifier subject;
	Supplier<DuplicateClassifier> factory;
	Map<AccountID, DuplicateClassifier> nodeClassifiers;

	@BeforeEach
	private void setup() {
		aClassifier = mock(DuplicateClassifier.class);
		bClassifier = mock(DuplicateClassifier.class);

		factory = mock(Supplier.class);
		given(factory.get()).willReturn(aClassifier).willReturn(bClassifier);

		nodeClassifiers = new HashMap<>();

		subject = new PerNodeDuplicateClassifier(factory, nodeClassifiers);
	}

	@Test
	public void delegatesObservationsCorrectly() {
		// when:
		subject.observe(nodeA, txnId1, at);
		subject.observe(nodeB, txnId2, at + 1);

		// then:
		assertEquals(nodeClassifiers.get(nodeA), aClassifier);
		assertEquals(nodeClassifiers.get(nodeB), bClassifier);
		// and:
		verify(aClassifier).observe(txnId1, at);
		verify(bClassifier).observe(txnId2, at + 1);
	}

	@Test
	public void propagatesShift() {
		// given:
		nodeClassifiers.put(nodeA, aClassifier);
		nodeClassifiers.put(nodeB, bClassifier);

		// when:
		subject.shiftWindow(at + 100);

		// then:
		verify(aClassifier).shiftWindow(at + 100);
		verify(bClassifier).shiftWindow(at + 100);
	}

	@Test
	public void classifiesNodeDuplicate() {
		given(aClassifier.isDuplicate(txnId1)).willReturn(true);
		// and:
		nodeClassifiers.put(nodeA, aClassifier);

		// when:
		DuplicateClassification actual = subject.classify(nodeA, txnId1);

		// then:
		assertEquals(NODE_DUPLICATE, actual);
	}

	@Test
	public void classifiesDuplicate() {
		given(aClassifier.isDuplicate(txnId1)).willReturn(false);
		given(bClassifier.isDuplicate(txnId1)).willReturn(true);
		// and:
		nodeClassifiers.put(nodeA, aClassifier);
		nodeClassifiers.put(nodeB, bClassifier);

		// when:
		DuplicateClassification actual = subject.classify(nodeA, txnId1);

		// then:
		assertEquals(DUPLICATE, actual);
	}

	@Test
	public void classifiesUnique() {
		given(aClassifier.isDuplicate(txnId1)).willReturn(false);
		given(bClassifier.isDuplicate(txnId1)).willReturn(false);
		// and:
		nodeClassifiers.put(nodeA, aClassifier);
		nodeClassifiers.put(nodeB, bClassifier);

		// when:
		DuplicateClassification actual = subject.classify(nodeA, txnId1);

		// then:
		assertEquals(BELIEVED_UNIQUE, actual);
	}
}
