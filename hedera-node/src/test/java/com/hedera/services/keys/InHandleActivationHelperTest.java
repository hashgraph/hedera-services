package com.hedera.services.keys;

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

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.crypto.SignatureStatus;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.order.SigningOrderResult;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.Transaction;
import com.swirlds.common.crypto.TransactionSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;

import static com.hedera.services.keys.DefaultActivationCharacteristics.DEFAULT_ACTIVATION_CHARACTERISTICS;
import static com.hedera.services.sigs.Rationalization.IN_HANDLE_SUMMARY_FACTORY;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class InHandleActivationHelperTest {
	private byte[] scopedTxnBytes = "ANYTHING".getBytes();
	private JKey other = new JEd25519Key("other".getBytes());
	private JKey scheduled = new JEd25519Key("scheduled".getBytes());
	List<JKey> required = List.of(other, scheduled);
	private SigningOrderResult<SignatureStatus> successful =
			IN_HANDLE_SUMMARY_FACTORY.forValidOrder(required);
	private SigningOrderResult<SignatureStatus> impermissible =
			IN_HANDLE_SUMMARY_FACTORY.forMissingAccount(
					IdUtils.asAccount("1.2.3"),
					TransactionID.getDefaultInstance());

	Transaction platformTxn;
	HederaSigningOrder keyOrderer;
	PlatformTxnAccessor accessor;

	TransactionSignature sig;
	CharacteristicsFactory characteristicsFactory;
	Function<byte[], TransactionSignature> scheduleSigsFn;
	Function<byte[], TransactionSignature> nonScheduleSigsFn;
	List<TransactionSignature> sigs = new ArrayList<>();

	InHandleActivationHelper.Activation activation;
	InHandleActivationHelper.PkToSigMapFactory scopedSigsFnSource;

	Function<
			List<TransactionSignature>,
			Function<byte[], TransactionSignature>> sigsFnSource;

	InHandleActivationHelper subject;

	@BeforeEach
	@SuppressWarnings("unchecked")
	public void setup() throws Exception {
		scheduled.setForScheduledTxn(true);

		keyOrderer = mock(HederaSigningOrder.class);
		given(keyOrderer.<SignatureStatus>keysForOtherParties(any(), any())).willReturn(successful);

		characteristicsFactory = mock(CharacteristicsFactory.class);
		given(characteristicsFactory.inferredFor(any())).willReturn(DEFAULT_ACTIVATION_CHARACTERISTICS);

		platformTxn = mock(Transaction.class);
		given(platformTxn.getSignatures()).willReturn(sigs);
		accessor = mock(PlatformTxnAccessor.class);
		given(accessor.getPlatformTxn()).willReturn(platformTxn);
		given(accessor.getTxnBytes()).willReturn(scopedTxnBytes);
		given(accessor.getFunction()).willReturn(CryptoTransfer);

		sig = mock(TransactionSignature.class);

		scheduleSigsFn = mock(Function.class);
		nonScheduleSigsFn = mock(Function.class);
		scopedSigsFnSource = mock(InHandleActivationHelper.PkToSigMapFactory.class);
		given(scopedSigsFnSource.get(scopedTxnBytes, true, sigs)).willReturn(nonScheduleSigsFn);
		given(scopedSigsFnSource.get(scopedTxnBytes, false, sigs)).willReturn(scheduleSigsFn);
		sigsFnSource =
				(Function<List<TransactionSignature>, Function<byte[], TransactionSignature>>) mock(Function.class);

		subject = new InHandleActivationHelper(keyOrderer, characteristicsFactory, () -> accessor);

		activation = mock(InHandleActivationHelper.Activation.class);

		InHandleActivationHelper.activation = activation;
		InHandleActivationHelper.sigsFnSource = sigsFnSource;
		InHandleActivationHelper.scopedSigsFnSource = scopedSigsFnSource;
	}

	@Test
	@SuppressWarnings("unchecked")
	public void usesEmptyKeysOnErrorReport() {
		// setup:
		BiPredicate<JKey, TransactionSignature> tests = (BiPredicate<JKey, TransactionSignature>) mock(BiPredicate.class);

		given(keyOrderer.<SignatureStatus>keysForOtherParties(any(), any())).willReturn(impermissible);

		// when:
		boolean ans = subject.areOtherPartiesActive(tests);

		// then:
		assertTrue(ans);

		// and:
		verify(activation, never()).test(any(), any(), any(), any());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void throwsIfScheduleActivationTestedForNonSchedTxn() {
		// setup:
		BiPredicate<JKey, TransactionSignature> tests = (BiPredicate<JKey, TransactionSignature>) mock(BiPredicate.class);

		// expect:
		Assertions.assertThrows(
				IllegalStateException.class,
				() -> subject.areScheduledPartiesActive(TransactionBody.getDefaultInstance(), tests));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void usesExpectedSigsFnForOthers() {
		// setup:
		BiPredicate<JKey, TransactionSignature> tests = (BiPredicate<JKey, TransactionSignature>) mock(BiPredicate.class);

		given(accessor.getFunction()).willReturn(ScheduleCreate);
		given(activation.test(scheduled, scheduleSigsFn, tests, DEFAULT_ACTIVATION_CHARACTERISTICS)).willReturn(true);
		given(activation.test(other, nonScheduleSigsFn, tests, DEFAULT_ACTIVATION_CHARACTERISTICS)).willReturn(false);

		// when:
		boolean otherAns = subject.areOtherPartiesActive(tests);
		boolean scheduledAns = subject.areScheduledPartiesActive(TransactionBody.getDefaultInstance(), tests);

		// then:
		assertFalse(otherAns);
		assertTrue(scheduledAns);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void usesExpectedKeysForOthers() {
		// setup:
		BiPredicate<JKey, TransactionSignature> tests = (BiPredicate<JKey, TransactionSignature>) mock(BiPredicate.class);

		given(sigsFnSource.apply(any())).willReturn(nonScheduleSigsFn);
		given(activation.test(other, nonScheduleSigsFn, tests, DEFAULT_ACTIVATION_CHARACTERISTICS)).willReturn(true);

		// when:
		boolean ans = subject.areOtherPartiesActive(tests);
		boolean ansAgain = subject.areOtherPartiesActive(tests);

		// then:
		assertTrue(ans);
		assertTrue(ansAgain);
		// and:
		verify(keyOrderer, times(1)).keysForOtherParties(any(), any());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void usesExpectedKeysForScheduled() {
		// setup:
		BiPredicate<JKey, TransactionSignature> tests = (BiPredicate<JKey, TransactionSignature>) mock(BiPredicate.class);

		given(accessor.getFunction()).willReturn(ScheduleCreate);
		given(activation.test(scheduled, scheduleSigsFn, tests, DEFAULT_ACTIVATION_CHARACTERISTICS)).willReturn(true);

		// when:
		boolean ans = subject.areScheduledPartiesActive(nonFileDelete(), tests);

		// then:
		assertTrue(ans);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void countsKeysAsExpected() {
		// setup:
		BiConsumer<JKey, TransactionSignature> visitor = (BiConsumer<JKey, TransactionSignature>) mock(BiConsumer.class);

		given(accessor.getFunction()).willReturn(ScheduleCreate);
		given(scopedSigsFnSource.get(scopedTxnBytes, false, sigs)).willReturn(scheduleSigsFn);
		given(scheduleSigsFn.apply(scheduled.getEd25519())).willReturn(sig);

		// when:
		subject.visitScheduledCryptoSigs(visitor);

		// then:
		verify(visitor).accept(scheduled, sig);
	}

	@AfterEach
	public void cleanup() {
		InHandleActivationHelper.activation = HederaKeyActivation::isActive;
		InHandleActivationHelper.sigsFnSource = HederaKeyActivation::pkToSigMapFrom;
		InHandleActivationHelper.scopedSigsFnSource = HederaKeyActivation::matchingPkToSigMapFrom;
	}

	private TransactionBody nonFileDelete() {
		return TransactionBody.newBuilder()
				.setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
				.build();
	}
}