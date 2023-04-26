/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.files.hashmap;

import static com.swirlds.merkledb.files.hashmap.HalfDiskHashMap.SPECIAL_DELETE_ME_VALUE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.merkledb.ExampleLongKeyFixedSize;
import com.swirlds.merkledb.ExampleLongKeyVariableSize;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.virtualmap.VirtualLongKey;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@SuppressWarnings({"RedundantCast", "unchecked", "rawtypes"})
class BucketTest {

    private enum KeyType {
        fixed(ExampleLongKeyFixedSize::new, new ExampleLongKeyFixedSize.Serializer()),
        variable(ExampleLongKeyVariableSize::new, new ExampleLongKeyVariableSize.Serializer());

        final Function<Long, VirtualLongKey> keyConstructor;
        final KeySerializer<VirtualLongKey> keySerializer;

        KeyType(Function<Long, VirtualLongKey> keyConstructor, final KeySerializer keySerializer) {
            this.keyConstructor = keyConstructor;
            this.keySerializer = (KeySerializer<VirtualLongKey>) ((Object) keySerializer);
        }
    }

    @ParameterizedTest
    @EnumSource(KeyType.class)
    void canSetAndGetBucketIndex(KeyType keyType) {
        final Bucket<VirtualLongKey> subject = new Bucket<>(keyType.keySerializer);
        final int pretendIndex = 123;
        subject.setBucketIndex(pretendIndex);
        assertEquals(pretendIndex, subject.getBucketIndex(), "Should be able to get the set index");
    }

    @ParameterizedTest
    @EnumSource(KeyType.class)
    void returnsNotFoundValueFromEmptyBucket(KeyType keyType) throws IOException {
        final Bucket<VirtualLongKey> subject = new Bucket<>(keyType.keySerializer);
        final long notFoundValue = 123;
        final ExampleLongKeyFixedSize missingKey = new ExampleLongKeyFixedSize(321);

        assertEquals(
                notFoundValue,
                subject.findValue(missingKey.hashCode(), missingKey, notFoundValue),
                "Should not find a value in an empty bucket");
    }

    @ParameterizedTest
    @EnumSource(KeyType.class)
    void testBucketAddAndDelete(KeyType keyType) throws IOException {
        // create some keys to test with
        final VirtualLongKey[] testKeys = new VirtualLongKey[20];
        for (int i = 0; i < testKeys.length; i++) {
            //			testKeys[i] = new ExampleLongKeyFixedSize(i+10);
            testKeys[i] = keyType.keyConstructor.apply((long) (i + 10));
        }
        // create a bucket
        final Bucket<VirtualLongKey> bucket = new Bucket<>(keyType.keySerializer);
        bucket.setKeySerializationVersion((int) keyType.keySerializer.getCurrentDataVersion());
        assertEquals(0, bucket.getBucketEntryCount(), "Check we start with empty bucket");
        // insert keys and check
        for (int i = 0; i < 10; i++) {
            final VirtualLongKey key = testKeys[i];
            bucket.putValue(key, key.getKeyAsLong() + 100);
            assertEquals(i + 1, bucket.getBucketEntryCount(), "Check we have correct count");
            // check that all keys added so far are there
            for (int j = 0; j <= i; j++) {
                checkKey(bucket, testKeys[j]);
            }
        }
        assertEquals(10, bucket.getBucketEntryCount(), "Check we have correct count");
        // now update, check and put back
        bucket.putValue(testKeys[5], 1234);
        assertEquals(
                1234, bucket.findValue(testKeys[5].hashCode(), testKeys[5], -1), "Should get expected value of 1234");
        bucket.putValue(testKeys[5], 115);
        for (int j = 0; j < 10; j++) {
            checkKey(bucket, testKeys[j]);
        }
        // now delete last key and check
        bucket.putValue(testKeys[9], SPECIAL_DELETE_ME_VALUE);
        assertEquals(9, bucket.getBucketEntryCount(), "Check we have correct count");
        for (int j = 0; j < 9; j++) {
            checkKey(bucket, testKeys[j]);
        }
        assertEquals(
                -1,
                bucket.findValue(testKeys[9].hashCode(), testKeys[9], -1),
                "Should not find entry 10 any more we deleted it");
        // now delete a middle, index 5
        bucket.putValue(testKeys[5], SPECIAL_DELETE_ME_VALUE);
        assertEquals(8, bucket.getBucketEntryCount(), "Check we have correct count");
        for (int j = 0; j < 5; j++) {
            checkKey(bucket, testKeys[j]);
        }
        for (int j = 6; j < 9; j++) {
            checkKey(bucket, testKeys[j]);
        }
        assertEquals(
                -1,
                bucket.findValue(testKeys[5].hashCode(), testKeys[5], -1),
                "Should not find entry 5 any more we deleted it");
        // now delete first, index 0
        bucket.putValue(testKeys[0], SPECIAL_DELETE_ME_VALUE);
        assertEquals(7, bucket.getBucketEntryCount(), "Check we have correct count");
        for (int j = 1; j < 5; j++) {
            checkKey(bucket, testKeys[j]);
        }
        for (int j = 6; j < 9; j++) {
            checkKey(bucket, testKeys[j]);
        }
        assertEquals(
                -1,
                bucket.findValue(testKeys[0].hashCode(), testKeys[0], -1),
                "Should not find entry 0 any more we deleted it");
        // add two more entries and check
        bucket.putValue(testKeys[10], 120);
        bucket.putValue(testKeys[11], 121);
        assertEquals(9, bucket.getBucketEntryCount(), "Check we have correct count");
        for (int j = 1; j < 5; j++) {
            checkKey(bucket, testKeys[j]);
        }
        for (int j = 6; j < 9; j++) {
            checkKey(bucket, testKeys[j]);
        }
        for (int j = 10; j < 12; j++) {
            checkKey(bucket, testKeys[j]);
        }
        // put 0, 5 and 9 back in, and check we have full range
        bucket.putValue(testKeys[0], 110);
        bucket.putValue(testKeys[5], 115);
        bucket.putValue(testKeys[9], 119);
        assertEquals(12, bucket.getBucketEntryCount(), "Check we have correct count");
        for (int j = 0; j < 12; j++) {
            checkKey(bucket, testKeys[j]);
        }
    }

    @ParameterizedTest
    @EnumSource(KeyType.class)
    void testBucketGrowing(KeyType keyType) {
        // create some keys to test with
        final VirtualLongKey[] testKeys = new VirtualLongKey[420];
        for (int i = 0; i < testKeys.length; i++) {
            //			testKeys[i] = new ExampleLongKeyFixedSize(i+10);
            testKeys[i] = keyType.keyConstructor.apply((long) (i + 10));
        }
        // create a bucket
        final Bucket<VirtualLongKey> bucket = new Bucket<>(keyType.keySerializer);
        bucket.setKeySerializationVersion((int) keyType.keySerializer.getCurrentDataVersion());
        assertEquals(0, bucket.getBucketEntryCount(), "Check we start with empty bucket");
        // insert keys and check
        for (int i = 0; i < testKeys.length; i++) {
            final VirtualLongKey key = testKeys[i];
            bucket.putValue(key, key.getKeyAsLong() + 100);
            assertEquals(i + 1, bucket.getBucketEntryCount(), "Check we have correct count");
            // check that all keys added so far are there
            for (int j = 0; j <= i; j++) {
                checkKey(bucket, testKeys[j]);
            }
        }
        assertEquals(testKeys.length, bucket.getBucketEntryCount(), "Check we have correct count");
    }

    @ParameterizedTest
    @EnumSource(KeyType.class)
    void testBucketImportExportClear(KeyType keyType) throws IOException {
        // create some keys to test with
        final VirtualLongKey[] testKeys = new VirtualLongKey[50];
        for (int i = 0; i < testKeys.length; i++) {
            //			testKeys[i] = new ExampleLongKeyFixedSize(i+10);
            testKeys[i] = keyType.keyConstructor.apply((long) (i + 10));
        }
        // create a bucket
        final Bucket<VirtualLongKey> bucket = new Bucket<>(keyType.keySerializer);
        bucket.setKeySerializationVersion((int) keyType.keySerializer.getCurrentDataVersion());
        assertEquals(0, bucket.getBucketEntryCount(), "Check we start with empty bucket");
        // insert keys and check
        for (int i = 0; i < testKeys.length; i++) {
            final VirtualLongKey key = testKeys[i];
            bucket.putValue(key, key.getKeyAsLong() + 100);
            assertEquals(i + 1, bucket.getBucketEntryCount(), "Check we have correct count");
            // check that all keys added so far are there
            for (int j = 0; j <= i; j++) {
                checkKey(bucket, testKeys[j]);
            }
        }
        assertEquals(testKeys.length, bucket.getBucketEntryCount(), "Check we have correct count");
        // get raw bytes first to compare to
        final int size = bucket.getSize();
        final byte[] goodBytes = new byte[size];
        System.arraycopy(bucket.getBucketBuffer().array(), 0, goodBytes, 0, size);
        final String goodBytesStr = Arrays.toString(goodBytes);
        // now test write to buffer
        final ByteBuffer bbuf = ByteBuffer.allocate(size);
        bucket.writeToByteBuffer(bbuf);
        bbuf.flip();
        assertEquals(goodBytesStr, Arrays.toString(bbuf.array()), "Expect bytes to match");

        // create new bucket with good bytes and check it is the same
        final Bucket<VirtualLongKey> bucket2 = new Bucket<>(keyType.keySerializer);
        bucket2.setKeySerializationVersion((int) keyType.keySerializer.getCurrentDataVersion());
        bucket2.putAllData(ByteBuffer.wrap(goodBytes));
        assertEquals(bucket.toString(), bucket2.toString(), "Expect bucket toStrings to match");

        // test clear
        final Bucket<VirtualLongKey> bucket3 = new Bucket<>(keyType.keySerializer);
        bucket3.setKeySerializationVersion((int) keyType.keySerializer.getCurrentDataVersion());
        bucket.clear();
        assertEquals(bucket3.toString(), bucket.toString(), "Expect bucket toStrings to match");
    }

    @Test
    void toStringAsExpectedForBucket() {
        final ExampleLongKeyFixedSize.Serializer keySerializer = new ExampleLongKeyFixedSize.Serializer();
        final Bucket<ExampleLongKeyFixedSize> bucket = new Bucket<>(keySerializer);
        bucket.setKeySerializationVersion((int) keySerializer.getCurrentDataVersion());

        final String emptyBucketRepr =
                "Bucket{bucketIndex=-1, entryCount=0, size=12\n" + "} RAW DATA = FF FF FF FF 00 00 00 0C 00 00 00 00 ";
        assertEquals(emptyBucketRepr, bucket.toString(), "Empty bucket should represent as expected");

        final ExampleLongKeyFixedSize key = new ExampleLongKeyFixedSize(2056);
        bucket.putValue(key, 5124);
        bucket.setBucketIndex(0);
        final String nonEmptyBucketRepr = "Bucket{bucketIndex=0, entryCount=1, size=32\n"
                + "    ENTRY[0] value= 5124 keyHashCode=2056 keyVer=3054"
                + " key=LongVirtualKey{value=2056, hashCode=2056} keySize=8\n"
                + "} RAW DATA = 00 00 00 00 00 00 00 20 00 00 00 01 00 00 08 08 00 00 00 00 00"
                + " 00 14 04 00 00 00 00 00 00 08 08 ";
        assertEquals(nonEmptyBucketRepr, bucket.toString(), "Non-empty bucket represent as expected");
    }

    private void checkKey(Bucket<VirtualLongKey> bucket, VirtualLongKey key) {
        var findResult =
                assertDoesNotThrow(() -> bucket.findValue(key.hashCode(), key, -1), "No exception should be thrown");
        assertEquals(key.getKeyAsLong() + 100, findResult, "Should get expected value");
    }
}
