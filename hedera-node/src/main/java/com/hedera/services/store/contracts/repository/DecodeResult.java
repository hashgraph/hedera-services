package com.hedera.services.store.contracts.repository;

import org.bouncycastle.util.encoders.Hex;
import java.io.Serializable;

@SuppressWarnings("serial")
public class DecodeResult implements Serializable {

    private int pos;
    private Object decoded;

    public DecodeResult(int pos, Object decoded) {
        this.pos = pos;
        this.decoded = decoded;
    }

    public int getPos() {
        return pos;
    }

    public Object getDecoded() {
        return decoded;
    }

    public String toString() {
        return asString(this.decoded);
    }

    private String asString(Object decoded) {
        if (decoded instanceof String) {
            return (String) decoded;
        } else if (decoded instanceof byte[]) {
            return Hex.toHexString((byte[]) decoded);
        } else if (decoded instanceof Object[]) {
            StringBuilder result = new StringBuilder();
            for (Object item : (Object[]) decoded) {
                result.append(asString(item));
            }
            return result.toString();
        }
        throw new RuntimeException("Not a valid type. Should not occur");
    }
}
