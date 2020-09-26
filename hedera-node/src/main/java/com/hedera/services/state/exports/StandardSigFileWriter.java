package com.hedera.services.state.exports;

import com.google.common.primitives.Ints;

import java.io.FileOutputStream;
import java.io.IOException;

import static com.hedera.services.legacy.stream.RecordStream.TYPE_FILE_HASH;
import static com.hedera.services.legacy.stream.RecordStream.TYPE_SIGNATURE;

public class StandardSigFileWriter implements SigFileWriter {
	@Override
	public String writeSigFile(String signedFile, byte[] sig, byte[] signedFileHash) {
		var sigFile = signedFile + "_sig";
		try (FileOutputStream fout = new FileOutputStream(sigFile, false)) {
			fout.write(TYPE_FILE_HASH);
			fout.write(signedFileHash);
			fout.write(TYPE_SIGNATURE);
			fout.write(Ints.toByteArray(sig.length));
			fout.write(sig);
		} catch (IOException e) {
			throw new IllegalArgumentException(String.format("I/O error writing sig of '%s'!", signedFile), e);
		}
		return sigFile;
	}
}
