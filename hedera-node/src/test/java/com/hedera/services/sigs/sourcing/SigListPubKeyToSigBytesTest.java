package com.hedera.services.sigs.sourcing;

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

import com.google.protobuf.ByteString;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.sigs.SigFactory;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.Signature;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.services.legacy.exception.KeySignatureCountMismatchException;
import com.swirlds.common.crypto.SignatureType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.hedera.test.factories.keys.NodeFactory.*;
import static com.hedera.test.factories.txns.SystemDeleteFactory.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static com.hedera.test.factories.sigs.SigFactory.NONSENSE_RSA_SIG;
import static com.hedera.test.factories.sigs.SigFactory.NONSENSE_ECDSA_SIG;

@RunWith(JUnitPlatform.class)
public class SigListPubKeyToSigBytesTest {
	private final static byte[] ANY_PUBLIC_KEY = "This isn't even really a public key! ;)".getBytes();
	private final static KeyTree[] payerSigners = {
			SignedTxnFactory.DEFAULT_PAYER_KT
	};
	private final static KeyTree[] otherPartySigners = {
		KeyTree.withRoot(threshold(1, ed25519(true), ed25519(false))),
		KeyTree.withRoot(list(rsa3072(true), threshold(1, ed25519(false), ecdsa384(true))))
	};
	private final static KeyTree[] allPartySigners =
			Stream.of(payerSigners, otherPartySigners).flatMap(Arrays::stream).toArray(n -> new KeyTree[n]);
	private final static KeyFactory factory = KeyFactory.getDefaultInstance();
	private static Transaction signedTxn;
	private static List<Signature> payerSigs;
	private static List<Signature> allPartySigs;
	private static List<Signature> otherPartySigs;
	private static ByteString[] expectedPayerSigBytes;
	private static ByteString[] expectedAllPartySigBytes;
	private static ByteString[] expectedNonPayerSigBytes;

	@BeforeAll
	private static void setupAll() throws Throwable {
		signedTxn = newSignedSystemDelete().nonPayerKts(otherPartySigners).useSigList().get();
		payerSigs = signedTxn.getSigs().getSigsList().subList(0, 1);
		allPartySigs = signedTxn.getSigs().getSigsList();
		otherPartySigs = signedTxn.getSigs().getSigsList().subList(1, 3);
		expectedPayerSigBytes = getExpectedSigBytes(payerSigners);
		expectedAllPartySigBytes = getExpectedSigBytes(allPartySigners);
		expectedNonPayerSigBytes = getExpectedSigBytes(otherPartySigners);
	}

	@Test
	public void recognizesMissingOtherPartySig() throws Throwable {
		// given:
		Transaction faultySignedTxn = newSignedSystemDelete().useSigList().get();

		// when:
		PubKeyToSigBytes subject = PubKeyToSigBytes.forOtherParties(faultySignedTxn);

		// then:
		assertThrows(KeySignatureCountMismatchException.class, () -> subject.sigBytesFor(ANY_PUBLIC_KEY));
	}

	@Test
	public void recognizesMissingPayerSig() throws Throwable {
		// given:
		Transaction faultySignedTxn = newSignedSystemDelete().skipPayerSig().useSigList().get();

		// when:
		PubKeyToSigBytes subject = PubKeyToSigBytes.forPayer(faultySignedTxn);

		// then:
		assertEquals(SigListPubKeyToSigBytes.NO_SIGS, subject);
	}

	@Test
	public void returnsNonPayerSigBytesInOrder() {
		// given:
		PubKeyToSigBytes subject = PubKeyToSigBytes.forOtherParties(signedTxn);

		// when:
		List<ByteString> sigBytes = IntStream.range(1, 6).mapToObj(ignore -> {
			try {
				return ByteString.copyFrom(subject.sigBytesFor(ANY_PUBLIC_KEY));
			} catch (Exception e) {
				throw new AssertionError("Impossible!");
			}
		}).collect(toList());

		// then:
		assertThat(sigBytes, contains(expectedNonPayerSigBytes));
	}

	@Test
	public void returnsPayerSigBytesInOrder() {
		// given:
		PubKeyToSigBytes subject = PubKeyToSigBytes.forPayer(signedTxn);

		// when:
		List<ByteString> sigBytes = IntStream.range(0, 1).mapToObj(ignore -> {
			try {
				return ByteString.copyFrom(subject.sigBytesFor(ANY_PUBLIC_KEY));
			} catch (Exception e) {
				throw new AssertionError("Impossible!");
			}
		}).collect(toList());

		// then:
		assertThat(sigBytes, contains(expectedPayerSigBytes));
	}

	@Test
	public void returnsAllPartySigBytesInOrder() {
		// given:
		PubKeyToSigBytes subject = PubKeyToSigBytes.forAllParties(signedTxn);

		// when:
		List<ByteString> sigBytes = IntStream.range(0, 6).mapToObj(ignore -> {
			try {
				return ByteString.copyFrom(subject.sigBytesFor(ANY_PUBLIC_KEY));
			} catch (Exception e) {
				throw new AssertionError("Impossible!");
			}
		}).collect(toList());

		// then:
		assertThat(sigBytes, contains(expectedAllPartySigBytes));
	}

	@Test
	public void failsAfterSigBytesAreExhausted() {
		// given:
		PubKeyToSigBytes subject = PubKeyToSigBytes.from(otherPartySigs);

		// expect:
		assertThrows(KeySignatureCountMismatchException.class, () -> {
			for (int i = 1; i < 7; i++)	{
				subject.sigBytesFor(ANY_PUBLIC_KEY);
			}
		});
	}

	private static ByteString[] getExpectedSigBytes(KeyTree[] kts) {
		return Stream.of(kts)
				.flatMap(signer -> {
					List<ByteString> simpleSigs = new ArrayList<>();
					signer.traverseLeaves(leaf -> {
						if (leaf.isUsedToSign()) {
							if (leaf.getSigType() == SignatureType.ED25519) {
								simpleSigs.add(
										ByteString.copyFrom(SigFactory.signUnchecked(
												signedTxn.getBodyBytes().toByteArray(),
												factory.lookupPrivateKey(leaf.asKey()))));
							} else if (leaf.getSigType() == SignatureType.RSA) {
								simpleSigs.add(ByteString.copyFrom(NONSENSE_RSA_SIG));
							} else if (leaf.getSigType() == SignatureType.ECDSA) {
								simpleSigs.add(ByteString.copyFrom(NONSENSE_ECDSA_SIG));
							}
						} else {
							simpleSigs.add(ByteString.EMPTY);
						}
					});
					return simpleSigs.stream();
				}).toArray(n -> new ByteString[n]);
	}
}
