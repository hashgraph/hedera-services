package com.hedera.services.txns.ethereum;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;


public class EthTxDataTest {

	static final String SIGNATURE_ADDRESS = "a94f5374fce5edbc8e2a8697c15331677e6ebf0b";
	static final String SIGNATURE_PUBKEY = "033a514176466fa815ed481ffad09110a2d344f6c9b78c1d14afc351c3a51be33d";
	static final String RAW_TX_TYPE_0 =
			"f864012f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc18180827653820277a0f9fbff985d374be4a55f296915002eec11ac96f1ce2df183adf992baa9390b2fa00c1e867cc960d9c74ec2e6a662b7908ec4c8cc9f3091e886bcefbeb2290fb792";
	static final String RAW_TX_TYPE_2 =
			"02f87082012a022f2f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181880de0b6b3a764000083123456c001a0df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479a01aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66";

	static final String EIP_155_DEMO_ADDRESS = "9d8a62f656a8d1615c1294fd71e9cfb3e4855a4f";
	static final String EIP_155_DEMO_PUBKEY = "024bc2a31265153f07e70e0bab08724e6b85e217f8cd628ceb62974247bb493382";
	static final String EIP155_DEMO =
			"f86c098504a817c800825208943535353535353535353535353535353535353535880de0b6b3a76400008025a028ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa636276a067cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d83";

	@Test
	public void extractFrontierSignature() {
		try {
			var frontierTx = EthTxData.populateEthTxData(Hex.decode(RAW_TX_TYPE_0));
			assertNotNull(frontierTx);
			assertEquals(RAW_TX_TYPE_0, Hex.toHexString(frontierTx.rawTx()));
			assertEquals(EthTxData.EthTransactionType.LEGACY_ETHEREUM, frontierTx.type());
			assertEquals("012a", Hex.toHexString(frontierTx.chainId()));
			assertEquals(1, frontierTx.nonce());
			assertEquals("2f", Hex.toHexString(frontierTx.gasPrice()));
			assertNull(frontierTx.maxPriorityGas());
			assertNull(frontierTx.maxGas());
			assertEquals(98_304L, frontierTx.gasLimit());
			assertEquals("7e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181", Hex.toHexString(frontierTx.to()));
			assertEquals(BigInteger.ZERO, frontierTx.value());
			assertEquals("7653", Hex.toHexString(frontierTx.callData()));
			assertNull(frontierTx.accessList());
			assertEquals(0, frontierTx.recId());
			assertEquals("0277", Hex.toHexString(frontierTx.v()));
			assertEquals("f9fbff985d374be4a55f296915002eec11ac96f1ce2df183adf992baa9390b2f",
					Hex.toHexString(frontierTx.r()));
			assertEquals("0c1e867cc960d9c74ec2e6a662b7908ec4c8cc9f3091e886bcefbeb2290fb792",
					Hex.toHexString(frontierTx.s()));

			var frontierSigs = EthTxSigs.extractSignatures(frontierTx);
			assertNotNull(frontierSigs);

			assertEquals(SIGNATURE_ADDRESS, Hex.toHexString(frontierSigs.address()));
			assertEquals(SIGNATURE_PUBKEY, Hex.toHexString(frontierSigs.publicKey()));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void extractEIP155Signature() {
		var eip155Tx = EthTxData.populateEthTxData(Hex.decode(EIP155_DEMO));
		assertNotNull(eip155Tx);
		assertEquals(EIP155_DEMO, Hex.toHexString(eip155Tx.rawTx()));
		assertEquals(EthTxData.EthTransactionType.LEGACY_ETHEREUM, eip155Tx.type());
		assertEquals("01", Hex.toHexString(eip155Tx.chainId()));
		assertEquals(9, eip155Tx.nonce());
		assertEquals("04a817c800", Hex.toHexString(eip155Tx.gasPrice()));
		assertNull(eip155Tx.maxPriorityGas());
		assertNull(eip155Tx.maxGas());
		assertEquals(21_000L, eip155Tx.gasLimit());
		assertEquals("3535353535353535353535353535353535353535", Hex.toHexString(eip155Tx.to()));
		assertEquals(new BigInteger("0de0b6b3a7640000", 16), eip155Tx.value());
		assertEquals(0, eip155Tx.callData().length);
		assertNull(eip155Tx.accessList());
		assertEquals(0, eip155Tx.recId());
		assertEquals("25", Hex.toHexString(eip155Tx.v()));
		assertEquals("28ef61340bd939bc2195fe537567866003e1a15d3c71ff63e1590620aa636276",
				Hex.toHexString(eip155Tx.r()));
		assertEquals("67cbe9d8997f761aecb703304b3800ccf555c9f3dc64214b297fb1966a3b6d83",
				Hex.toHexString(eip155Tx.s()));

		var eip155Sigs = EthTxSigs.extractSignatures(eip155Tx);
		assertNotNull(eip155Sigs);
		assertEquals(EIP_155_DEMO_ADDRESS, Hex.toHexString(eip155Sigs.address()));
		assertEquals(EIP_155_DEMO_PUBKEY, Hex.toHexString(eip155Sigs.publicKey()));
	}

	@Test
	public void extractLondonSignature() {
		var londonTx = EthTxData.populateEthTxData(Hex.decode(RAW_TX_TYPE_2));
		assertNotNull(londonTx);
		assertEquals(RAW_TX_TYPE_2, Hex.toHexString(londonTx.rawTx()));
		assertEquals(EthTxData.EthTransactionType.EIP1559, londonTx.type());
		assertEquals("012a", Hex.toHexString(londonTx.chainId()));
		assertEquals(2, londonTx.nonce());
		assertNull(londonTx.gasPrice());
		assertEquals("2f", Hex.toHexString(londonTx.maxPriorityGas()));
		assertEquals("2f", Hex.toHexString(londonTx.maxGas()));
		assertEquals(98_304L, londonTx.gasLimit());
		assertEquals("7e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc181", Hex.toHexString(londonTx.to()));
		assertEquals(new BigInteger("0de0b6b3a7640000", 16), londonTx.value());
		assertEquals("123456", Hex.toHexString(londonTx.callData()));
		assertEquals("", Hex.toHexString(londonTx.accessList()));
		assertEquals(1, londonTx.recId());
		assertNull(londonTx.v());
		assertEquals("df48f2efd10421811de2bfb125ab75b2d3c44139c4642837fb1fccce911fd479",
				Hex.toHexString(londonTx.r()));
		assertEquals("1aaf7ae92bee896651dfc9d99ae422a296bf5d9f1ca49b2d96d82b79eb112d66",
				Hex.toHexString(londonTx.s()));

		var londonSigs = EthTxSigs.extractSignatures(londonTx);
		assertNotNull(londonSigs);
		assertEquals(SIGNATURE_ADDRESS, Hex.toHexString(londonSigs.address()));
		assertEquals(SIGNATURE_PUBKEY, Hex.toHexString(londonSigs.publicKey()));
	}

	@Test
	void roundTripFrontier() {
		var expected = Hex.decode(RAW_TX_TYPE_0);
		var frontierTx = EthTxData.populateEthTxData(expected);

		assertNotNull(frontierTx);
		assertArrayEquals(expected, frontierTx.encodeTx());
	}
	
	@Test
	void roundTrip155() {
		var expected = Hex.decode(EIP155_DEMO);
		var tx155 = EthTxData.populateEthTxData(expected);

		assertNotNull(tx155);
		assertArrayEquals(expected, tx155.encodeTx());
	}	

	@Test
	void roundTrip1559() {
		var expected = Hex.decode(RAW_TX_TYPE_2);
		var tx1559 = EthTxData.populateEthTxData(expected);

		assertNotNull(tx1559);
		assertArrayEquals(expected, tx1559.encodeTx());
	}	
}
