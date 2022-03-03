package com.hedera.services.bdd.suites.contract;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.CommonUtils;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.Hash;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.hedera.services.bdd.spec.HapiPropertySource.asDotDelimitedLongArray;
import static com.swirlds.common.CommonUtils.unhex;
import static java.lang.System.arraycopy;

public class Utils {
	public static ByteString eventSignatureOf(String event) {
		return ByteString.copyFrom(Hash.keccak256(
				Bytes.wrap(event.getBytes())).toArray());
	}

	public static ByteString parsedToByteString(long n) {
		return ByteString.copyFrom(Bytes32.fromHexStringLenient(Long.toHexString(n)).toArray());
	}

	public static byte[] asAddress(final TokenID id) {
		return asSolidityAddress((int) id.getShardNum(), id.getRealmNum(), id.getTokenNum());
	}

	public static byte[] asAddress(final AccountID id) {
		return asSolidityAddress((int) id.getShardNum(), id.getRealmNum(), id.getAccountNum());
	}

	public static byte[] asAddress(final ContractID id) {
		return asSolidityAddress((int) id.getShardNum(), id.getRealmNum(), id.getContractNum());
	}

	public static byte[] asSolidityAddress(final int shard, final long realm, final long num) {
		final byte[] solidityAddress = new byte[20];

		arraycopy(Ints.toByteArray(shard), 0, solidityAddress, 0, 4);
		arraycopy(Longs.toByteArray(realm), 0, solidityAddress, 4, 8);
		arraycopy(Longs.toByteArray(num), 0, solidityAddress, 12, 8);

		return solidityAddress;
	}

	public static ByteString extractByteCode(String path) {
		try {
			final var bytes = Files.readAllBytes(Path.of(path));
			return ByteString.copyFrom(bytes);
		} catch (IOException e) {
			e.printStackTrace();
			return ByteString.EMPTY;
		}
	}

	public static TokenID asToken(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return TokenID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setTokenNum(nativeParts[2])
				.build();
	}

	public static AccountAmount aaWith(final AccountID account, final long amount) {
		return AccountAmount.newBuilder()
				.setAccountID(account)
				.setAmount(amount)
				.build();
	}

	public static AccountAmount aaWith(final ByteString evmAddress, final long amount) {
		return AccountAmount.newBuilder()
				.setAccountID(accountId(evmAddress))
				.setAmount(amount)
				.build();
	}

	public static AccountAmount aaWith(final String hexedEvmAddress, final long amount) {
		return AccountAmount.newBuilder()
				.setAccountID(accountId(hexedEvmAddress))
				.setAmount(amount)
				.build();
	}

	public static NftTransfer ocWith(final AccountID from, final AccountID to, final long serialNo) {
		return NftTransfer.newBuilder()
				.setSenderAccountID(from)
				.setReceiverAccountID(to)
				.setSerialNumber(serialNo)
				.build();
	}

	public static AccountID accountId(final String hexedEvmAddress) {
		return AccountID.newBuilder().setAlias(ByteString.copyFrom(unhex(hexedEvmAddress))).build();
	}

	public static AccountID accountId(final ByteString evmAddress) {
		return AccountID.newBuilder().setAlias(evmAddress).build();
	}

	public static Key aliasContractIdKey(final String hexedEvmAddress) {
		return Key.newBuilder()
				.setContractID(ContractID.newBuilder()
						.setEvmAddress(ByteString.copyFrom(CommonUtils.unhex(hexedEvmAddress)))
				).build();
	}

	public static Key aliasDelegateContractKey(final String hexedEvmAddress) {
		return Key.newBuilder()
				.setDelegatableContractId(ContractID.newBuilder()
						.setEvmAddress(ByteString.copyFrom(CommonUtils.unhex(hexedEvmAddress)))
				).build();
	}
}
