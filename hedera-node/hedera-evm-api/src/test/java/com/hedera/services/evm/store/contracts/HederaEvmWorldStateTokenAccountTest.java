package com.hedera.services.evm.store.contracts;

import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.datatypes.Wei;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.evm.store.contracts.HederaEvmWorldStateTokenAccount.bytecodeForToken;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class HederaEvmWorldStateTokenAccountTest {
	private static final Address pretendTokenAddr = Address.BLS12_G1MULTIEXP;

	private HederaEvmWorldStateTokenAccount subject =
			new HederaEvmWorldStateTokenAccount(pretendTokenAddr);

	@Test
	void getsExpectedCode() {
		final var expected = bytecodeForToken(pretendTokenAddr);
		final var firstActual = subject.getCode();
		final var secondActual = subject.getCode();
		assertEquals(expected, firstActual);
		assertSame(firstActual, secondActual);
	}

	@Test
	void alwaysHasCode() {
		assertTrue(subject.hasCode());
	}

	@Test
	void neverEmpty() {
		assertFalse(subject.isEmpty());
	}

	@Test
	void hasTokenNonce() {
		assertEquals(HederaEvmWorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE, subject.getNonce());
	}

	@Test
	void balanceAlwaysZero() {
		final var balance = subject.getBalance();
		assertEquals(Wei.of(0), balance);
	}

	@Test
	void addressHashEmpty() {
		assertEquals(Hash.EMPTY, subject.getAddressHash());
	}

	@Test
	void expectedCodeHash() {
		final var bytecode = bytecodeForToken(pretendTokenAddr);
		final var expected = Hash.hash(bytecode);
		final var actual = subject.getCodeHash();
		assertEquals(expected, actual);
	}

	@Test
	void allStorageIsZero() {
		final var storageValue = subject.getStorageValue(UInt256.ONE);
		final var origStorageValue = subject.getOriginalStorageValue(UInt256.ONE);
		assertEquals(UInt256.ZERO, storageValue);
		assertEquals(UInt256.ZERO, origStorageValue);
	}

	@Test
	void storageEntriesStreamStillNotSupported() {
		Assertions.assertThrows(
				UnsupportedOperationException.class, () -> subject.storageEntriesFrom(null, 0));
	}
}