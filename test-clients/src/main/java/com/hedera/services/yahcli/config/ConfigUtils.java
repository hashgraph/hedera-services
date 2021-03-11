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

	public static void ensureDir(String loc) {
		File f = new File(loc);
		if (!f.exists()) {
			if (!f.mkdirs()) {
				throw new IllegalStateException("Failed to create directory: " + f.getAbsolutePath());
			}
		}
	}

	public static void fileExists(String filePath) {
		File f = new File(filePath);
		if(!f.exists()) {
			throw new IllegalStateException("File not found: " + filePath);
		}
	}
}
