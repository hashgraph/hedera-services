package com.hedera.services.state.exports;

@FunctionalInterface
public interface SigFileWriter {
	String writeSigFile(String signedFile, byte[] sig, byte[] signedFileHash);
}
