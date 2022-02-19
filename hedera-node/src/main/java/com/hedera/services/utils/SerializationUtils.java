package com.hedera.services.utils;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class SerializationUtils {
	private SerializationUtils()  {
		throw new UnsupportedOperationException("Utility Class");
	}

	public static void serializeCryptoAllowances(
			SerializableDataOutputStream out,
			final Map<EntityNum, Long> cryptoAllowances) throws IOException {
		out.writeInt(cryptoAllowances.size());
		for (Map.Entry<EntityNum, Long> entry : cryptoAllowances.entrySet()) {
			out.writeLong(entry.getKey().longValue());
			out.writeLong(entry.getValue());
		}
	}

	public static void serializeTokenAllowances(
			SerializableDataOutputStream out,
			final Map<FcTokenAllowanceId, Long> fungibleTokenAllowances) throws IOException {
		out.writeInt(fungibleTokenAllowances.size());
		for (Map.Entry<FcTokenAllowanceId, Long> entry : fungibleTokenAllowances.entrySet()) {
			out.writeSerializable(entry.getKey(), true);
			out.writeLong(entry.getValue());
		}
	}

	public static void serializeNftAllowance(
			SerializableDataOutputStream out,
			final Map<FcTokenAllowanceId, FcTokenAllowance> nftAllowances) throws IOException {
		out.writeInt(nftAllowances.size());
		for (Map.Entry<FcTokenAllowanceId, FcTokenAllowance> entry : nftAllowances.entrySet()) {
			out.writeSerializable(entry.getKey(), true);
			out.writeSerializable(entry.getValue(), true);
		}
	}
	public static void serializeAllowances(
			SerializableDataOutputStream out,
			final Map<EntityNum, Long> cryptoAllowances,
			final Map<FcTokenAllowanceId, Long> fungibleTokenAllowances,
			final Map<FcTokenAllowanceId, FcTokenAllowance> nftAllowances) throws IOException {
		out.writeInt(cryptoAllowances.size());
		for (Map.Entry<EntityNum, Long> entry : cryptoAllowances.entrySet()) {
			out.writeLong(entry.getKey().longValue());
			out.writeLong(entry.getValue());
		}
		out.writeInt(fungibleTokenAllowances.size());
		for (Map.Entry<FcTokenAllowanceId, Long> entry : fungibleTokenAllowances.entrySet()) {
			out.writeSerializable(entry.getKey(), true);
			out.writeLong(entry.getValue());
		}
		out.writeInt(nftAllowances.size());
		for (Map.Entry<FcTokenAllowanceId, FcTokenAllowance> entry : nftAllowances.entrySet()) {
			out.writeSerializable(entry.getKey(), true);
			out.writeSerializable(entry.getValue(), true);
		}
	}

	public static Map<EntityNum, Long> deserializeCryptoAllowances(SerializableDataInputStream in) throws IOException {
		Map<EntityNum, Long> cryptoAllowances = Collections.emptyMap();
		var numCryptoAllowances = in.readInt();
		if(numCryptoAllowances > 0){
			cryptoAllowances = new TreeMap<>();
		}
		while (numCryptoAllowances-- > 0) {
			final var entityNum = EntityNum.fromLong(in.readLong());
			final var allowance = in.readLong();
			cryptoAllowances.put(entityNum, allowance);
		}
		return cryptoAllowances;
	}

	public static Map<FcTokenAllowanceId, Long> deserializeFungibleTokenAllowances(SerializableDataInputStream in) throws IOException {
		Map<FcTokenAllowanceId, Long> fungibleTokenAllowances = Collections.emptyMap();
		var numFungibleTokenAllowances = in.readInt();
		if(numFungibleTokenAllowances > 0){
			fungibleTokenAllowances = new TreeMap<>();
		}
		while (numFungibleTokenAllowances-- > 0) {
			final FcTokenAllowanceId fungibleAllowanceId = in.readSerializable();
			final Long value = in.readLong();
			fungibleTokenAllowances.put(fungibleAllowanceId, value);
		}
		return fungibleTokenAllowances;
	}

	public static Map<FcTokenAllowanceId, FcTokenAllowance> deserializeNftAllowances(SerializableDataInputStream in) throws IOException {
		Map<FcTokenAllowanceId, FcTokenAllowance> nftAllowances = Collections.emptyMap();
		var numNftAllowances = in.readInt();
		if(numNftAllowances > 0){
			nftAllowances = new TreeMap<>();
		}
		while (numNftAllowances-- > 0) {
			final FcTokenAllowanceId nftAllowanceId = in.readSerializable();
			final FcTokenAllowance value = in.readSerializable();
			nftAllowances.put(nftAllowanceId, value);
		}
		return nftAllowances;
	}
}
