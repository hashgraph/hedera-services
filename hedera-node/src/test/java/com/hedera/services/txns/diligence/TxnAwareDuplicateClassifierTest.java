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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.time.Instant;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.mockito.BDDMockito.*;
import static com.hedera.services.txns.diligence.DuplicateClassification.*;

@RunWith(JUnitPlatform.class)
class TxnAwareDuplicateClassifierTest {
	Instant now = Instant.ofEpochSecond(1_234_567L);
	AccountID node = asAccount("0.0.3");
	TransactionID txnId = TransactionID.newBuilder().setAccountID(asAccount("0.0.2")).build();

	TransactionContext txnCtx;
	PlatformTxnAccessor accessor;
	NodeDuplicateClassifier nodeDuplicateClassifier;

	TxnAwareDuplicateClassifier subject;

	@BeforeEach
	private void setup() {
		accessor = mock(PlatformTxnAccessor.class);
		given(accessor.getTxnId()).willReturn(txnId);

		txnCtx = mock(TransactionContext.class);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(now);
		given(txnCtx.submittingNodeAccount()).willReturn(node);

		nodeDuplicateClassifier = mock(NodeDuplicateClassifier.class);

		subject = new TxnAwareDuplicateClassifier(txnCtx, nodeDuplicateClassifier);
	}

	@Test
	public void delegatesWindowShift() {
		// when:
		subject.shiftDetectionWindow();

		// then:
		verify(nodeDuplicateClassifier).shiftWindow(now.getEpochSecond());
	}

	@Test
	public void delegatesDuplicityDecision() {
		given(nodeDuplicateClassifier.classify(node, txnId)).willReturn(NODE_DUPLICATE);

		// when:
		DuplicateClassification actual = subject.duplicityOfActiveTxn();

		// then:
		assertEquals(NODE_DUPLICATE, actual);
	}

	@Test
	public void ignoresObservationIfInvisible() {
		given(txnCtx.status()).willReturn(INVALID_PAYER_SIGNATURE);

		// when:
		subject.incorporateCommitment();

		// then:
		verify(nodeDuplicateClassifier, never()).observe(node, txnId, now.getEpochSecond());
	}

	@Test
	public void delegatesObservationIfVisible() {
		given(txnCtx.status()).willReturn(INVALID_SIGNATURE);

		// when:
		subject.incorporateCommitment();

		// then:
		verify(nodeDuplicateClassifier).observe(node, txnId, now.getEpochSecond());
	}
}
