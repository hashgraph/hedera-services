package com.hedera.services.contracts.execution;

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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.keys.SyncActivationCheck;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.BDDMockito.*;

class TxnAwareSoliditySigsVerifierTest {
	AccountID payer = IdUtils.asAccount("0.0.2");
	AccountID sigRequired = IdUtils.asAccount("0.0.555");
	AccountID smartContract = IdUtils.asAccount("0.0.666");
	AccountID noSigRequired = IdUtils.asAccount("0.0.777");
	Set<AccountID> touched;
	SyncVerifier syncVerifier;
	PlatformTxnAccessor accessor;
	JKey expectedKey;

	MerkleAccount sigReqAccount, noSigReqAccount, contract;

	TransactionContext txnCtx;
	SyncActivationCheck areActive;
	FCMap<MerkleEntityId, MerkleAccount> accounts;

	TxnAwareSoliditySigsVerifier subject;

	@BeforeEach
	private void setup() throws Exception {
		syncVerifier = mock(SyncVerifier.class);
		expectedKey = TxnHandlingScenario.MISC_ACCOUNT_KT.asJKey();

		contract = mock(MerkleAccount.class);
		given(contract.isSmartContract()).willReturn(true);
		given(contract.isReceiverSigRequired()).willReturn(true);
		sigReqAccount = mock(MerkleAccount.class);
		given(sigReqAccount.isReceiverSigRequired()).willReturn(true);
		given(sigReqAccount.getKey()).willReturn(expectedKey);
		noSigReqAccount = mock(MerkleAccount.class);
		given(noSigReqAccount.isReceiverSigRequired()).willReturn(false);

		accessor = mock(PlatformTxnAccessor.class);
		txnCtx = mock(TransactionContext.class);
		given(txnCtx.accessor()).willReturn(accessor);
		given(txnCtx.activePayer()).willReturn(payer);

		accounts = mock(FCMap.class);
		given(accounts.get(MerkleEntityId.fromAccountId(payer))).willReturn(sigReqAccount);
		given(accounts.get(MerkleEntityId.fromAccountId(sigRequired))).willReturn(sigReqAccount);
		given(accounts.get(MerkleEntityId.fromAccountId(noSigRequired))).willReturn(noSigReqAccount);
		given(accounts.get(MerkleEntityId.fromAccountId(smartContract))).willReturn(contract);

		areActive = mock(SyncActivationCheck.class);

		subject = new TxnAwareSoliditySigsVerifier(syncVerifier, txnCtx, areActive, () -> accounts);
	}

	@Test
	public void respectsActivity() {
		given(areActive.allKeysAreActive(
				argThat(List.of(expectedKey)::equals),
				any(), any(), any(), any(), any(), any(), any())).willReturn(false);
		// and:
		touched = Set.of(payer, sigRequired, smartContract);

		// when:
		boolean flag = subject.allRequiredKeysAreActive(touched);

		// then:
		Assertions.assertFalse(flag);
		// and:
		verify(areActive).allKeysAreActive(
				argThat(List.of(expectedKey)::equals),
				any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	public void filtersContracts() {
		given(areActive.allKeysAreActive(
				argThat(List.of(expectedKey)::equals),
				any(), any(), any(), any(), any(), any(), any())).willReturn(true);
		// and:
		touched = Set.of(payer, sigRequired, smartContract);

		// when:
		boolean flag = subject.allRequiredKeysAreActive(touched);

		// then:
		Assertions.assertTrue(flag);
	}

	@Test
	public void filtersNoSigRequired() {
		given(areActive.allKeysAreActive(
				argThat(List.of(expectedKey)::equals),
				any(), any(), any(), any(), any(), any(), any())).willReturn(true);
		// and:
		touched = Set.of(payer, noSigRequired, sigRequired);

		// when:
		boolean flag = subject.allRequiredKeysAreActive(touched);

		// then:
		Assertions.assertTrue(flag);
		// and:
		verify(areActive).allKeysAreActive(any(), any(), any(), any(), any(), any(), any(), any());
	}

	@Test
	public void filtersPayerSinceSigIsGuaranteed() {
		touched = Set.of(payer, noSigRequired);

		// when:
		boolean flag = subject.allRequiredKeysAreActive(touched);

		// then:
		Assertions.assertTrue(flag);
	}
}
