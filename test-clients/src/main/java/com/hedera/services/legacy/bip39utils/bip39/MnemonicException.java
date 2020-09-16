package com.hedera.services.legacy.bip39utils.bip39;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

/**
 * Exceptions thrown by the Mnemonic module.
 */
@SuppressWarnings("serial")
public class MnemonicException extends Exception {
	public MnemonicException() {
		super();
	}

	public MnemonicException(String msg) {
		super(msg);
	}

	/**
	 * Thrown when an argument to Mnemonic is the wrong length.
	 */
	public static class MnemonicLengthException extends MnemonicException {
		public MnemonicLengthException(String msg) {
			super(msg);
		}
	}

	/**
	 * Thrown when a list of Mnemonic words fails the checksum check.
	 */
	public static class MnemonicChecksumException extends MnemonicException {
		public MnemonicChecksumException() {
			super();
		}
	}

	/**
	 * Thrown when a word is encountered which is not in the Mnemonic's word list.
	 */
	public static class MnemonicWordException extends MnemonicException {
		/** Contains the word that was not found in the word list. */
		public final String badWord;

		public MnemonicWordException(String badWord) {
			super();
			this.badWord = badWord;
		}
	}
}
