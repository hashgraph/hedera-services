package com.hedera.services.state.logic;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.fees.charging.TxnChargingPolicyAgent;
import com.hedera.services.utils.TxnAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TopLevelTransitionTest {
	private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L, 890);

	@Mock
	private TransactionContext txnCtx;
	@Mock
	private NetworkCtxManager networkCtxManager;
	@Mock
	private TxnChargingPolicyAgent chargingPolicyAgent;
	@Mock
	private TxnAccessor accessor;
	@Mock
	private ScreenedTransition screenedTransition;
	@Mock
	private TxnIndependentValidator txnValidator;

	private TopLevelTransition subject;

	@BeforeEach
	void setUp() {
		subject = new TopLevelTransition(
				screenedTransition,
				networkCtxManager,
				txnCtx,
				chargingPolicyAgent,
				txnValidator);
	}

	@Test
	void validatedScopedProcessFlows() {
		// setup:
		InOrder inOrder = Mockito.inOrder(networkCtxManager, chargingPolicyAgent, txnValidator);

		given(accessor.getValidationStatus()).willReturn(OK);
		given(accessor.hasActivePayerSig()).willReturn(true);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(consensusNow);
		given(chargingPolicyAgent.applyPolicyFor(accessor)).willReturn(true);

		// when:
		subject.run();

		// then:
		inOrder.verify(networkCtxManager).advanceConsensusClockTo(consensusNow);
		inOrder.verify(chargingPolicyAgent).applyPolicyFor(accessor);
		verifyNoInteractions(txnValidator);
		verify(screenedTransition).finishFor(accessor);
	}

	@Test
	void notActivePayerSig() {
		// setup:
		InOrder inOrder = Mockito.inOrder(networkCtxManager, chargingPolicyAgent, txnValidator);

		given(accessor.getValidationStatus()).willReturn(OK);
		given(accessor.hasActivePayerSig()).willReturn(false);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(consensusNow);
		given(chargingPolicyAgent.applyPolicyFor(accessor)).willReturn(true);

		// when:
		subject.run();

		// then:
		verify(txnCtx, never()).payerSigIsKnownActive();
		verify(networkCtxManager, never()).prepareForIncorporating(accessor);
		verify(screenedTransition).finishFor(accessor);
	}

	@Test
	void abortsWhenChargingPolicyAgentFails() {
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(consensusNow);
		given(accessor.getValidationStatus()).willReturn(OK);
		given(accessor.hasActivePayerSig()).willReturn(true);

		// when:
		subject.run();

		// then:
		verify(screenedTransition, never()).finishFor(accessor);
	}

	@Test
	void abortsWhenValidationFails() {
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.consensusTime()).willReturn(consensusNow);
		given(accessor.getValidationStatus()).willReturn(INVALID_SIGNATURE);

		// when:
		subject.run();

		// then:
		verify(txnCtx).setStatus(INVALID_SIGNATURE);
		verify(screenedTransition, never()).finishFor(accessor);
	}
}
