package com.hedera.services.yahcli.config;

import java.io.File;
import java.nio.file.Paths;
import java.util.Optional;

public class ConfigUtils {
	public static String asId(String entity) {
		try {
			int number = Integer.parseInt(entity);
			return "0.0." + number;
		} catch (NumberFormatException ignore) {}
		return entity;
	}

	public static boolean isLiteral(String entity) {
		return entity.startsWith("0.0.");
	}

	public static Optional<File> keyFileFor(String keysLoc, String typedNum) {
		var pemFile = Paths.get(keysLoc, typedNum + ".pem").toFile();
		if (pemFile.exists()) {
			return Optional.of(pemFile);
		}

		var wordsFile = Paths.get(keysLoc, typedNum + ".words").toFile();
		if (wordsFile.exists()) {
			return Optional.of(wordsFile);
		}

		return Optional.empty();
	}

	public static Optional<File> passFileFor(File pemFile) {
		var absPath = pemFile.getAbsolutePath();
		var passFile = new File(absPath.replace(".pem", ".pass"));
		return passFile.exists() ? Optional.of(passFile) : Optional.empty();
	}
}
