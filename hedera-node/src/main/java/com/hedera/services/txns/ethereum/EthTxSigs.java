package com.hedera.services.txns.ethereum;

import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.util.Integers;
import com.hedera.services.txns.ethereum.EthTxData.EthTransactionType;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.LongByReference;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import static com.hedera.services.txns.ethereum.EthTxData.EthTransactionType.LEGACY_ETHEREUM;
import static com.hedera.services.txns.ethereum.EthTxData.SECP256K1_EC_COMPRESSED;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.CONTEXT;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.SECP256K1_EC_UNCOMPRESSED;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.secp256k1_ecdsa_recover;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.secp256k1_ecdsa_recoverable_signature_parse_compact;

public record EthTxSigs(byte[] publicKey, byte[] address) {

	public static EthTxSigs extractSignatures(EthTxData ethTx) {
		byte[] message = calculateSingableMessage(ethTx);
		var pubKey = extractSig(ethTx.recId(), ethTx.r(), ethTx.s(), message);
		byte[] address = recoverAddressFromPubKey(pubKey);
		byte[] compressedKey = recoverCompressedPubKey(pubKey);

		return new EthTxSigs(compressedKey, address);
	}


	public static EthTxData signMessage(EthTxData ethTx, byte[] privateKey) {
		byte[] signableMessage = calculateSingableMessage(ethTx);
		final LibSecp256k1.secp256k1_ecdsa_recoverable_signature signature =
				new LibSecp256k1.secp256k1_ecdsa_recoverable_signature();
		LibSecp256k1.secp256k1_ecdsa_sign_recoverable(
				CONTEXT, signature, new Keccak.Digest256().digest(signableMessage), privateKey, null, null);

		final ByteBuffer compactSig = ByteBuffer.allocate(64);
		final IntByReference recId = new IntByReference(0);
		LibSecp256k1.secp256k1_ecdsa_recoverable_signature_serialize_compact(
				LibSecp256k1.CONTEXT, compactSig, recId, signature);
		compactSig.flip();
		final byte[] sig = compactSig.array();

		// wrap in signature object
		final byte[] r = new byte[32];
		System.arraycopy(sig, 0, r, 0, 32);
		final byte[] s = new byte[32];
		System.arraycopy(sig, 32, s, 0, 32);

		BigInteger v;
		if (ethTx.type() == LEGACY_ETHEREUM) {
			if (ethTx.chainId() == null || ethTx.chainId().length == 0) {
				v = BigInteger.valueOf(27 + recId.getValue());
			} else {
				v =
						BigInteger.valueOf(35 + recId.getValue())
								.add(new BigInteger(1, ethTx.chainId()).multiply(BigInteger.TWO));
			}
		} else {
			v = null;
		}

		return new EthTxData(
				ethTx.rawTx(),
				ethTx.type(),
				ethTx.chainId(),
				ethTx.nonce(),
				ethTx.gasPrice(),
				ethTx.maxPriorityGas(),
				ethTx.maxGas(),
				ethTx.gasLimit(),
				ethTx.to(),
				ethTx.value(),
				ethTx.callData(),
				ethTx.accessList(),
				(byte) recId.getValue(),
				v == null ? null : v.toByteArray(),
				r,
				s);
	}

	static byte[] calculateSingableMessage(EthTxData ethTx) {
		byte[] message;
		if (ethTx.type() == LEGACY_ETHEREUM) {
			if (ethTx.chainId() != null) {
				message =
						RLPEncoder.encodeAsList(
								Integers.toBytes(ethTx.nonce()),
								ethTx.gasPrice(),
								Integers.toBytes(ethTx.gasLimit()),
								ethTx.to(),
								Integers.toBytesUnsigned(ethTx.value()),
								ethTx.callData(),
								ethTx.chainId(),
								Integers.toBytes(0),
								Integers.toBytes(0));
			} else {
				message =
						RLPEncoder.encodeAsList(
								Integers.toBytes(ethTx.nonce()),
								ethTx.gasPrice(),
								Integers.toBytes(ethTx.gasLimit()),
								ethTx.to(),
								Integers.toBytesUnsigned(ethTx.value()),
								ethTx.callData());
			}
		} else if (ethTx.type() == EthTransactionType.EIP1559) {
			message =
					RLPEncoder.encodeSequentially(
							Integers.toBytes(2),
							new Object[] {
									ethTx.chainId(),
									Integers.toBytes(ethTx.nonce()),
									ethTx.maxPriorityGas(),
									ethTx.maxGas(),
									Integers.toBytes(ethTx.gasLimit()),
									ethTx.to(),
									Integers.toBytesUnsigned(ethTx.value()),
									ethTx.callData(),
									new Object[0]
							});

		} else {
			throw new RuntimeException("Unsupported transaction type " + ethTx.type());
		}
		return message;
	}

	static byte[] recoverAddressFromPubKey(LibSecp256k1.secp256k1_pubkey pubKey) {
		final ByteBuffer recoveredFullKey = ByteBuffer.allocate(65);
		final LongByReference fullKeySize = new LongByReference(recoveredFullKey.limit());
		LibSecp256k1.secp256k1_ec_pubkey_serialize(
				CONTEXT, recoveredFullKey, fullKeySize, pubKey, SECP256K1_EC_UNCOMPRESSED);

		recoveredFullKey.get(); // recoveryId is not part of the account hash
		var preHash = new byte[64];
		recoveredFullKey.get(preHash, 0, 64);
		var keyHash = new Keccak.Digest256().digest(preHash);
		var address = new byte[20];
		System.arraycopy(keyHash, 12, address, 0, 20);
		return address;
	}

	static byte[] recoverCompressedPubKey(LibSecp256k1.secp256k1_pubkey pubKey) {
		final ByteBuffer recoveredFullKey = ByteBuffer.allocate(33);
		final LongByReference fullKeySize = new LongByReference(recoveredFullKey.limit());
		LibSecp256k1.secp256k1_ec_pubkey_serialize(
				CONTEXT, recoveredFullKey, fullKeySize, pubKey, SECP256K1_EC_COMPRESSED);
		return recoveredFullKey.array();
	}

	private static LibSecp256k1.secp256k1_pubkey extractSig(int recId, byte[] r, byte[] s, byte[] message) {
		byte[] dataHash = new Keccak.Digest256().digest(message);

		byte[] signature = new byte[64];
		System.arraycopy(r, 0, signature, 0, r.length);
		System.arraycopy(s, 0, signature, 32, s.length);

		final LibSecp256k1.secp256k1_ecdsa_recoverable_signature parsedSignature =
				new LibSecp256k1.secp256k1_ecdsa_recoverable_signature();

		if (secp256k1_ecdsa_recoverable_signature_parse_compact(CONTEXT, parsedSignature, signature, recId) == 0) {
			throw new IllegalArgumentException("Could not parse signature");
		}
		final LibSecp256k1.secp256k1_pubkey newPubKey = new LibSecp256k1.secp256k1_pubkey();
		if (secp256k1_ecdsa_recover(CONTEXT, newPubKey, parsedSignature, dataHash) == 0) {
			throw new IllegalArgumentException("Could not recover signature");
		} else {
			return newPubKey;
		}
	}
}
