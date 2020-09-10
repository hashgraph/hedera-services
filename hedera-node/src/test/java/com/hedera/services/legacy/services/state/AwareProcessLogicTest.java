package com.hedera.services.legacy.services.state;

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

import com.hedera.services.context.ServicesContext;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.Transaction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import javax.annotation.Signed;
import java.time.Instant;

import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class AwareProcessLogicTest {
	Logger mockLog;
	Transaction platformTxn;
	Transaction platformSignedTxn;
	AddressBook book;
	ServicesContext ctx;

	AwareProcessLogic subject;

	@BeforeEach
	public void setup() {
		ctx = mock(ServicesContext.class);
		mockLog = mock(Logger.class);
		TransactionBody txnBody = TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
								.setAccountID(IdUtils.asAccount("0.0.2"))).build();
		platformTxn = new Transaction(com.hederahashgraph.api.proto.java.Transaction.newBuilder()
				.setBodyBytes(txnBody.toByteString())
				.build().toByteArray());

		AwareProcessLogic.log = mockLog;

		var zeroStakeAddress = mock(Address.class);
		given(zeroStakeAddress.getStake()).willReturn(0L);
		var stakedAddress = mock(Address.class);
		given(stakedAddress.getStake()).willReturn(1L);
		book = mock(AddressBook.class);
		given(book.getAddress(1)).willReturn(stakedAddress);
		given(book.getAddress(666L)).willReturn(zeroStakeAddress);
		given(ctx.addressBook()).willReturn(book);

		subject = new AwareProcessLogic(ctx);

		SignedTransaction signedTxn = SignedTransaction.newBuilder().setBodyBytes(txnBody.toByteString()).build();
		platformSignedTxn =  new Transaction(com.hederahashgraph.api.proto.java.Transaction.newBuilder().
				setSignedTransactionBytes(signedTxn.toByteString()).build().toByteArray());
	}

	@AfterEach
	public void cleanup() {
		AwareProcessLogic.log = LogManager.getLogger(AwareProcessLogic.class);
	}

	@Test
	public void shortCircuitsWithWarningOnZeroStakeSubmission() {
		// setup:
		var now = Instant.now();
		var then = now.minusMillis(1L);

		given(ctx.consensusTimeOfLastHandledTxn()).willReturn(then);

		// when:
		subject.incorporateConsensusTxn(platformTxn, now, 666);

		// then:
		verify(mockLog).warn(argThat((String s) -> s.startsWith("Ignoring a transaction submitted by zero-stake")));
	}

	@Test
	public void shortCircuitsWithErrorOnNonIncreasingConsensusTime() {
		// setup:
		var now = Instant.now();

		given(ctx.consensusTimeOfLastHandledTxn()).willReturn(now);

		// when:
		subject.incorporateConsensusTxn(platformTxn, now,1);

		// then:
		verify(mockLog).error(argThat((String s) -> s.startsWith("Catastrophic invariant failure!")));
	}

	@Test
	public void shortCircuitsWithWarningOnZeroStakeSignedTxnSubmission() {
		// setup:
		var now = Instant.now();
		var then = now.minusMillis(1L);

		given(ctx.consensusTimeOfLastHandledTxn()).willReturn(then);

		// when:
		subject.incorporateConsensusTxn(platformSignedTxn, now, 666);

		// then:
		verify(mockLog).warn(argThat((String s) -> s.startsWith("Ignoring a transaction submitted by zero-stake")));
	}
}