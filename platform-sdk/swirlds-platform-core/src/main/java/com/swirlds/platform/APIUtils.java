/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * This class contains only static methods, used to encode and decode information into byte arrays for use
 * in the API.
 */
class APIUtils {

    /** never instantiate this class */
    private APIUtils() {}

    /**
     * Given a number in this sequence, return the next number in the sequence. The sequence starts at zero
     * and goes through all positive and negative long values: {0, -1, 1, -2, 2, -3, 3 ... -(1&lt;&lt;63)}.
     * After the last number in the sequence, it wraps around and returns zero.
     *
     * @param n
     * 		a number in the sequence
     * @return the next number in the sequence
     */
    public static long seqInc(long n) {
        return n < 0 ? -n == n ? 0 : -n : (-n) - 1L;
    }

    /**
     * Write a long to the stream in varint format. This might write from 1 to 10 bytes.
     *
     * The varint format is described
     * <a href="https://developers.google.com/protocol-buffers/docs/encoding">here</a>.
     *
     * @param n
     * 		the long to write
     * @param dos
     * 		the stream to write to
     * @return the number of bytes that were written
     * @throws IOException
     * 		if the stream throws this exception while writing to it
     */
    public static int writeVarint(long n, DataOutputStream dos) throws IOException {
        n = (n << 1) ^ (n >> 63); // zigzag so small negative numbers use few bytes
        int count = 0;
        while (true) {
            int b;
            long next = n >>> 7;
            if (next == 0) {
                b = (int) (n & 0x7f);
                dos.writeByte(b);
                count++;
                break;
            }
            b = (int) ((n & 0x7f) | 0x80);
            dos.writeByte(b);
            count++;
            n = next;
        }
        return count;
    }

    /**
     * Read a long from the stream in varint format. This might read from 1 to 10 bytes.
     *
     * @param dis
     * 		the stream to read from
     * @return the number that was read
     * @throws IOException
     * 		if the stream throws this exception while reading from it, or if the number being read
     * 		has an illegal format
     */
    public static long readVarint(DataInputStream dis) throws IOException {
        long n = 0;
        int count = 0;
        while (true) {
            long b = dis.readByte() & 0xff;
            count++;
            n = n | ((b & 0x7f) << (7 * (count - 1)));
            if ((b & 0x80) == 0) { // if this is the last byte in the varint (msb is 0)
                if (count == 10 && b > 1) {
                    throw new IOException("the varint read has illegal format (more than 64 bits)");
                }
                break;
            }
            if (count == 10) {
                throw new IOException("the varint read has illegal format (more than 10 bytes)");
            }
        }
        n = ((n << 63) >> 63) ^ (n >>> 1); // zigzag so small negative numbers use few bytes
        return n;
    }

    /**
     * Write a duration in seconds, using as few bytes as possible, and return how many bytes were written.
     * The duration is rounded down to the nearest second before writing it.
     *
     * @param duration
     * 		the duration, in seconds.
     * @param dos
     * 		the stream to write to
     * @return the number of bytes that were written
     * @throws IOException
     * 		if the stream throws this exception while writing to it
     */
    public static int writeDurationSec(Duration duration, DataOutputStream dos) throws IOException {
        return writeVarint(duration.getSeconds(), dos);
    }

    /**
     * Write a duration in nanoseconds, using as few bytes as possible, and return how many bytes were
     * written. The full (64+32)-bit duration is written (seconds and nanoseconds), written as a varint and
     * an int. The int is 4 bytes, big endian, 2s complement.
     *
     * @param duration
     * 		the duration, in nanoseconds.
     * @param dos
     * 		the stream to write to
     * @return the number of bytes that were written
     * @throws IOException
     * 		if the stream throws this exception while writing to it
     */
    public static int writeDurationNano(Duration duration, DataOutputStream dos) throws IOException {
        int count = 4 + writeVarint(duration.getSeconds(), dos);
        int nano = duration.getNano();
        // notes for porting to other languages:
        // int is a 32-bit 2s complement signed integer
        // writeByte sends the least significant 8 bits of its argument
        dos.writeByte(nano >> 24);
        dos.writeByte(nano >> 16);
        dos.writeByte(nano >> 8);
        dos.writeByte(nano);
        return count;
    }

    /**
     * Write the duration from reference to time, in seconds, using as few bytes as possible, and return how
     * many bytes were written. The duration is rounded down to the nearest second before writing it.
     *
     * @param reference
     * 		the starting time for calculating the duration
     * @param time
     * 		the ending time for calculating the duration
     * @param dos
     * 		the stream to write to
     * @return the number of bytes that were written
     * @throws IOException
     * 		if the stream throws this exception while writing to it
     */
    public static int writeTimeSecRelative(Instant time, Instant reference, DataOutputStream dos) throws IOException {
        return writeDurationSec(Duration.between(reference, time), dos);
    }

    /**
     * Write the duration from reference to time, in nanoseconds, using as few bytes as possible, and return
     * how many bytes were written. The full (64+32) bit duration is written (seconds and nanoseconds),
     * written as a varint and an int.
     *
     * @param reference
     * 		the starting time for calculating the duration
     * @param time
     * 		the ending time for calculating the duration
     * @param dos
     * 		the stream to write to
     * @return the number of bytes that were written
     * @throws IOException
     * 		if the stream throws this exception while writing to it
     */
    public static int writeTimeNanoRelative(Instant time, Instant reference, DataOutputStream dos) throws IOException {
        return writeDurationNano(Duration.between(reference, time), dos);
    }

    /**
     * Write the time, in seconds, using as few bytes as possible, and return how many bytes were written.
     * The time is rounded down to the nearest second.
     *
     * @param time
     * 		the time to write
     * @param dos
     * 		the stream to write to
     * @return the number of bytes that were written
     * @throws IOException
     * 		if the stream throws this exception while writing to it
     */
    public static int writeTimeSec(Instant time, DataOutputStream dos) throws IOException {
        return writeVarint(time.getEpochSecond(), dos);
    }

    /**
     * Write the time, in nanoseconds, using as few bytes as possible, and return how many bytes were
     * written. The full (64+32) bit duration is written (seconds and nanoseconds), written as a varint and
     * an int. The int is 4 bytes, big endian, 2s complement.
     *
     * @param time
     * 		the time to write
     * @param dos
     * 		the stream to write to
     * @return the number of bytes that were written
     * @throws IOException
     * 		if the stream throws this exception while writing to it
     */
    public static int writeTimeNano(Instant time, DataOutputStream dos) throws IOException {
        int count = 4 + writeVarint(time.getEpochSecond(), dos);
        int nano = time.getNano();
        // notes for porting to other languages:
        // int is a 32-bit 2s complement signed integer
        // writeByte sends the least significant 8 bits of its argument
        dos.writeByte(nano >> 24);
        dos.writeByte(nano >> 16);
        dos.writeByte(nano >> 8);
        dos.writeByte(nano);
        return count;
    }

    /**
     * Read a duration that was written with writeDurationSec.
     *
     * @param dis
     * 		the stream to read from
     * @return the duration
     * @throws IOException
     * 		if the stream throws this exception while reading from it
     */
    public static Duration readDurationSec(DataInputStream dis) throws IOException {
        return Duration.ofSeconds(readVarint(dis));
    }

    /**
     * Read a duration that was written with writeDurationNano.
     *
     * @param dis
     * 		the stream to read from
     * @return the duration
     * @throws IOException
     * 		if the stream throws this exception while reading from it
     */
    public static Duration readDurationNano(DataInputStream dis) throws IOException {
        long sec = readVarint(dis);
        int nano = dis.readByte();
        nano = (nano << 8) | (dis.readByte() & 0xff);
        nano = (nano << 8) | (dis.readByte() & 0xff);
        nano = (nano << 8) | (dis.readByte() & 0xff);
        return Duration.ofSeconds(sec, nano);
    }

    /**
     * Read a time that was written with writeTimeSecRelative using the same reference. The result will be
     * incorrect if the reference is different.
     *
     * @param reference
     * 		the reference time for calculating this time
     * @param dis
     * 		the stream to read from
     * @return the time
     * @throws IOException
     * 		if the stream throws this exception while reading from it
     */
    public static Instant readTimeSecRelative(Instant reference, DataInputStream dis) throws IOException {
        Duration duration = readDurationSec(dis);
        return reference.plus(duration);
    }

    /**
     * Read a time that was written with writeTimeNanoRelative using the same reference. The result will be
     * incorrect if the reference is different.
     *
     * @param reference
     * 		the reference time for calculating this time
     * @param dis
     * 		the stream to read from
     * @return the time
     * @throws IOException
     * 		if the stream throws this exception while reading from it
     */
    public static Instant readTimeNanoRelative(Instant reference, DataInputStream dis) throws IOException {
        Duration duration = readDurationNano(dis);
        return reference.plus(duration);
    }

    /**
     * Read a time that was written with writeTimeSec.
     *
     * @param dis
     * 		the stream to read from
     * @return the time
     * @throws IOException
     * 		if the stream throws this exception while reading from it
     */
    public static Instant readTimeSec(DataInputStream dis) throws IOException {
        long seconds = readVarint(dis);
        return Instant.ofEpochSecond(seconds);
    }

    /**
     * Read a time that was written with writeTimeNano.
     *
     * @param dis
     * 		the stream to read from
     * @return the time
     * @throws IOException
     * 		if the stream throws this exception while reading from it
     */
    public static Instant readTimeNano(DataInputStream dis) throws IOException {
        long seconds = readVarint(dis);
        int nano = dis.readByte();
        nano = (nano << 8) | (dis.readByte() & 0xff);
        nano = (nano << 8) | (dis.readByte() & 0xff);
        nano = (nano << 8) | (dis.readByte() & 0xff);
        return Instant.ofEpochSecond(seconds, nano);
    }

    /**
     * Write then read the given long as a varint. This is purely for testing and debugging. It should never
     * be called by production code.
     *
     * @param n
     * 		the number to test (by writing then reading it).
     */
    static void test(long n) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            int count = writeVarint(n, dos);
            System.out.printf("% d used %d bytes, and was correct\n", n, count);
            dos.flush();
            byte[] result = baos.toByteArray();
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(result));
            long m = readVarint(dis);
            if (m != n) {
                System.out.println("ERROR: wrote " + n + " but read " + m);
            }
        } catch (IOException e) {
            System.out.println("ERROR: " + e);
            e.printStackTrace();
        }
    }

    /**
     * Test writing then reading 10 numbers starting with n. This is purely for testing and debugging. It
     * should never be called by production code.
     *
     * @param n
     * 		starting number to test
     */
    static void test10(long n) {
        for (long i = 0; i < 10; i++) {
            test(n);
            n = seqInc(n);
        }
        System.out.println();
    }

    /**
     * This is purely for testing and debugging. It should never be called by production code.
     *
     * @param args
     * 		this is ignored
     */
    public static void main(String[] args) {
        test10(0);
        test10(0b0_0000000_0000000_0000000_0000000_0000000_0000000_0000000_0000000_0111110L);
        test10(0b0_0000000_0000000_0000000_0000000_0111111_1111111_1111111_1111111_1111110L);
        test10(0b0_0111111_1111111_1111111_1111111_1111111_1111111_1111111_1111111_1111110L);
        test10(0b0_1111111_1111111_1111111_1111111_1111111_1111111_1111111_1111111_1111110L);
    }
}
