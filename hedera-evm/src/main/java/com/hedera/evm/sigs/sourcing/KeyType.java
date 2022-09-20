package com.hedera.evm.sigs.sourcing;

public enum KeyType {
    ED25519(32),
    ECDSA_SECP256K1(33);

    private final int length;

    KeyType(final int length) {
        this.length = length;
    }

    public int getLength() {
        return length;
    }
}
