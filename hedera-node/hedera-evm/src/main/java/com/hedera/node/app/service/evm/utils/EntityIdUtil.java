package com.hedera.node.app.service.evm.utils;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.utility.CommonUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import java.util.Arrays;

import static java.lang.System.arraycopy;

public class EntityIdUtil {
	public static final int EVM_ADDRESS_SIZE = 20;
	public static byte[] asEvmAddress(final ContractID id) {
		if (id.getEvmAddress().size() == EVM_ADDRESS_SIZE) {
			return id.getEvmAddress().toByteArray();
		} else {
			return asEvmAddress((int) id.getShardNum(), id.getRealmNum(), id.getContractNum());
		}
	}

	public static byte[] asEvmAddress(final AccountID id) {
		return asEvmAddress((int) id.getShardNum(), id.getRealmNum(), id.getAccountNum());
	}

	public static byte[] asEvmAddress(final TokenID id) {
		return asEvmAddress((int) id.getShardNum(), id.getRealmNum(), id.getTokenNum());
	}

	public static Address asTypedEvmAddress(final AccountID id) {
		return Address.wrap(Bytes.wrap(asEvmAddress(id)));
	}

	public static Address asTypedEvmAddress(final ContractID id) {
		return Address.wrap(Bytes.wrap(asEvmAddress(id)));
	}

	public static Address asTypedEvmAddress(final TokenID id) {
		return Address.wrap(Bytes.wrap(asEvmAddress(id)));
	}

	public static byte[] asEvmAddress(final int shard, final long realm, final long num) {
		final byte[] evmAddress = new byte[20];

		arraycopy(Ints.toByteArray(shard), 0, evmAddress, 0, 4);
		arraycopy(Longs.toByteArray(realm), 0, evmAddress, 4, 8);
		arraycopy(Longs.toByteArray(num), 0, evmAddress, 12, 8);

		return evmAddress;
	}

	public static long shardFromEvmAddress(final byte[] bytes) {
		return Ints.fromByteArray(Arrays.copyOfRange(bytes, 0, 4));
	}

	public static long realmFromEvmAddress(final byte[] bytes) {
		return Longs.fromByteArray(Arrays.copyOfRange(bytes, 4, 12));
	}

	public static long numFromEvmAddress(final byte[] bytes) {
		return Longs.fromBytes(
				bytes[12], bytes[13], bytes[14], bytes[15], bytes[16], bytes[17], bytes[18],
				bytes[19]);
	}

	public static AccountID accountIdFromEvmAddress(final Address address) {
		return accountIdFromEvmAddress(address.toArrayUnsafe());
	}

	public static ContractID contractIdFromEvmAddress(final Address address) {
		return contractIdFromEvmAddress(address.toArrayUnsafe());
	}

	public static TokenID tokenIdFromEvmAddress(final Address address) {
		return tokenIdFromEvmAddress(address.toArrayUnsafe());
	}

	public static AccountID accountIdFromEvmAddress(final byte[] bytes) {
		return AccountID.newBuilder()
				.setShardNum(shardFromEvmAddress(bytes))
				.setRealmNum(realmFromEvmAddress(bytes))
				.setAccountNum(numFromEvmAddress(bytes))
				.build();
	}

	public static ContractID contractIdFromEvmAddress(final byte[] bytes) {
		return ContractID.newBuilder()
				.setShardNum(Ints.fromByteArray(Arrays.copyOfRange(bytes, 0, 4)))
				.setRealmNum(Longs.fromByteArray(Arrays.copyOfRange(bytes, 4, 12)))
				.setContractNum(Longs.fromByteArray(Arrays.copyOfRange(bytes, 12, 20)))
				.build();
	}

	public static TokenID tokenIdFromEvmAddress(final byte[] bytes) {
		return TokenID.newBuilder()
				.setShardNum(Ints.fromByteArray(Arrays.copyOfRange(bytes, 0, 4)))
				.setRealmNum(Longs.fromByteArray(Arrays.copyOfRange(bytes, 4, 12)))
				.setTokenNum(Longs.fromByteArray(Arrays.copyOfRange(bytes, 12, 20)))
				.build();
	}
	public static Address addressFromBytes(final byte[] bytes) {
		return Address.wrap(Bytes.wrap(bytes));
	}
}
