package com.hedera.services.state.exports;

public interface FileHashReader {
	byte[] readHash(String targetLoc);
}
