package com.hedera.services.recordstreaming;

import com.hedera.services.stream.proto.RecordStreamFile;
import com.hedera.services.stream.proto.SignatureFile;
import org.apache.commons.lang3.tuple.Pair;

import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.util.Optional;

public class RecordStreamFileParser {
	private RecordStreamFileParser(){}

	public static Pair<Integer, Optional<RecordStreamFile>> readRecordStreamFile(final String fileLoc) {
		try (final var fin = new FileInputStream(fileLoc)) {
			final int recordFileVersion = ByteBuffer.wrap(fin.readNBytes(4)).getInt();
			final var recordStreamFile = RecordStreamFile.parseFrom(fin);
			return Pair.of(recordFileVersion, Optional.ofNullable(recordStreamFile));
		} catch (Exception e) {
			return Pair.of(-1, Optional.empty());
		}
	}

	public static Optional<SignatureFile> readRecordStreamSignatureFile(final String fileLoc) {
		try (final var fin = new FileInputStream(fileLoc)) {
			final var recordStreamSignatureFile = SignatureFile.parseFrom(fin);
			return Optional.ofNullable(recordStreamSignatureFile);
		} catch (Exception e) {
			return Optional.empty();
		}
	}
}
