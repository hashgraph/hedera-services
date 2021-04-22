package com.hedera.services.yahcli.config;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.yahcli.Yahcli;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.Optional;

import static com.hedera.services.bdd.spec.persistence.SpecKey.readFirstKpFromPem;
import static com.hedera.services.yahcli.output.CommonMessages.COMMON_MESSAGES;

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

	static Optional<File> passFileFor(File pemFile) {
		var absPath = pemFile.getAbsolutePath();
		var passFile = new File(absPath.replace(".pem", ".pass"));
		return passFile.exists() ? Optional.of(passFile) : Optional.empty();
	}

	public static void ensureDir(String loc) {
		File f = new File(loc);
		if (!f.exists()) {
			if (!f.mkdirs()) {
				throw new IllegalStateException("Failed to create directory: " + f.getAbsolutePath());
			}
		}
	}

	static Optional<String> promptForPassphrase(String pemLoc, String prompt, int maxAttempts) {
		var pemFile = new File(pemLoc);
		String fullPrompt = prompt + ": ";
		char[] passphrase;
		while (maxAttempts-- > 0) {
			passphrase = readCandidate(fullPrompt);
			var asString = new String(passphrase);
			if (unlocks(pemFile, asString)) {
				return Optional.of(asString);
			} else {
				if (maxAttempts > 0) {
					System.out.println(
							"Sorry, that isn't it! (Don't worry, still " + maxAttempts + " attempts remaining.)");
				} else {
					return Optional.empty();
				}
			}
		}
		throw new AssertionError("Impossible!");
	}

	static boolean unlocks(File keyFile, String passphrase) {
		try {
			readFirstKpFromPem(keyFile, passphrase);
			return true;
		} catch (Exception ignore) {
			return false;
		}
	}

	private static char[] readCandidate(String prompt) {
		System.out.print(prompt);
		System.out.flush();
		if (System.console() != null) {
			return System.console().readPassword();
		} else {
			var reader = new BufferedReader(new InputStreamReader(System.in));
			try {
				return reader.readLine().toCharArray();
			} catch (IOException e) {
				return new char[0];
			}
		}
	}

	public static ConfigManager configFrom(Yahcli yahcli) throws IOException {
		var config = ConfigManager.from(yahcli);
		config.assertNoMissingDefaults();
		COMMON_MESSAGES.printGlobalInfo(config);
		return config;
	}
}
