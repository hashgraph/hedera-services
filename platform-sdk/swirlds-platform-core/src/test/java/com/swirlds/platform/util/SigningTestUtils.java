package com.swirlds.platform.util;

import java.security.KeyPair;
import java.util.Objects;

public class SigningTestUtils {
    /**
     * Loads a key from a keystore in the test resources
     *
     * @return the key
     */
    public static KeyPair loadKey() {
        return FileSigningUtils.loadPfxKey(
                Objects.requireNonNull(FileSigningUtilsTests.class.getResource("testKeyStore.pkcs12"))
                        .getFile(),
                "123456",
                "testKey");
    }
}
