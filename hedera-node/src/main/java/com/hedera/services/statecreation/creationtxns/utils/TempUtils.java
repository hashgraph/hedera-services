package com.hedera.services.statecreation.creationtxns.utils;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.AccountID;

import java.util.UUID;
import java.util.stream.Stream;

public class TempUtils {
	public static byte[] randomUtf8Bytes(int n) {
		byte[] data = new byte[n];
		int i = 0;
		while (i < n) {
			byte[] rnd = UUID.randomUUID().toString().getBytes();
			System.arraycopy(rnd, 0, data, i, Math.min(rnd.length, n - 1 - i));
			i += rnd.length;
		}
		return data;
	}

	public static ByteString randomUtf8ByteString(int n) {
		return ByteString.copyFrom(randomUtf8Bytes(n));
	}


	public static AccountID asAccount(String v) {
		long[] nativeParts = asDotDelimitedLongArray(v);
		return AccountID.newBuilder()
				.setShardNum(nativeParts[0])
				.setRealmNum(nativeParts[1])
				.setAccountNum(nativeParts[2])
				.build();
	}

	static long[] asDotDelimitedLongArray(String s) {
		String[] parts = s.split("[.]");
		return Stream.of(parts).mapToLong(Long::valueOf).toArray();
	}

}
