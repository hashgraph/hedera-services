package com.hedera.services.store.contracts.repository;

import org.ethereum.util.ByteUtil;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class CompactEncoder {
    private static final byte TERMINATOR = 16;
    private static final Map<Character, Byte> hexMap = new HashMap();

    public CompactEncoder() {
    }

    public static byte[] packNibbles(byte[] nibbles) {
        int terminator = 0;
        if (nibbles[nibbles.length - 1] == 16) {
            terminator = 1;
            nibbles = Arrays.copyOf(nibbles, nibbles.length - 1);
        }

        int oddlen = nibbles.length % 2;
        int flag = 2 * terminator + oddlen;
        byte[] flags;
        if (oddlen != 0) {
            flags = new byte[]{(byte)flag};
            nibbles = org.spongycastle.util.Arrays.concatenate(flags, nibbles);
        } else {
            flags = new byte[]{(byte)flag, 0};
            nibbles = org.spongycastle.util.Arrays.concatenate(flags, nibbles);
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        for(int i = 0; i < nibbles.length; i += 2) {
            buffer.write(16 * nibbles[i] + nibbles[i + 1]);
        }

        return buffer.toByteArray();
    }

    public static boolean hasTerminator(byte[] packedKey) {
        return (packedKey[0] >> 4 & 2) != 0;
    }

    public static byte[] unpackToNibbles(byte[] str) {
        byte[] base = binToNibbles(str);
        base = Arrays.copyOf(base, base.length - 1);
        if (base[0] >= 2) {
            base = ByteUtil.appendByte(base, (byte)16);
        }

        if (base[0] % 2 == 1) {
            base = Arrays.copyOfRange(base, 1, base.length);
        } else {
            base = Arrays.copyOfRange(base, 2, base.length);
        }

        return base;
    }

    public static byte[] binToNibbles(byte[] str) {
        byte[] hexEncoded = Hex.encode(str);
        byte[] hexEncodedTerminated = Arrays.copyOf(hexEncoded, hexEncoded.length + 1);

        for(int i = 0; i < hexEncoded.length; ++i) {
            byte b = hexEncodedTerminated[i];
            hexEncodedTerminated[i] = (Byte)hexMap.get((char)b);
        }

        hexEncodedTerminated[hexEncodedTerminated.length - 1] = 16;
        return hexEncodedTerminated;
    }

    public static byte[] binToNibblesNoTerminator(byte[] str) {
        byte[] hexEncoded = Hex.encode(str);

        for(int i = 0; i < hexEncoded.length; ++i) {
            byte b = hexEncoded[i];
            hexEncoded[i] = (Byte)hexMap.get((char)b);
        }

        return hexEncoded;
    }

    static {
        hexMap.put('0', (byte)0);
        hexMap.put('1', (byte)1);
        hexMap.put('2', (byte)2);
        hexMap.put('3', (byte)3);
        hexMap.put('4', (byte)4);
        hexMap.put('5', (byte)5);
        hexMap.put('6', (byte)6);
        hexMap.put('7', (byte)7);
        hexMap.put('8', (byte)8);
        hexMap.put('9', (byte)9);
        hexMap.put('a', (byte)10);
        hexMap.put('b', (byte)11);
        hexMap.put('c', (byte)12);
        hexMap.put('d', (byte)13);
        hexMap.put('e', (byte)14);
        hexMap.put('f', (byte)15);
    }
}
