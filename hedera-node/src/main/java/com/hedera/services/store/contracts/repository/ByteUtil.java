package com.hedera.services.store.contracts.repository;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.spongycastle.util.encoders.Hex;

public class ByteUtil {
    public static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    public static final byte[] ZERO_BYTE_ARRAY = new byte[]{0};

    public ByteUtil() {
    }

    public static byte[] appendByte(byte[] bytes, byte b) {
        byte[] result = Arrays.copyOf(bytes, bytes.length + 1);
        result[result.length - 1] = b;
        return result;
    }

    public static byte[] bigIntegerToBytes(BigInteger b, int numBytes) {
        if (b == null) {
            return null;
        } else {
            byte[] bytes = new byte[numBytes];
            byte[] biBytes = b.toByteArray();
            int start = biBytes.length == numBytes + 1 ? 1 : 0;
            int length = Math.min(biBytes.length, numBytes);
            System.arraycopy(biBytes, start, bytes, numBytes - length, length);
            return bytes;
        }
    }

    public static byte[] bigIntegerToBytesSigned(BigInteger b, int numBytes) {
        if (b == null) {
            return null;
        } else {
            byte[] bytes = new byte[numBytes];
            Arrays.fill(bytes, (byte)(b.signum() < 0 ? -1 : 0));
            byte[] biBytes = b.toByteArray();
            int start = biBytes.length == numBytes + 1 ? 1 : 0;
            int length = Math.min(biBytes.length, numBytes);
            System.arraycopy(biBytes, start, bytes, numBytes - length, length);
            return bytes;
        }
    }

    public static byte[] bigIntegerToBytes(BigInteger value) {
        if (value == null) {
            return null;
        } else {
            byte[] data = value.toByteArray();
            if (data.length != 1 && data[0] == 0) {
                byte[] tmp = new byte[data.length - 1];
                System.arraycopy(data, 1, tmp, 0, tmp.length);
                data = tmp;
            }

            return data;
        }
    }

    public static BigInteger bytesToBigInteger(byte[] bb) {
        return bb != null && bb.length != 0 ? new BigInteger(1, bb) : BigInteger.ZERO;
    }

    public static int matchingNibbleLength(byte[] a, byte[] b) {
        int i = 0;

        for(int length = a.length < b.length ? a.length : b.length; i < length; ++i) {
            if (a[i] != b[i]) {
                return i;
            }
        }

        return i;
    }

    public static byte[] longToBytes(long val) {
        return ByteBuffer.allocate(8).putLong(val).array();
    }

    public static byte[] longToBytesNoLeadZeroes(long val) {
        if (val == 0L) {
            return EMPTY_BYTE_ARRAY;
        } else {
            byte[] data = ByteBuffer.allocate(8).putLong(val).array();
            return stripLeadingZeroes(data);
        }
    }

    public static byte[] intToBytes(int val) {
        return ByteBuffer.allocate(4).putInt(val).array();
    }

    public static byte[] intToBytesNoLeadZeroes(int val) {
        if (val == 0) {
            return EMPTY_BYTE_ARRAY;
        } else {
            int lenght = 0;

            for(int tmpVal = val; tmpVal != 0; ++lenght) {
                tmpVal >>>= 8;
            }

            byte[] result = new byte[lenght];

            for(int index = result.length - 1; val != 0; --index) {
                result[index] = (byte)(val & 255);
                val >>>= 8;
            }

            return result;
        }
    }

    public static String toHexString(byte[] data) {
        return data == null ? "" : Hex.toHexString(data);
    }

    public static byte[] calcPacketLength(byte[] msg) {
        int msgLen = msg.length;
        return new byte[]{(byte)(msgLen >> 24 & 255), (byte)(msgLen >> 16 & 255), (byte)(msgLen >> 8 & 255), (byte)(msgLen & 255)};
    }

    public static int byteArrayToInt(byte[] b) {
        return b != null && b.length != 0 ? (new BigInteger(1, b)).intValue() : 0;
    }

    public static long byteArrayToLong(byte[] b) {
        return b != null && b.length != 0 ? (new BigInteger(1, b)).longValue() : 0L;
    }

    public static String nibblesToPrettyString(byte[] nibbles) {
        StringBuilder builder = new StringBuilder();
        byte[] var2 = nibbles;
        int var3 = nibbles.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            byte nibble = var2[var4];
            String nibbleString = oneByteToHexString(nibble);
            builder.append("\\x").append(nibbleString);
        }

        return builder.toString();
    }

    public static String oneByteToHexString(byte value) {
        String retVal = Integer.toString(value & 255, 16);
        if (retVal.length() == 1) {
            retVal = "0" + retVal;
        }

        return retVal;
    }

    public static int numBytes(String val) {
        BigInteger bInt = new BigInteger(val);

        int bytes;
        for(bytes = 0; !bInt.equals(BigInteger.ZERO); ++bytes) {
            bInt = bInt.shiftRight(8);
        }

        if (bytes == 0) {
            ++bytes;
        }

        return bytes;
    }

    public static byte[] encodeValFor32Bits(Object arg) {
        byte[] data;
        if (arg.toString().trim().matches("-?\\d+(\\.\\d+)?")) {
            data = (new BigInteger(arg.toString().trim())).toByteArray();
        } else if (arg.toString().trim().matches("0[xX][0-9a-fA-F]+")) {
            data = (new BigInteger(arg.toString().trim().substring(2), 16)).toByteArray();
        } else {
            data = arg.toString().trim().getBytes();
        }

        if (data.length > 32) {
            throw new RuntimeException("values can't be more than 32 byte");
        } else {
            byte[] val = new byte[32];
            int j = 0;

            for(int i = data.length; i > 0; --i) {
                val[31 - j] = data[i - 1];
                ++j;
            }

            return val;
        }
    }

    public static byte[] encodeDataList(Object... args) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Object[] var2 = args;
        int var3 = args.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            Object arg = var2[var4];
            byte[] val = encodeValFor32Bits(arg);

            try {
                baos.write(val);
            } catch (IOException var8) {
                throw new Error("Happen something that should never happen ", var8);
            }
        }

        return baos.toByteArray();
    }

    public static int firstNonZeroByte(byte[] data) {
        for(int i = 0; i < data.length; ++i) {
            if (data[i] != 0) {
                return i;
            }
        }

        return -1;
    }

    public static byte[] stripLeadingZeroes(byte[] data) {
        if (data == null) {
            return null;
        } else {
            int firstNonZero = firstNonZeroByte(data);
            switch(firstNonZero) {
                case -1:
                    return ZERO_BYTE_ARRAY;
                case 0:
                    return data;
                default:
                    byte[] result = new byte[data.length - firstNonZero];
                    System.arraycopy(data, firstNonZero, result, 0, data.length - firstNonZero);
                    return result;
            }
        }
    }

//    public static boolean increment(byte[] bytes) {
//        int startIndex = false;
//
//        int i;
//        for(i = bytes.length - 1; i >= 0; --i) {
//            ++bytes[i];
//            if (bytes[i] != 0) {
//                break;
//            }
//        }
//
//        return i >= 0 || bytes[0] != 0;
//    }

    public static byte[] copyToArray(BigInteger value) {
        byte[] src = bigIntegerToBytes(value);
        byte[] dest = ByteBuffer.allocate(32).array();
        System.arraycopy(src, 0, dest, dest.length - src.length, src.length);
        return dest;
    }

    public static ByteArrayWrapper wrap(byte[] data) {
        return new ByteArrayWrapper(data);
    }

    public static byte[] setBit(byte[] data, int pos, int val) {
        if (data.length * 8 - 1 < pos) {
            throw new Error("outside byte array limit, pos: " + pos);
        } else {
            int posByte = data.length - 1 - pos / 8;
            int posBit = pos % 8;
            byte setter = (byte)(1 << posBit);
            byte toBeSet = data[posByte];
            byte result;
            if (val == 1) {
                result = (byte)(toBeSet | setter);
            } else {
                result = (byte)(toBeSet & ~setter);
            }

            data[posByte] = result;
            return data;
        }
    }

    public static int getBit(byte[] data, int pos) {
        if (data.length * 8 - 1 < pos) {
            throw new Error("outside byte array limit, pos: " + pos);
        } else {
            int posByte = data.length - 1 - pos / 8;
            int posBit = pos % 8;
            byte dataByte = data[posByte];
            return Math.min(1, dataByte & 1 << posBit);
        }
    }

    public static byte[] and(byte[] b1, byte[] b2) {
        if (b1.length != b2.length) {
            throw new RuntimeException("Array sizes differ");
        } else {
            byte[] ret = new byte[b1.length];

            for(int i = 0; i < ret.length; ++i) {
                ret[i] = (byte)(b1[i] & b2[i]);
            }

            return ret;
        }
    }

    public static byte[] or(byte[] b1, byte[] b2) {
        if (b1.length != b2.length) {
            throw new RuntimeException("Array sizes differ");
        } else {
            byte[] ret = new byte[b1.length];

            for(int i = 0; i < ret.length; ++i) {
                ret[i] = (byte)(b1[i] | b2[i]);
            }

            return ret;
        }
    }

    public static byte[] xor(byte[] b1, byte[] b2) {
        if (b1.length != b2.length) {
            throw new RuntimeException("Array sizes differ");
        } else {
            byte[] ret = new byte[b1.length];

            for(int i = 0; i < ret.length; ++i) {
                ret[i] = (byte)(b1[i] ^ b2[i]);
            }

            return ret;
        }
    }

    public static byte[] xorAlignRight(byte[] b1, byte[] b2) {
        byte[] b1_;
        if (b1.length > b2.length) {
            b1_ = new byte[b1.length];
            System.arraycopy(b2, 0, b1_, b1.length - b2.length, b2.length);
            b2 = b1_;
        } else if (b2.length > b1.length) {
            b1_ = new byte[b2.length];
            System.arraycopy(b1, 0, b1_, b2.length - b1.length, b1.length);
            b1 = b1_;
        }

        return xor(b1, b2);
    }

    public static byte[] merge(byte[]... arrays) {
        int count = 0;
        byte[][] var2 = arrays;
        int start = arrays.length;

        for(int var4 = 0; var4 < start; ++var4) {
            byte[] array = var2[var4];
            count += array.length;
        }

        byte[] mergedArray = new byte[count];
        start = 0;
        byte[][] var9 = arrays;
        int var10 = arrays.length;

        for(int var6 = 0; var6 < var10; ++var6) {
            byte[] array = var9[var6];
            System.arraycopy(array, 0, mergedArray, start, array.length);
            start += array.length;
        }

        return mergedArray;
    }

    public static boolean isNullOrZeroArray(byte[] array) {
        return array == null || array.length == 0;
    }

    public static boolean isSingleZero(byte[] array) {
        return array.length == 1 && array[0] == 0;
    }

    public static Set<byte[]> difference(Set<byte[]> setA, Set<byte[]> setB) {
        Set<byte[]> result = new HashSet();
        Iterator var3 = setA.iterator();

        while(var3.hasNext()) {
            byte[] elementA = (byte[])var3.next();
            boolean found = false;
            Iterator var6 = setB.iterator();

            while(var6.hasNext()) {
                byte[] elementB = (byte[])var6.next();
                if (Arrays.equals(elementA, elementB)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                result.add(elementA);
            }
        }

        return result;
    }

    public static int length(byte[]... bytes) {
        int result = 0;
        byte[][] var2 = bytes;
        int var3 = bytes.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            byte[] array = var2[var4];
            result += array == null ? 0 : array.length;
        }

        return result;
    }

    public static byte[] intsToBytes(int[] arr, boolean bigEndian) {
        byte[] ret = new byte[arr.length * 4];
        intsToBytes(arr, ret, bigEndian);
        return ret;
    }

    public static int[] bytesToInts(byte[] arr, boolean bigEndian) {
        int[] ret = new int[arr.length / 4];
        bytesToInts(arr, ret, bigEndian);
        return ret;
    }

    public static void bytesToInts(byte[] b, int[] arr, boolean bigEndian) {
        int off;
        int i;
        int ii;
        if (!bigEndian) {
            off = 0;

            for(i = 0; i < arr.length; ++i) {
                ii = b[off++] & 255;
                ii |= b[off++] << 8 & '\uff00';
                ii |= b[off++] << 16 & 16711680;
                ii |= b[off++] << 24;
                arr[i] = ii;
            }
        } else {
            off = 0;

            for(i = 0; i < arr.length; ++i) {
                ii = b[off++] << 24;
                ii |= b[off++] << 16 & 16711680;
                ii |= b[off++] << 8 & '\uff00';
                ii |= b[off++] & 255;
                arr[i] = ii;
            }
        }

    }

    public static void intsToBytes(int[] arr, byte[] b, boolean bigEndian) {
        int off;
        int i;
        int ii;
        if (!bigEndian) {
            off = 0;

            for(i = 0; i < arr.length; ++i) {
                ii = arr[i];
                b[off++] = (byte)(ii & 255);
                b[off++] = (byte)(ii >> 8 & 255);
                b[off++] = (byte)(ii >> 16 & 255);
                b[off++] = (byte)(ii >> 24 & 255);
            }
        } else {
            off = 0;

            for(i = 0; i < arr.length; ++i) {
                ii = arr[i];
                b[off++] = (byte)(ii >> 24 & 255);
                b[off++] = (byte)(ii >> 16 & 255);
                b[off++] = (byte)(ii >> 8 & 255);
                b[off++] = (byte)(ii & 255);
            }
        }

    }

    public static short bigEndianToShort(byte[] bs) {
        return bigEndianToShort(bs, 0);
    }

    public static short bigEndianToShort(byte[] bs, int off) {
        int n = bs[off] << 8;
        ++off;
        n |= bs[off] & 255;
        return (short)n;
    }

    public static byte[] shortToBytes(short n) {
        return ByteBuffer.allocate(2).putShort(n).array();
    }

    public static byte[] hexStringToBytes(String data) {
        if (data == null) {
            return EMPTY_BYTE_ARRAY;
        } else {
            if (data.startsWith("0x")) {
                data = data.substring(2);
            }

            if (data.length() % 2 == 1) {
                data = "0" + data;
            }

            return Hex.decode(data);
        }
    }

    public static byte[] hostToBytes(String ip) {
        byte[] bytesIp;
        try {
            bytesIp = InetAddress.getByName(ip).getAddress();
        } catch (UnknownHostException var3) {
            bytesIp = new byte[4];
        }

        return bytesIp;
    }

    public static String bytesToIp(byte[] bytesIp) {
        StringBuilder sb = new StringBuilder();
        sb.append(bytesIp[0] & 255);
        sb.append(".");
        sb.append(bytesIp[1] & 255);
        sb.append(".");
        sb.append(bytesIp[2] & 255);
        sb.append(".");
        sb.append(bytesIp[3] & 255);
        String ip = sb.toString();
        return ip;
    }

    public static int numberOfLeadingZeros(byte[] bytes) {
        int i = firstNonZeroByte(bytes);
        if (i == -1) {
            return bytes.length * 8;
        } else {
            int byteLeadingZeros = Integer.numberOfLeadingZeros(bytes[i] & 255) - 24;
            return i * 8 + byteLeadingZeros;
        }
    }

    public static byte[] parseBytes(byte[] input, int offset, int len) {
        if (offset < input.length && len != 0) {
            byte[] bytes = new byte[len];
            System.arraycopy(input, offset, bytes, 0, Math.min(input.length - offset, len));
            return bytes;
        } else {
            return EMPTY_BYTE_ARRAY;
        }
    }

    public static byte[] parseWord(byte[] input, int idx) {
        return parseBytes(input, 32 * idx, 32);
    }

    public static byte[] parseWord(byte[] input, int offset, int idx) {
        return parseBytes(input, offset + 32 * idx, 32);
    }
}

