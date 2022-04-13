package com.hedera.services.txns.ethereum;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static com.hedera.services.txns.ethereum.TestingConstants.CHAINID_TESTNET;
import static com.hedera.services.txns.ethereum.TestingConstants.TINYBARS_2_IN_WEIBARS;
import static com.hedera.services.txns.ethereum.TestingConstants.TINYBARS_57_IN_WEIBARS;
import static com.hedera.services.txns.ethereum.TestingConstants.TRUFFLE0_ADDRESS;
import static com.hedera.services.txns.ethereum.TestingConstants.TRUFFLE0_PRIVATE_ECDSA_KEY;
import static com.hedera.services.txns.ethereum.TestingConstants.TRUFFLE0_PUBLIC_ECDSA_KEY;
import static com.hedera.services.txns.ethereum.TestingConstants.TRUFFLE1_ADDRESS;
import static com.hedera.services.txns.ethereum.TestingConstants.TRUFFLE1_PRIVATE_ECDSA_KEY;
import static com.hedera.services.txns.ethereum.TestingConstants.ZERO_BYTES;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class EthTxSigsTest {
	
	@Test
	void signsLegacyUnprotected() {
		var tx = new EthTxData(null, EthTxData.EthTransactionType.LEGACY_ETHEREUM, ZERO_BYTES, 1, TINYBARS_57_IN_WEIBARS,
				TINYBARS_2_IN_WEIBARS,  TINYBARS_57_IN_WEIBARS, 1_000_000L, TRUFFLE1_ADDRESS, BigInteger.ZERO, ZERO_BYTES, ZERO_BYTES, 1, new byte[0], new byte[0],
				new byte[0]);
		
		EthTxData signedTx  = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);
		
		assertArrayEquals(ZERO_BYTES, signedTx.chainId());
		assertArrayEquals(new byte[] {27}, signedTx.v());
	}
	
	@Test
	void signsLegacyProtected() {
		var tx = new EthTxData(null, EthTxData.EthTransactionType.LEGACY_ETHEREUM, CHAINID_TESTNET, 1, TINYBARS_57_IN_WEIBARS,
				TINYBARS_2_IN_WEIBARS,  TINYBARS_57_IN_WEIBARS, 1_000_000L, TRUFFLE1_ADDRESS, BigInteger.ZERO, ZERO_BYTES, ZERO_BYTES, 1, new byte[0], new byte[0],
				new byte[0]);
		
		EthTxData signedTx = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);
		
		assertArrayEquals(CHAINID_TESTNET, signedTx.chainId());
		assertArrayEquals(new byte[] {2, 116}, signedTx.v());
	}
	
	@Test
	void extractAddress() {
		var tx = new EthTxData(null, EthTxData.EthTransactionType.LEGACY_ETHEREUM, CHAINID_TESTNET, 1, TINYBARS_57_IN_WEIBARS,
				TINYBARS_2_IN_WEIBARS,  TINYBARS_57_IN_WEIBARS, 1_000_000L, TRUFFLE1_ADDRESS, BigInteger.ZERO, ZERO_BYTES, ZERO_BYTES, 1, new byte[0], new byte[0],
				new byte[0]);
		
		EthTxData 			signedTx = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);

		EthTxSigs sigs = EthTxSigs.extractSignatures(signedTx);
		
		assertArrayEquals(sigs.address(), TRUFFLE0_ADDRESS);
		assertArrayEquals(sigs.publicKey(), TRUFFLE0_PUBLIC_ECDSA_KEY);
	}
	
	@Test void equalsToStringHashCode() {
		var tx = new EthTxData(null, EthTxData.EthTransactionType.LEGACY_ETHEREUM, CHAINID_TESTNET, 1, TINYBARS_57_IN_WEIBARS,
				TINYBARS_2_IN_WEIBARS,  TINYBARS_57_IN_WEIBARS, 1_000_000L, TRUFFLE1_ADDRESS, BigInteger.ZERO, ZERO_BYTES, ZERO_BYTES, 1, new byte[0], new byte[0],
				new byte[0]);

		EthTxData 			signedTx = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);
		EthTxData 			signedTxAgain = EthTxSigs.signMessage(tx, TRUFFLE0_PRIVATE_ECDSA_KEY);
		EthTxData 			signedTx1 = EthTxSigs.signMessage(tx, TRUFFLE1_PRIVATE_ECDSA_KEY);

		EthTxSigs sigs = EthTxSigs.extractSignatures(signedTx);
		EthTxSigs sigsAgain = EthTxSigs.extractSignatures(signedTxAgain);
		EthTxSigs sigs1 = EthTxSigs.extractSignatures(signedTx1);

		assertEquals(sigs.toString(), sigsAgain.toString());
		assertNotEquals(sigs.toString(), sigs1.toString());

		assertDoesNotThrow(() -> sigs.hashCode());
		assertDoesNotThrow(() -> sigsAgain.hashCode());
		assertDoesNotThrow(() -> sigs1.hashCode());

		assertEquals(sigs, sigsAgain);
		assertNotEquals(sigs, sigs1);

	}
}
