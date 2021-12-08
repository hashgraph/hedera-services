package com.hedera.services.bdd.suites.crypto;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.Key;
import org.apache.commons.lang3.RandomStringUtils;

import java.util.Random;

public class AutoCreateUtils {
	private static final Random rand = new Random();
	private static String alias =
			"a479462fba67674b5a41acfb16cb6828626b61d3f389fa611005a45754130e5c749073c0b1b791596430f4a54649cc8a3f6d28147dd4099070a5c3c4811d1771";

	public static ByteString randomValidEd25519Alias() {
		final var alias = RandomStringUtils.random(128, true, true);
		return Key.newBuilder().setEd25519(ByteString.copyFromUtf8(alias)).build().toByteString();
	}

	public static ByteString randomValidECDSAAlias() {
		final var alias = RandomStringUtils.random(128, true, true);
		return Key.newBuilder().setECDSASecp256K1(ByteString.copyFromUtf8(alias)).build().toByteString();
	}

	public static Key asKey(final ByteString alias) {
		Key aliasKey;
		try {
			aliasKey = Key.parseFrom(alias);
		} catch (InvalidProtocolBufferException ex) {
			return Key.newBuilder().build();
		}
		return aliasKey;
	}
}
