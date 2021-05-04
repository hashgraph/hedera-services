package com.hedera.services.txns.submission;

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

import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class SemanticPrecheckTest {
	private final SignedTxnAccessor xferAccessor = SignedTxnAccessor.uncheckedFrom(Transaction.newBuilder()
			.setBodyBytes(TransactionBody.newBuilder()
					.setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
					.build()
					.toByteString())
			.build());

	@Mock
	private TransitionLogic transitionLogic;
	@Mock
	private TransitionLogicLookup transitionLogicLookup;
	@Mock
	private Function<TransactionBody, ResponseCodeEnum> semanticsCheck;

	private SemanticPrecheck subject;

	@BeforeEach
	void setUp() {
		subject = new SemanticPrecheck(transitionLogicLookup);
	}

	@Test
	void usesDiscoveredLogicCheck() {
		given(transitionLogicLookup.lookupFor(CryptoTransfer, xferAccessor.getTxn()))
				.willReturn(Optional.of(transitionLogic));
		given(transitionLogic.semanticCheck())
				.willReturn(semanticsCheck);
		given(semanticsCheck.apply(xferAccessor.getTxn()))
				.willReturn(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS);

		// when:
		var result = subject.validate(xferAccessor.getFunction(), xferAccessor.getTxn(), NOT_SUPPORTED);

		// then:
		Assertions.assertEquals(ACCOUNT_REPEATED_IN_ACCOUNT_AMOUNTS, result);
	}

	@Test
	void defaultsToNotSupported() {
		given(transitionLogicLookup.lookupFor(CryptoTransfer, xferAccessor.getTxn()))
				.willReturn(Optional.empty());

		// when:
		var result = subject.validate(xferAccessor.getFunction(), xferAccessor.getTxn(), INSUFFICIENT_TX_FEE);

		// then:
		Assertions.assertEquals(INSUFFICIENT_TX_FEE, result);
	}
}
