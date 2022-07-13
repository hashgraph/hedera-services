package com.hedera.services.keys;

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

import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.swirlds.common.crypto.CryptographyException;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith({ MockitoExtension.class, LogCaptureExtension.class })
class OnlyIfSigVerifiableValidTest {
	@Mock
	private Future<Void> syncFuture;

	@Mock
	private SyncVerifier syncVerifier;

	@LoggingTarget
	private LogCaptor logCaptor;
	@LoggingSubject
	private OnlyIfSigVerifiableValid subject;

	@BeforeEach
	void setUp() {
		subject = new OnlyIfSigVerifiableValid(syncVerifier);
	}

	@Test
	void acceptsValidSig() throws ExecutionException, InterruptedException {
		final var sig = new SignatureWithStatus(VerificationStatus.VALID);
		sig.setFuture(syncFuture);

		Assertions.assertTrue(subject.test(null, sig));

		verify(syncFuture).get();
	}

	@Test
	void dealsWithInterruptedException() throws ExecutionException, InterruptedException {
		final var sig = new SignatureWithStatus(VerificationStatus.UNKNOWN);
		sig.setFuture(syncFuture);
		willThrow(InterruptedException.class).given(syncFuture).get();

		assertFalse(subject.test(null, sig));

		assertThat(logCaptor.warnLogs(), contains(
				startsWith("Interrupted while validating signature, this will be fatal outside reconnect")));
	}

	@Test
	void dealsWithExecutionException() throws ExecutionException, InterruptedException {
		final var sig = new SignatureWithStatus(VerificationStatus.UNKNOWN);
		sig.setFuture(syncFuture);
		willThrow(ExecutionException.class).given(syncFuture).get();

		assertFalse(subject.test(null, sig));

		assertThat(logCaptor.errorLogs(), contains(
				startsWith("Erred while validating signature, this is likely fatal")));
	}

	@Test
	void acceptsVerifiableValidSig() {
		// given:
		final var sig = new SignatureWithStatus(VerificationStatus.UNKNOWN);
		sig.setFuture(syncFuture);
		// and:
		willAnswer(invocationOnMock -> {
			final List<SignatureWithStatus> sigs = invocationOnMock.getArgument(0);
			sigs.get(0).setStatus(VerificationStatus.VALID);
			return null;
		}).given(syncVerifier).verifySync(List.of(sig));

		// expect:
		Assertions.assertTrue(subject.test(null, sig));
	}

	@Test
	void rejectsVerifiableInvalidSig() {
		// given:
		final var sig = new SignatureWithStatus(VerificationStatus.UNKNOWN);
		sig.setFuture(syncFuture);
		// and:
		willAnswer(invocationOnMock -> {
			final List<SignatureWithStatus> sigs = invocationOnMock.getArgument(0);
			sigs.get(0).setStatus(VerificationStatus.INVALID);
			return null;
		}).given(syncVerifier).verifySync(List.of(sig));

		// expect:
		assertFalse(subject.test(null, sig));
	}

	@Test
	void rejectsUnverifiableSig() {
		// given:
		final var sig = new SignatureWithStatus(VerificationStatus.UNKNOWN);
		sig.setFuture(syncFuture);
		// and:
		willThrow(CryptographyException.class).given(syncVerifier).verifySync(List.of(sig));

		// expect:
		assertFalse(subject.test(null, sig));
	}

	@Test
	void rejectsInvalidSig() {
		// given:
		final var sig = new SignatureWithStatus(VerificationStatus.INVALID);
		sig.setFuture(syncFuture);

		// expect:
		assertFalse(subject.test(null, sig));
	}

	private static class SignatureWithStatus extends TransactionSignature {
		private Future<Void> future;
		private VerificationStatus status;

		private static byte[] MEANINGLESS_BYTE = new byte[] {
				(byte) 0xAB
		};

		public SignatureWithStatus(VerificationStatus status) {
			super(MEANINGLESS_BYTE, 0, 0, 0, 0, 0, 0);
			this.status = status;
		}

		@Override
		public VerificationStatus getSignatureStatus() {
			return status;
		}

		public void setStatus(VerificationStatus status) {
			this.status = status;
		}

		@Override
		public synchronized Future<Void> waitForFuture() {
			return future;
		}

		@Override
		public void setFuture(Future<Void> future) {
			this.future = future;
		}
	}
}
