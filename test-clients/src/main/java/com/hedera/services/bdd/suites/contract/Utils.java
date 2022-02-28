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
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.crypto.Hash;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.IntStream;

import static com.hedera.services.bdd.suites.contract.Utils.FunctionType.CONSTRUCTOR;
import static java.lang.System.arraycopy;
import static org.apache.commons.lang3.StringUtils.EMPTY;

public class Utils {
	public static final String RESOURCE_PATH = "src/main/resource/contract/contracts/%1$s/%1$s";

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

	public static String getABIFor(final FunctionType type, final String functionName, String contractName) {
		final var path = getResourcePath(contractName, ".json");
		var ABI = EMPTY;
		try (final var input = new FileInputStream(path)) {
			final var array = new JSONArray(new JSONTokener(input));
			ABI = IntStream
					.range(0, array.length())
					.mapToObj(array::getJSONObject)
					.filter(object -> type == CONSTRUCTOR
							? object.getString("type").equals(type.toString().toLowerCase())
							: object.getString("type").equals(type.toString().toLowerCase()) && object.getString("name").equals(functionName))
					.map(JSONObject::toString)
					.findFirst()
					.orElseThrow(() -> new IllegalArgumentException("No such function found: " + functionName));
		} catch (IOException e) {
			e.getStackTrace();
		}
		return ABI;
	}

	public static String getResourcePath(final String resourceName, final String extension) {
		final var path = String.format(RESOURCE_PATH + extension, resourceName);
		final var file = new File(path);
		if (!file.exists()) {
			throw new IllegalArgumentException("Invalid argument: " + path.substring(path.lastIndexOf('/') + 1));
		}
		return path;
	}

	public enum FunctionType {CONSTRUCTOR, FUNCTION}
}
