package com.hedera.services.txns.ethereum;

import com.esaulpaugh.headlong.rlp.RLPDecoder;
import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.rlp.RLPItem;
import com.esaulpaugh.headlong.util.Integers;
import com.sun.jna.ptr.LongByReference;
import org.bouncycastle.jcajce.provider.digest.Keccak;
import org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;

import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.CONTEXT;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.SECP256K1_EC_UNCOMPRESSED;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.secp256k1_ecdsa_recover;
import static org.hyperledger.besu.nativelib.secp256k1.LibSecp256k1.secp256k1_ecdsa_recoverable_signature_parse_compact;


public record EthTxData(
		byte[] rawTx,
		EthTransactionType type,
		byte[] chainId,
		long nonce,
		byte[] gasPrice,
		byte[] maxPriorityGas,
		byte[] maxGas,
		long gasLimit,
		byte[] to,
		BigInteger value,
		byte[] callData,
		byte[] accessList,
		byte recId,
		byte[] v,
		byte[] r,
		byte[] s) {
	// TODO constant should be in besu-native
	static final int SECP256K1_FLAGS_TYPE_COMPRESSION = 1 << 1;
	static final int SECP256K1_FLAGS_BIT_COMPRESSION = 1 << 8;
	static final int SECP256K1_EC_COMPRESSED = (SECP256K1_FLAGS_TYPE_COMPRESSION | SECP256K1_FLAGS_BIT_COMPRESSION);

	public static EthTxData populateEthTxData(byte[] data) {

		var decoder = RLPDecoder.RLP_STRICT.sequenceIterator(data);
		var rlpItem = decoder.next();
		EthTransactionType type;
		long nonce;
		byte[] chainId = null;
		byte[] gasPrice = null;
		byte[] maxPriorityGas = null;
		byte[] maxGas = null;
		long gasLimit;
		byte[] to;
		BigInteger value;
		byte[] callData;
		byte[] accessList = null;
		byte recId;
		byte[] v = null;
		byte[] r;
		byte[] s;
		if (rlpItem.isList()) {
			// frontier TX
			type = EthTransactionType.LEGACY_ETHEREUM;
			List<RLPItem> rlpList = rlpItem.asRLPList().elements();
			if (rlpList.size() != 9) {
				return null;
			}
			nonce = rlpList.get(0).asLong();
			gasPrice = rlpList.get(1).asBytes();
			gasLimit = rlpList.get(2).asLong();
			to = rlpList.get(3).data();
			value = rlpList.get(4).asBigInt();
			callData = rlpList.get(5).data();
			v = rlpList.get(6).asBytes();
			BigInteger vBI = new BigInteger(1, v);
			recId = vBI.testBit(0) ? (byte) 0 : 1;
			r = rlpList.get(7).data();
			s = rlpList.get(8).data();
			if (vBI.compareTo(BigInteger.valueOf(34)) > 0) {
				chainId = vBI.subtract(BigInteger.valueOf(35)).shiftRight(1).toByteArray();
			}
		} else {
			// typed transaction?
			byte typeByte = rlpItem.asByte();
			if (typeByte != 2) {
				// we only support EIP1559 at the moment.
				return null;
			}
			type = EthTransactionType.EIP1559;
			rlpItem = decoder.next();
			if (!rlpItem.isList()) {
				return null;
			}
			List<RLPItem> rlpList = rlpItem.asRLPList().elements();
			if (rlpList.size() != 12) {
				return null;
			}
			chainId = rlpList.get(0).data();
			nonce = rlpList.get(1).asLong();
			maxPriorityGas = rlpList.get(2).data();
			maxGas = rlpList.get(3).data();
			gasLimit = rlpList.get(4).asLong();
			to = rlpList.get(5).data();
			value = rlpList.get(6).asBigInt();
			callData = rlpList.get(7).data();
			accessList = rlpList.get(8).data();
			recId = rlpList.get(9).asByte();
			r = rlpList.get(10).data();
			s = rlpList.get(11).data();
		}

		return new EthTxData(
				data,
				type,
				chainId,
				nonce,
				gasPrice,
				maxPriorityGas,
				maxGas,
				gasLimit,
				to,
				value,
				callData,
				accessList,
				recId,
				v,
				r,
				s);
	}
	
	EthTxData relpaceCallData(byte[] callData) {
		return new EthTxData(
				rawTx, type, chainId, nonce, gasPrice, maxPriorityGas, maxGas, gasLimit, to, value, callData, accessList, recId, v, r, s); 
	
	}

	EthTxSigs extractSignatures() {
		byte[] message = calculateSingableMessage();
		var pubKey = extractSig(recId, r, s, message);
		byte[] address = recoverAddressFromPubKey(pubKey);
		byte[] compressedKey = recoverCompressedPubKey(pubKey);

		return new EthTxSigs(compressedKey, address);
	}

	private byte[] calculateSingableMessage() {
		byte[] message;
		if (type == EthTransactionType.LEGACY_ETHEREUM) {
			if (chainId != null) {
				message =
						RLPEncoder.encodeAsList(
								Integers.toBytes(nonce),
								gasPrice,
								Integers.toBytes(gasLimit),
								to,
								Integers.toBytesUnsigned(value),
								callData,
								chainId,
								Integers.toBytes(0),
								Integers.toBytes(0));
			} else {
				message =
						RLPEncoder.encodeAsList(
								Integers.toBytes(nonce),
								gasPrice,
								Integers.toBytes(gasLimit),
								to,
								Integers.toBytesUnsigned(value),
								callData);
			}
		} else if (type == EthTransactionType.EIP1559) {
			message =
					RLPEncoder.encodeSequentially(
							Integers.toBytes(2),
							new Object[] {
									chainId,
									Integers.toBytes(nonce),
									maxPriorityGas,
									maxGas,
									Integers.toBytes(gasLimit),
									to,
									Integers.toBytesUnsigned(value),
									callData,
									new Object[0]
							});

		} else {
			throw new RuntimeException("Unsupported transaction type " + type);
		}
		return message;
	}

	byte[] recoverAddressFromPubKey(LibSecp256k1.secp256k1_pubkey pubKey) {
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

	byte[] recoverCompressedPubKey(LibSecp256k1.secp256k1_pubkey pubKey) {
		final ByteBuffer recoveredFullKey = ByteBuffer.allocate(33);
		final LongByReference fullKeySize = new LongByReference(recoveredFullKey.limit());
		LibSecp256k1.secp256k1_ec_pubkey_serialize(
				CONTEXT, recoveredFullKey, fullKeySize, pubKey, SECP256K1_EC_COMPRESSED);
		return recoveredFullKey.array();
	}

	private LibSecp256k1.secp256k1_pubkey extractSig(int recId, byte[] r, byte[] s, byte[] message) {
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


	public enum EthTransactionType {
		LEGACY_ETHEREUM,
		EIP2930,
		EIP1559,
	}


}
