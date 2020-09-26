package com.hedera.services.state.exports;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Sha384HashReader implements FileHashReader {
	private static final MessageDigest SHA384_MD;
	static {
		try {
			SHA384_MD = MessageDigest.getInstance("SHA-384");
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-384 not supported by Java API!");
		}
	}

	@Override
	public byte[] readHash(String targetLoc) {
		try {
			byte[] data = Files.readAllBytes(Paths.get(targetLoc));
			return SHA384_MD.digest(data);
		} catch (IOException e) {
			throw new IllegalArgumentException(String.format("I/O error reading hash of '%s'!", targetLoc), e);
		}
	}
}
