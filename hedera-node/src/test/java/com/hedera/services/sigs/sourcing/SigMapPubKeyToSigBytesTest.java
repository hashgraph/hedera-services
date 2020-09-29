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
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.test.factories.keys.KeyFactory;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.keys.KeyTreeLeaf;
import com.hedera.test.factories.keys.OverlappingKeyGenerator;
import com.hedera.test.factories.sigs.SigFactory;
import com.hedera.test.factories.sigs.SigMapGenerator;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.swirlds.common.crypto.SignatureType;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.hedera.test.factories.keys.NodeFactory.*;
import static com.hedera.test.factories.txns.SystemDeleteFactory.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertThrows;

@RunWith(JUnitPlatform.class)
public class SigMapPubKeyToSigBytesTest {
	private final byte[] EMPTY_SIG = {};
	private final KeyTree payerKt =
			KeyTree.withRoot(list(ed25519(true), ed25519(true), ed25519(true), ecdsa384(true), rsa3072(true)));
	private final KeyTree otherKt =
			KeyTree.withRoot(list(ed25519(true), ed25519(true), ed25519(true)));
	private final KeyFactory defaultFactory = KeyFactory.getDefaultInstance();
	private final KeyFactory overlapFactory = new KeyFactory(OverlappingKeyGenerator.withDefaultOverlaps());
	private final SigMapGenerator ambigSigMapGen = SigMapGenerator.withAmbiguousPrefixes();

	@Test
	public void getsExpectedSigBytesForPayer() throws Throwable {
		// given:
		Transaction signedTxn = newSignedSystemDelete()
				.payerKt(payerKt)
				.nonPayerKts(otherKt)
				.get();
		PubKeyToSigBytes subject = PubKeyToSigBytes.forPayer(signedTxn);

		// expect:
		lookupsMatch(payerKt, defaultFactory, CommonUtils.extractTransactionBodyBytes(signedTxn), subject);
	}

	@Test
	public void getsExpectedSigBytesForOtherParties() throws Throwable {
		// given:
		Transaction signedTxn = newSignedSystemDelete()
				.payerKt(payerKt)
				.nonPayerKts(otherKt)
				.get();
		PubKeyToSigBytes subject = PubKeyToSigBytes.forOtherParties(signedTxn);

		// expect:
		lookupsMatch(otherKt, defaultFactory, CommonUtils.extractTransactionBodyBytes(signedTxn), subject);
	}

	@Test
	public void getsExpectedSigBytesForAllParties() throws Throwable {
		// given:
		Transaction signedTxn = newSignedSystemDelete()
				.payerKt(payerKt)
				.nonPayerKts(otherKt)
				.get();
		PubKeyToSigBytes subject = PubKeyToSigBytes.forAllParties(signedTxn);

		// expect:
		lookupsMatch(payerKt, defaultFactory, CommonUtils.extractTransactionBodyBytes(signedTxn), subject);
		lookupsMatch(otherKt, defaultFactory, CommonUtils.extractTransactionBodyBytes(signedTxn), subject);
	}

	@Test
	public void rejectsNonUniqueSigBytes() throws Throwable {
		// given:
		Transaction signedTxn =
				newSignedSystemDelete().sigMapGen(ambigSigMapGen).keyFactory(overlapFactory).payerKt(payerKt).get();
		PubKeyToSigBytes subject = PubKeyToSigBytes.from(signedTxn.getSigMap());

		// expect:
		assertThrows(KeyPrefixMismatchException.class, () -> {
			lookupsMatch(payerKt, overlapFactory, CommonUtils.extractTransactionBodyBytes(signedTxn), subject);
		});
	}

	private void lookupsMatch(KeyTree kt, KeyFactory factory, byte[] data, PubKeyToSigBytes subject) throws Exception {
		AtomicReference<Exception> thrown = new AtomicReference<>();
		kt.traverseLeaves(leaf -> {
			byte[] pubKey = pubKeyFor(leaf, factory);
			byte[] sigBytes = EMPTY_SIG;
			byte[] expectedSigBytes = expectedSigFor(leaf, factory, data);
			try {
				sigBytes = subject.sigBytesFor(pubKey);
			} catch (Exception e) {
				thrown.set(e);
			}
			if (thrown.get() == null) {
				assertThat(List.of(sigBytes), contains(expectedSigBytes));
			}
		});
		if (thrown.get() != null) {
			throw thrown.get();
		}
	}
	private byte[] pubKeyFor(KeyTreeLeaf leaf, KeyFactory factory) {
		Key key = leaf.asKey(factory);
		if (key.getEd25519() != ByteString.EMPTY) {
			return key.getEd25519().toByteArray();
		} else if (key.getECDSA384() != ByteString.EMPTY) {
			return key.getECDSA384().toByteArray();
		} else if (key.getRSA3072() != ByteString.EMPTY) {
			return key.getRSA3072().toByteArray();
		}
		throw new AssertionError("Impossible leaf type!");
	}
	private byte[] expectedSigFor(KeyTreeLeaf leaf, KeyFactory factory, byte[] data) {
		if (!leaf.isUsedToSign()) {
			return EMPTY_SIG;
		} else {
			if (leaf.getSigType() == SignatureType.ED25519) {
				return SigFactory.signUnchecked(data, factory.lookupPrivateKey(leaf.asKey(factory)));
			} else if (leaf.getSigType() == SignatureType.RSA) {
				return SigFactory.NONSENSE_RSA_SIG;
			} else if (leaf.getSigType() == SignatureType.ECDSA) {
				return SigFactory.NONSENSE_ECDSA_SIG;
			}
			throw new AssertionError("Impossible leaf type!");
		}
	}
}
