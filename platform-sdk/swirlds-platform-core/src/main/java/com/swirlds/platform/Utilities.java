/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.internal.Deserializer;
import com.swirlds.platform.internal.Serializer;
import com.swirlds.platform.network.PeerInfo;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.net.SocketException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Supplier;
import java.util.stream.StreamSupport;
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
    public static int arrayCompare(byte[] sig1, byte[] sig2) {
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
    public static int arrayCompare(byte[] sig1, byte[] sig2, byte[] whitening) {
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

    /**
     * Checks all nesting of causes for any instance of the supplied type.
     *
     * @param throwable
     * 		the throwable to unwrap
     * @param type
     * 		the type to check against
     * @return true if any of the causes matches the supplied type, false otherwise.
     */
    public static boolean hasAnyCauseSuppliedType(
            @NonNull final Throwable throwable, @NonNull final Class<? extends Throwable> type) {
        Throwable cause = throwable;
        // check all causes
        while (cause != null) {
            if (type.isInstance(cause)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }

    /**
     * Create a list of PeerInfos from the address book. The list will contain information about all peers but not us.
     *
     * @param addressBook
     * 		the address book to create the list from
     * @param selfId
     * 		our ID
     * @return a list of PeerInfo
     */
    public static @NonNull List<PeerInfo> createPeerInfoList(
            @NonNull final AddressBook addressBook, @NonNull final NodeId selfId) {
        Objects.requireNonNull(addressBook);
        Objects.requireNonNull(selfId);
        return StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(addressBook.iterator(), Spliterator.ORDERED), false)
                .filter(address -> !address.getNodeId().equals(selfId))
                .map(address -> new PeerInfo(
                        address.getNodeId(),
                        address.getSelfName(),
                        address.getHostnameExternal(),
                        address.getSigCert()))
                .toList();
    }
}
