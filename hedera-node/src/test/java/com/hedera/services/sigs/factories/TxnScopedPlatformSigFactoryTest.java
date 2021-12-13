package com.hedera.services.sigs.factories;

import com.hedera.services.sigs.sourcing.KeyType;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TxnScopedPlatformSigFactoryTest {
	private static final byte[] mockKey = "abcdefg".getBytes();
	private static final byte[] mockSig = "hijklmn".getBytes();

	@Test
	void choosesAppropriateSignatureForEd25519() {
		final var subject = mock(TxnScopedPlatformSigFactory.class);

		doCallRealMethod().when(subject).signAppropriately(KeyType.ED25519, mockKey, mockSig);

		subject.signAppropriately(KeyType.ED25519, mockKey, mockSig);

		verify(subject).signBodyWithEd25519(mockKey, mockSig);
	}

	@Test
	void choosesAppropriateSignatureForSecp256k1() {
		final var subject = mock(TxnScopedPlatformSigFactory.class);

		doCallRealMethod().when(subject).signAppropriately(KeyType.ECDSA_SECP256K1, mockKey, mockSig);

		subject.signAppropriately(KeyType.ECDSA_SECP256K1, mockKey, mockSig);

		verify(subject).signKeccak256DigestWithSecp256k1(mockKey, mockSig);
	}
}