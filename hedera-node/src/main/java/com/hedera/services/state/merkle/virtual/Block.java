package com.hedera.services.state.merkle.virtual;

import java.util.Arrays;
import java.util.Objects;

/**
 * Because primitive byte arrays can not be used as keys in collections we need this wrapper
 */
public class Block {
    private final byte[] byteArray;
    private int hashCode;

    public Block(byte[] byteArray) {
        if (byteArray.length != 256) {
            throw new IllegalArgumentException("We only store 256 byte blocks.");
        }
        this.byteArray = Objects.requireNonNull(byteArray);
        this.hashCode = Arrays.hashCode(byteArray);
    }

    public byte[] getByteArray() {
        return byteArray;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Block that = (Block) o;
        return Arrays.equals(byteArray, that.byteArray);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}