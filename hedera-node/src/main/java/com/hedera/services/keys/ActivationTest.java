package com.hedera.services.keys;

import com.hedera.services.legacy.core.jproto.JKey;
import com.swirlds.common.crypto.TransactionSignature;

import java.util.function.BiPredicate;
import java.util.function.Function;

public interface ActivationTest {
	/**
	 * Checks if the given Hedera key has an active top-level signature, using:
	 * <ol>
	 *    <li>The provided {@code sigsFn} to map cryptographic public keys such as Ed25519
	 *    or ECDSA(secp256k1) into {@link TransactionSignature} objects; and,</li>
	 *    <li>The provided {@code validityTest} to check if a primitive keys (either
	 *    cryptographic or contract) has a valid signature.
	 * </ol>
	 *
	 * @param key the key whose activation to test
	 * @param sigsFn a mapping from public keys to cryptographic signatures
	 * @param validityTest a test for validity of the cryptographic signature for a primitive key
	 * @return  whether the Hedera key is active
	 */
	boolean test(
			JKey key,
			Function<byte[], TransactionSignature> sigsFn,
			BiPredicate<JKey, TransactionSignature> validityTest);
}
