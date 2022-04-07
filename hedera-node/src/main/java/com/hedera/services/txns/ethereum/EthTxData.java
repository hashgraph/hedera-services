package com.hedera.services.txns.ethereum;

import com.esaulpaugh.headlong.rlp.RLPDecoder;
import com.esaulpaugh.headlong.rlp.RLPEncoder;
import com.esaulpaugh.headlong.rlp.RLPItem;
import com.esaulpaugh.headlong.util.Integers;

import java.math.BigInteger;
import java.util.List;


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
		int recId,
		byte[] v,
		byte[] r,
		byte[] s) {

	// TODO constants should be in besu-native
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

	EthTxData replaceCallData(byte[] callData) {
		return new EthTxData(
				null, type, chainId, nonce, gasPrice, maxPriorityGas, maxGas, gasLimit, to, value, callData, accessList,
				recId, v, r, s);

	}

	public byte[] encodeTx() {
		if (accessList != null && accessList.length > 0) {
			throw new RuntimeException("Re-encoding access list is unsupported");
		}
		return switch (type) {
			case LEGACY_ETHEREUM -> RLPEncoder.encodeAsList(
					Integers.toBytes(nonce), gasPrice, Integers.toBytes(gasLimit), to, Integers.toBytesUnsigned(value),
					callData, v, r, s);
			case EIP2930 -> RLPEncoder.encodeSequentially(Integers.toBytes(0x01), List.of(
					chainId, Integers.toBytes(nonce), gasPrice, Integers.toBytes(gasLimit), to,
					Integers.toBytesUnsigned(value), callData, List.of(/*accessList*/), Integers.toBytes(recId), r, s
			));
			case EIP1559 -> RLPEncoder.encodeSequentially(Integers.toBytes(0x02), List.of(
					chainId, Integers.toBytes(nonce), maxPriorityGas, maxGas, Integers.toBytes(gasLimit), to,
					Integers.toBytesUnsigned(value), callData, List.of(/*accessList*/), Integers.toBytes(recId), r, s
			));
		};
	}

	public enum EthTransactionType {
		LEGACY_ETHEREUM,
		EIP2930,
		EIP1559,
	}
}
