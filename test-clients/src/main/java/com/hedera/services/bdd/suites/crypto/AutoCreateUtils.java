package com.hedera.services.bdd.suites.crypto;

import com.google.protobuf.ByteString;
import com.hederahashgraph.api.proto.java.Key;

import java.util.Random;

public class AutoCreateUtils {
	private static final Random rand = new Random();

	public static ByteString randomValidEd25519Alias() {
		byte[] arr = new byte[32];
		rand.nextBytes(arr);
		return Key.newBuilder().setEd25519(ByteString.copyFrom(arr)).build().toByteString();
	}

	public static ByteString randomValidECDSAAlias() {
		byte[] arr = new byte[33];
		rand.nextBytes(arr);
		return Key.newBuilder().setECDSASecp256K1(ByteString.copyFrom(arr)).build().toByteString();
	}
}
