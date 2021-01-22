package com.hedera.services.sigs.verification;

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

import com.hedera.services.sigs.PlatformSigOps;
import com.hedera.services.sigs.factories.BodySigningSigFactory;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytesProvider;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.txns.PlatformTxnFactory;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.TransactionSignature;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.util.Collections.EMPTY_LIST;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.hedera.test.factories.sigs.SyncVerifiers.*;

import static org.mockito.BDDMockito.*;
import static com.hedera.services.utils.PlatformTxnAccessor.uncheckedAccessorFor;
import static com.hedera.test.factories.keys.NodeFactory.*;

public class PrecheckVerifierTest {
	private static List<JKey> reqKeys;
	private static final TransactionBody txnBody = TransactionBody.newBuilder()
			.setTransactionID(TransactionID.newBuilder().setAccountID(IdUtils.asAccount("0.0.2")))
			.build();
	private static final Transaction txn = Transaction.newBuilder().setBodyBytes(txnBody.toByteString()).build();
	private static final PlatformTxnAccessor accessor = uncheckedAccessorFor(PlatformTxnFactory.from(txn));

	private final static byte[][] VALID_SIG_BYTES = {
			"firstSig".getBytes(),
			"secondSig".getBytes(),
			"thirdSig".getBytes(),
			"fourthSig".getBytes()
	};
	private final static Supplier<PubKeyToSigBytes> VALID_PROVIDER_FACTORY = () -> new PubKeyToSigBytes() {
		private int i = 0;

		@Override
		public byte[] sigBytesFor(byte[] pubKey) throws Exception {
			return VALID_SIG_BYTES[i++];
		}
	};
	private static List<TransactionSignature> expectedSigs = EMPTY_LIST;

	private PrecheckKeyReqs precheckKeyReqs;
	private PubKeyToSigBytesProvider provider;
	private PrecheckVerifier subject;

	@BeforeAll
	static void setupAll() throws Throwable {
		reqKeys = List.of(
				KeyTree.withRoot(list(ed25519(), list(ed25519(), ed25519()))).asJKey(),
				KeyTree.withRoot(ed25519()).asJKey());
		expectedSigs = PlatformSigOps.createEd25519PlatformSigsFrom(
				reqKeys, VALID_PROVIDER_FACTORY.get(), new BodySigningSigFactory(accessor.getTxnBytes())
		).getPlatformSigs();
	}

	@BeforeEach
	void setup() {
		provider = mock(PubKeyToSigBytesProvider.class);
		precheckKeyReqs = mock(PrecheckKeyReqs.class);
	}

	@Test
	public void affirmsValidSignatures() throws Exception {
		given(precheckKeyReqs.getRequiredKeys(txnBody)).willReturn(reqKeys);
		given(provider.allPartiesSigBytesFor(txn)).willReturn(VALID_PROVIDER_FACTORY.get());
		AtomicReference<List<TransactionSignature>> actualSigsVerified = new AtomicReference<>();
		givenImpliedSubject(sigs -> {
			actualSigsVerified.set(sigs);
			ALWAYS_VALID.verifySync(sigs);
		});

		// when:
		boolean hasPrechekSigs = subject.hasNecessarySignatures(accessor);

		// then:
		assertEquals(expectedSigs, actualSigsVerified.get());
		assertTrue(hasPrechekSigs);
	}

	@Test
	public void rejectsInvalidSignatures() throws Exception {
		given(precheckKeyReqs.getRequiredKeys(txnBody)).willReturn(reqKeys);
		given(provider.allPartiesSigBytesFor(txn)).willReturn(VALID_PROVIDER_FACTORY.get());
		AtomicReference<List<TransactionSignature>> actualSigsVerified = new AtomicReference<>();
		givenImpliedSubject(sigs -> {
			actualSigsVerified.set(sigs);
			NEVER_VALID.verifySync(sigs);
		});

		// when:
		boolean hasPrechekSigs = subject.hasNecessarySignatures(accessor);

		// then:
		assertEquals(expectedSigs, actualSigsVerified.get());
		assertFalse(hasPrechekSigs);
	}

	@Test
	public void propagatesSigCreationFailure() throws Exception {
		given(precheckKeyReqs.getRequiredKeys(txnBody)).willReturn(reqKeys);
		given(provider.allPartiesSigBytesFor(txn)).willReturn(bytes -> {
			throw new KeyPrefixMismatchException("Oops!");
		});
		givenImpliedSubject(ALWAYS_VALID);

		// expect:
		assertThrows(KeyPrefixMismatchException.class, () -> subject.hasNecessarySignatures(accessor));
	}

	@Test
	public void rejectsGivenInvalidPayerException() throws Exception {
		given(precheckKeyReqs.getRequiredKeys(txnBody)).willThrow(new InvalidPayerAccountException());
		givenImpliedSubject(ALWAYS_VALID);

		// expect:
		assertFalse(subject.hasNecessarySignatures(accessor));
	}

	@Test
	public void propagatesOtherKeyLookupExceptions() throws Exception {
		given(precheckKeyReqs.getRequiredKeys(txnBody)).willThrow(new IllegalStateException());
		givenImpliedSubject(ALWAYS_VALID);

		// expect:
		assertThrows(IllegalStateException.class, () -> subject.hasNecessarySignatures(accessor));
	}

	@Test
	public void affirmsValidSignaturesInSignedTxn() throws Exception {
		//setUp
		SignedTransaction signedTransaction = SignedTransaction.newBuilder().setBodyBytes(
				txnBody.toByteString()).build();
		final Transaction signedTxn = Transaction.newBuilder().setSignedTransactionBytes(
				signedTransaction.toByteString()).build();
		PlatformTxnAccessor signedTxnAccessor = uncheckedAccessorFor(PlatformTxnFactory.from(signedTxn));

		//given
		given(precheckKeyReqs.getRequiredKeys(TransactionBody.parseFrom(signedTransaction.getBodyBytes()))).
				willReturn(reqKeys);
		given(provider.allPartiesSigBytesFor(signedTxn)).willReturn(VALID_PROVIDER_FACTORY.get());
		AtomicReference<List<TransactionSignature>> actualSigsVerified = new AtomicReference<>();
		givenImpliedSubject(sigs -> {
			actualSigsVerified.set(sigs);
			ALWAYS_VALID.verifySync(sigs);
		});

		// when:
		boolean hasPrechekSigs = subject.hasNecessarySignatures(signedTxnAccessor);

		// then:
		assertEquals(expectedSigs, actualSigsVerified.get());
		assertTrue(hasPrechekSigs);
	}

	private void givenImpliedSubject(SyncVerifier syncVerifier) {
		subject = new PrecheckVerifier(syncVerifier, precheckKeyReqs, provider);
	}
}
