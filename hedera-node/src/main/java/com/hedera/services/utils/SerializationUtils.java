package com.hedera.services.utils;

import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;

import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;

final public class SerializationUtils {
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

	public static void deserializeAllowances(
			SerializableDataInputStream in,
			Map<EntityNum, Long> cryptoAllowances,
			Map<FcTokenAllowanceId, Long> fungibleTokenAllowances,
			Map<FcTokenAllowanceId, FcTokenAllowance> nftAllowances) throws IOException {
		var numCryptoAllowances = in.readInt();
		if(numCryptoAllowances > 0){
			cryptoAllowances = new TreeMap<>();
		}
		while (numCryptoAllowances-- > 0) {
			final var entityNum = EntityNum.fromLong(in.readLong());
			final var allowance = in.readLong();
			cryptoAllowances.put(entityNum, allowance);
		}

		var numFungibleTokenAllowances = in.readInt();
		if(numFungibleTokenAllowances > 0){
			fungibleTokenAllowances = new TreeMap<>();
		}
		while (numFungibleTokenAllowances-- > 0) {
			final FcTokenAllowanceId fungibleAllowanceId = in.readSerializable();
			final Long value = in.readLong();
			fungibleTokenAllowances.put(fungibleAllowanceId, value);
		}

		var numNftAllowances = in.readInt();
		if(numNftAllowances > 0){
			nftAllowances = new TreeMap<>();
		}
		while (numNftAllowances-- > 0) {
			final FcTokenAllowanceId nftAllowanceId = in.readSerializable();
			final FcTokenAllowance value = in.readSerializable();
			nftAllowances.put(nftAllowanceId, value);
		}
	}
}
