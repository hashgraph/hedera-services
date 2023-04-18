/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.internal.Deserializer;
import com.swirlds.platform.internal.Serializer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.SocketException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This is a collection of static utility methods, such as for comparing and deep cloning of arrays.
 */
public final class Utilities {

    private Utilities() {}

    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(Utilities.class);

    /**
     * Convert a string to a boolean.
     *
     * A false is defined to be any string that, after trimming leading/trailing whitespace and conversion
     * to lowercase, is equal to null, or the empty string, or "off" or "0", or starts with "f" or "n". All
     * other strings are true.
     *
     * @param par
     * 		the string to convert (or null)
     * @return the boolean value
     */
    public static boolean parseBoolean(String par) {
        if (par == null) {
            return false;
        }
        String p = par.trim().toLowerCase();
        if (p.equals("")) {
            return false;
        }
        String f = p.substring(0, 1);
        return !(p.equals("0") || f.equals("f") || f.equals("n") || p.equals("off"));
    }

    /**
     * Do a deep clone of a 2D array. Here, "deep" means that after doing x=deepClone(y), x won't be
     * affected by changes to any part of y, such as assigning to y or to y[0] or to y[0][0].
     *
     * @param original
     * 		the original array
     * @return the deep clone
     */
    public static long[][] deepClone(long[][] original) {
        if (original == null) {
            return null;
        }
        long[][] result = original.clone();
        for (int i = 0; i < original.length; i++) {
            if (original[i] != null) {
                result[i] = original[i].clone();
            }
        }
        return result;
    }

    /**
     * Do a deep clone of a 2D array. Here, "deep" means that after doing x=deepClone(y), x won't be
     * affected by changes to any part of y, such as assigning to y or to y[0] or to y[0][0].
     *
     * @param original
     * 		the original array
     * @return the deep clone
     */
    public static byte[][] deepClone(byte[][] original) {
        if (original == null) {
            return null;
        }
        byte[][] result = original.clone();
        for (int i = 0; i < original.length; i++) {
            if (original[i] != null) {
                result[i] = original[i].clone();
            }
        }
        return result;
    }

    /**
     * Do a deep clone of any serialiazable object that can reach only other serializable objects through
     * following references.
     *
     * @param original
     * 		the object to clone
     * @return the clone
     */
    public static Object deepCloneBySerializing(Object original) {
        ObjectOutputStream dos = null;
        ObjectInputStream dis = null;
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            dos = new ObjectOutputStream(bos);
            // serialize and pass the object
            dos.writeObject(original);
            dos.flush();
            ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
            dis = new ObjectInputStream(bin);
            return dis.readObject();
        } catch (Exception e) {
            logger.error(EXCEPTION.getMarker(), "", e);
        } finally {
            try {
                if (dos != null) {
                    dos.close();
                }
            } catch (Exception ignored) {
            }
            try {
                if (dis != null) {
                    dis.close();
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    /**
     * Compare arrays lexicographically, with element 0 having the most influence.
     * A null array is considered less than a non-null array.
     * This is the same as Java.Util.Arrays#compar
     *
     * @param sig1
     * 		first array
     * @param sig2
     * 		second array
     * @return 1 if first is bigger, -1 if second, 0 otherwise
     */
    static int arrayCompare(byte[] sig1, byte[] sig2) {
        if (sig1 == null && sig2 == null) {
            return 0;
        }
        if (sig1 == null && sig2 != null) {
            return -1;
        }
        if (sig1 != null && sig2 == null) {
            return 1;
        }
        for (int i = 0; i < Math.min(sig1.length, sig2.length); i++) {
            if (sig1[i] < sig2[i]) {
                return -1;
            }
            if (sig1[i] > sig2[i]) {
                return 1;
            }
        }
        if (sig1.length < sig2.length) {
            return -1;
        }
        if (sig1.length > sig2.length) {
            return 1;
        }
        return 0;
    }

    /**
     * Compare arrays lexicographically, with element 0 having the most influence, as if each array was
     * XORed with whitening before the comparison. The XOR doesn't actually happen, and the arrays are left
     * unchanged.
     *
     * @param sig1
     * 		first array
     * @param sig2
     * 		second array
     * @param whitening
     * 		the array virtually XORed with the other two
     * @return 1 if first is bigger, -1 if second, 0 otherwise
     */
    static int arrayCompare(byte[] sig1, byte[] sig2, byte[] whitening) {
        int maxLen;
        int minLen;
        if (sig1 == null && sig2 == null) {
            return 0;
        }
        if (sig1 != null && sig2 == null) {
            return 1;
        }
        if (sig1 == null && sig2 != null) {
            return -1;
        }
        maxLen = Math.max(sig1.length, sig2.length);
        minLen = Math.min(sig1.length, sig2.length);
        if (whitening.length < maxLen) {
            whitening = Arrays.copyOf(whitening, maxLen);
        }
        for (int i = 0; i < minLen; i++) {
            int b1 = sig1[i] ^ whitening[i];
            int b2 = sig2[i] ^ whitening[i];
            if (b1 > b2) {
                return 1;
            }
            if (b1 < b2) {
                return -1;
            }
        }
        if (sig1.length > sig2.length) {
            return 1;
        }
        if (sig1.length < sig2.length) {
            return -1;
        }
        return 0;
    }

    /////////////////////////////////////////////////////////////
    // read from DataInputStream and
    // write to DataOutputStream

    /**
     * Writes a list to the stream serializing the objects with the supplied method
     *
     * @param list
     * 		the list to be serialized
     * @param stream
     * 		the stream to write to
     * @param serializer
     * 		the method used to write the object
     * @param <T>
     * 		the type of object being written
     * @throws IOException
     * 		thrown if there are any problems during the operation
     */
    @Deprecated
    public static <T> void writeList(List<T> list, SerializableDataOutputStream stream, Serializer<T> serializer)
            throws IOException {
        if (list == null) {
            stream.writeInt(-1);
            return;
        }
        stream.writeInt(list.size());
        for (T t : list) {
            serializer.serialize(t, stream);
        }
    }

    /**
     * Reads a list from the stream deserializing the objects with the supplied method
     *
     * @param stream
     * 		the stream to read from
     * @param listSupplier
     * 		a method that supplies the list to add to
     * @param deserializer
     * 		a method used to deserialize the objects
     * @param <T>
     * 		the type of object contained in the list
     * @return a list that was read from the stream, can be null if that was written
     * @throws IOException
     * 		thrown if there are any problems during the operation
     */
    @Deprecated
    public static <T> List<T> readList(
            SerializableDataInputStream stream, Supplier<List<T>> listSupplier, Deserializer<T> deserializer)
            throws IOException {
        int listSize = stream.readInt();
        if (listSize < 0) {
            return null;
        }
        List<T> list = listSupplier.get();
        for (int i = 0; i < listSize; i++) {
            list.add(deserializer.deserialize(stream));
        }
        return list;
    }

    /**
     * Convert the given long to bytes, big endian.
     *
     * @param n
     * 		the long to convert
     * @return a big-endian representation of n as an array of Long.BYTES bytes
     */
    public static byte[] toBytes(long n) {
        byte[] bytes = new byte[Long.BYTES];
        toBytes(n, bytes, 0);
        return bytes;
    }

    /**
     * Convert the given long to bytes, big endian, and put them into the array, starting at index start
     *
     * @param bytes
     * 		the array to hold the Long.BYTES bytes of result
     * @param n
     * 		the long to convert to bytes
     * @param start
     * 		the bytes are written to Long.BYTES elements of the array, starting with this index
     */
    public static void toBytes(long n, byte[] bytes, int start) {
        for (int i = start + Long.BYTES - 1; i >= start; i--) {
            bytes[i] = (byte) n;
            n >>>= 8;
        }
    }

    /**
     * convert the given byte array to a long
     *
     * @param b
     * 		the byte array to convert (at least 8 bytes)
     * @return the long that was represented by the array
     */
    public static long toLong(byte[] b) {
        return toLong(b, 0);
    }

    /**
     * convert part of the given byte array to a long, starting with index start
     *
     * @param b
     * 		the byte array to convert
     * @param start
     * 		the index of the first byte (most significant byte) of the 8 bytes to convert
     * @return the long
     */
    public static long toLong(byte[] b, int start) {
        long result = 0;
        for (int i = start; i < start + Long.BYTES; i++) {
            result <<= 8;
            result |= b[i] & 0xFF;
        }
        return result;
    }

    /**
     * Is the part more than 2/3 of the whole?
     *
     * @param part
     * 		a long value, the fraction of the whole being compared
     * @param whole
     * 		a long value, the whole being considered (such as the sum of the entire weight)
     * @return true if part is more than two thirds of the whole
     */
    public static boolean isSuperMajority(final long part, final long whole) {

        // For non-negative integers p and w,
        // the following three inequalities are
        // mathematically equivalent (for
        // infinite precision real computations):
        //
        // p > w * 2 / 3
        //
        // p > floor(w * 2 / 3)
        //
        // p > floor(w / 3) * 2 + floor((w mod 3) * 2 / 3)
        //
        // Therefore, given that Java long division
        // rounds toward zero, it is equivalent to do
        // the following:
        //
        // p > w / 3 * 2 + (w % 3) * 2 / 3;
        //
        // That avoids overflow for p and w
        // if they are positive long variable.

        return part > whole / 3 * 2 + (whole % 3) * 2 / 3;
    }

    /**
     * Is the part 1/3 or more of the whole?
     *
     * @param part
     * 		a long value, the fraction of the whole being compared
     * @param whole
     * 		a long value, the whole being considered (such as the sum of the entire weight)
     * @return true if part is greater than or equal to one third of the whole
     */
    public static boolean isStrongMinority(final long part, final long whole) {

        // Java long division rounds down, but in this case we instead want to round up.
        // If whole is divisible by three then floor(whole/3) == ceil(whole/3)
        // If whole is not divisible by three then floor(whole/3) + 1 == ceil(whole/3)

        return part >= (whole / 3) + ((whole % 3 == 0) ? 0 : 1);
    }

    /**
     * Is the part more than 1/2 of the whole?
     *
     * @param part
     * 		a long value, the fraction of the whole being compared
     * @param whole
     * 		a long value, the whole being considered (such as the sum of the entire weight)
     * @return true if part is greater or equal to one half of the whole
     */
    public static boolean isMajority(final long part, final long whole) {
        return part >= (whole / 2) + 1;
    }

    /**
     * if it is or caused by SocketException,
     * we should log it with SOCKET_EXCEPTIONS marker
     *
     * @param ex
     * @return return true if it is a SocketException or is caused by SocketException;
     * 		return false otherwise
     */
    public static boolean isOrCausedBySocketException(final Throwable ex) {
        return isRootCauseSuppliedType(ex, SocketException.class);
    }

    /**
     * @param e
     * 		the exception to check
     * @return true if the cause is an IOException
     */
    public static boolean isCausedByIOException(final Exception e) {
        return isRootCauseSuppliedType(e, IOException.class);
    }

    /**
     * Unwraps a Throwable and checks the root cause
     *
     * @param t
     * 		the throwable to unwrap
     * @param type
     * 		the type to check against
     * @return true if the root cause matches the supplied type
     */
    public static boolean isRootCauseSuppliedType(final Throwable t, final Class<? extends Throwable> type) {
        if (t == null) {
            return false;
        }
        Throwable cause = t;
        // get to the root cause
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return type.isInstance(cause);
    }
}
