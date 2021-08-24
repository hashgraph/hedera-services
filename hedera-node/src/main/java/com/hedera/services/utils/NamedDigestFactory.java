package com.hedera.services.utils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@FunctionalInterface
public interface NamedDigestFactory {
	MessageDigest forName(String name) throws NoSuchAlgorithmException;
}
