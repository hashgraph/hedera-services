package com.hedera.services.sigs.factories;

/*-
 * ‌
 * Hedera Services Node
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

import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.TransactionSignature;
import org.apache.commons.codec.binary.Hex;
import org.junit.jupiter.api.Assertions;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.hedera.services.sigs.factories.PlatformSigFactory.*;
import static org.junit.jupiter.api.Assertions.assertTrue;


public class PlatformSigFactoryTest {
	public static String PK = "Not really a ed25519 public key!";
	public static String DIFFERENT_PK = "NOT really a ed25519 public key!";
	public static String SIG = "Not really an ed25519 signature!";
	public static String DIFFERENT_SIG = "NOT really an ed25519 signature!";
	public static String DATA = "Not really a Hedera GRPCA transaction!";
	public static String DIFFERENT_DATA = "NOT really a Hedera GRPCA transaction!";
	public static String CONTENTS = SIG + DATA;
	public static byte[] pk = PK.getBytes();
	public static byte[] differentPk = DIFFERENT_PK.getBytes();
	public static byte[] sig = SIG.getBytes();
	public static byte[] differentSig = DIFFERENT_SIG.getBytes();
	public static byte[] data = DATA.getBytes();
	public static byte[] differentData = DIFFERENT_DATA.getBytes();
	public static TransactionSignature EXPECTED_SIG = new TransactionSignature(
			CONTENTS.getBytes(),
			0, sig.length,
			pk, 0, pk.length,
			sig.length, data.length);

	@Test
	public void createsExpectedSig() {
		// when:
		TransactionSignature actualSig = PlatformSigFactory.createEd25519(pk, sig, data);

		// then:
		Assertions.assertEquals(EXPECTED_SIG, actualSig);
	}

	@Test
	public void differentPksAreUnequal() {
		// given:
		var a = createEd25519(pk, sig, data);
		var b = createEd25519(differentPk, sig, data);

		// expect:
		assertFalse(varyingMaterialEquals(a, b));
	}

	@Test
	public void differentSigsAreUnequal() {
		// given:
		var a = createEd25519(pk, sig, data);
		var b = createEd25519(pk, differentSig, data);

		// expect:
		assertFalse(varyingMaterialEquals(a, b));
	}

	@Test
	public void equalVaryingMaterialAreEqual() {
		// given:
		var a = createEd25519(pk, sig, data);
		var b = createEd25519(pk, sig, differentData);

		// expect:
		assertTrue(varyingMaterialEquals(a, b));
	}

	@Test
	public void differentLensAreUnequal() {
		// setup:
		var a = createEd25519(pk, sig, data);

		// given:
		var aList = List.of(a);
		var bList = List.of(a, a);

		// then:
		assertFalse(allVaryingMaterialEquals(aList, bList));
	}

	@Test
	public void differingItemsMeanUnequal() {
		// setup:
		var a = createEd25519(pk, sig, data);
		var b = createEd25519(pk, differentSig, data);

		// given:
		var aList = List.of(a, a, a);
		var bList = List.of(a, b, a);

		// then:
		assertFalse(allVaryingMaterialEquals(aList, bList));
	}

	@Test
	public void pkSigReprWorks() {
		// setup:
		var a = createEd25519(pk, sig, data);
		var b = createEd25519(differentPk, differentSig, data);
		String expected = String.format(
				"(PK = %s | SIG = %s | UNKNOWN), (PK = %s | SIG = %s | UNKNOWN)",
				Hex.encodeHexString(pk),
				Hex.encodeHexString(sig),
				Hex.encodeHexString(differentPk),
				Hex.encodeHexString(differentSig));

		// given:
		var sigs = List.of(a, b);

		// when:
		var repr = pkSigRepr(sigs);

		// then:
		Assertions.assertEquals(expected, repr);
	}
}
