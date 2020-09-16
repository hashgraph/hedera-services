package com.hedera.services.legacy.bip39utils;

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

import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.SecretKeySpec;

public class SLIP10 {

	private SLIP10() {
	}

	private static final String hmacSHA512algorithm = "HmacSHA512";

	/**
	 * Derives only the private key for ED25519 in the manor defined in
	 * <a href="https://github.com/satoshilabs/slips/blob/master/slip-0010.md">SLIP-0010</a>.
	 *
	 * @param seed
	 * 		Seed, the BIP0039 output.
	 * @param indexes
	 * 		an array of indexes that define the path. E.g. for m/1'/2'/3', pass 1, 2, 3.
	 * 		As with Ed25519 non-hardened child indexes are not supported, this function treats all indexes
	 * 		as hardened.
	 * @return Private key.
	 * @throws NoSuchAlgorithmException
	 * 		If it cannot find the HmacSHA512 algorithm by name.
	 * @throws ShortBufferException
	 * 		Occurrence not expected.
	 * @throws InvalidKeyException
	 * 		Occurrence not expected.
	 */
	public static byte[] deriveEd25519PrivateKey(final byte[] seed, final int... indexes)
			throws NoSuchAlgorithmException, ShortBufferException, InvalidKeyException {

		final byte[] I = new byte[64];
		final Mac mac = Mac.getInstance(hmacSHA512algorithm);

		// I = HMAC-SHA512(Key = bytes("ed25519 seed"), Data = seed)
		mac.init(new SecretKeySpec("ed25519 seed".getBytes(Charset.forName("UTF-8")), hmacSHA512algorithm));
		mac.update(seed);
		mac.doFinal(I, 0);

		for (int i : indexes) {
			// I = HMAC-SHA512(Key = c_par, Data = 0x00 || ser256(k_par) || ser32(i'))
			// which is simply:
			// I = HMAC-SHA512(Key = Ir, Data = 0x00 || Il || ser32(i'))
			// Key = Ir
			mac.init(new SecretKeySpec(I, 32, 32, hmacSHA512algorithm));
			// Data = 0x00
			mac.update((byte) 0x00);
			// Data += Il
			mac.update(I, 0, 32);
			// Data += ser32(i')
			mac.update((byte) (i >> 24 | 0x80));
			mac.update((byte) (i >> 16));
			mac.update((byte) (i >> 8));
			mac.update((byte) i);
			// Write to I
			mac.doFinal(I, 0);
		}

		final byte[] Il = new byte[32];
		// copy head 32 bytes of I into Il
		System.arraycopy(I, 0, Il, 0, 32);
		return Il;
	}
}
