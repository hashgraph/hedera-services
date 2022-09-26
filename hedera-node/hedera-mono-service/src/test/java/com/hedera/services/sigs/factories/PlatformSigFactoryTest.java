/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.sigs.factories;

import static com.hedera.services.sigs.factories.PlatformSigFactory.allVaryingMaterialEquals;
import static com.hedera.services.sigs.factories.PlatformSigFactory.ed25519Sig;
import static com.hedera.services.sigs.factories.PlatformSigFactory.pkSigRepr;
import static com.hedera.services.sigs.factories.PlatformSigFactory.varyingMaterialEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.utility.CommonUtils;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PlatformSigFactoryTest {
    private static final String PK = "Not really a ed25519 public key!";
    private static final String DIFFERENT_PK = "NOT really a ed25519 public key!";
    private static final String SIG = "Not really an ed25519 signature!";
    private static final String DIFFERENT_SIG = "NOT really an ed25519 signature!";
    private static final String DATA = "Not really a Hedera GRPCA transaction!";
    private static final String DIFFERENT_DATA = "NOT really a Hedera GRPCA transaction!";
    private static final String CONTENTS = SIG + DATA;
    private static final byte[] differentPk = DIFFERENT_PK.getBytes();
    private static final byte[] differentSig = DIFFERENT_SIG.getBytes();
    private static final byte[] differentData = DIFFERENT_DATA.getBytes();

    static final byte[] sig = SIG.getBytes();
    static final byte[] data = DATA.getBytes();
    public static final byte[] pk = PK.getBytes();
    public static final TransactionSignature EXPECTED_SIG =
            new TransactionSignature(
                    CONTENTS.getBytes(), 0, sig.length, pk, 0, pk.length, sig.length, data.length);

    @Test
    void createsExpectedSig() {
        final var actualSig = PlatformSigFactory.ed25519Sig(pk, sig, data);

        Assertions.assertEquals(EXPECTED_SIG, actualSig);
    }

    @Test
    void differentPksAreUnequal() {
        final var a = ed25519Sig(pk, sig, data);
        final var b = ed25519Sig(differentPk, sig, data);

        assertFalse(varyingMaterialEquals(a, b));
    }

    @Test
    void differentSigsAreUnequal() {
        final var a = ed25519Sig(pk, sig, data);
        final var b = ed25519Sig(pk, differentSig, data);

        assertFalse(varyingMaterialEquals(a, b));
    }

    @Test
    void equalVaryingMaterialAreEqual() {
        final var a = ed25519Sig(pk, sig, data);
        final var b = ed25519Sig(pk, sig, differentData);

        assertTrue(varyingMaterialEquals(a, b));
    }

    @Test
    void differentLensAreUnequal() {
        final var a = ed25519Sig(pk, sig, data);

        final var aList = List.of(a);
        final var bList = List.of(a, a);

        assertFalse(allVaryingMaterialEquals(aList, bList));
    }

    @Test
    void differingItemsMeanUnequal() {
        final var a = ed25519Sig(pk, sig, data);
        final var b = ed25519Sig(pk, differentSig, data);

        final var aList = List.of(a, a, a);
        final var bList = List.of(a, b, a);

        assertFalse(allVaryingMaterialEquals(aList, bList));
    }

    @Test
    void pkSigReprWorks() {
        final var a = ed25519Sig(pk, sig, data);
        final var b = ed25519Sig(differentPk, differentSig, data);
        final var expected =
                String.format(
                        "(PK = %s | SIG = %s | UNKNOWN), (PK = %s | SIG = %s | UNKNOWN)",
                        CommonUtils.hex(pk),
                        CommonUtils.hex(sig),
                        CommonUtils.hex(differentPk),
                        CommonUtils.hex(differentSig));
        final var sigs = List.of(a, b);

        final var repr = pkSigRepr(sigs);

        Assertions.assertEquals(expected, repr);
    }
}
