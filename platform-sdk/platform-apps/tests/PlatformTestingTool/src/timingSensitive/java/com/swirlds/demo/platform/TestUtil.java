/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform;

import com.swirlds.demo.merkle.map.MapValueFCQ;
import com.swirlds.demo.platform.fs.stresstest.proto.AppTransactionSignatureType;
import com.swirlds.merkle.test.fixtures.map.pta.TransactionRecord;
import java.time.Instant;
import java.util.Random;

/**
 * Utilities which test classes can use
 */
public class TestUtil {
    private static final Random random = new Random();
    private static final int contentSize = 100;

    /**
     * Generate transaction records to insert into FCQueue
     *
     * @return TransactionRecord generated
     */
    public static TransactionRecord generateTxRecord(long expirationTime) {
        final long index = random.nextLong();
        final long balance = random.nextLong();
        final byte[] content = generateRandomContent();
        return new TransactionRecord(index, balance, content, expirationTime);
    }

    /**
     * Add records to FCQueue
     *
     * @param mapValueFCQ
     * @return new MapValueFCQ after new records are added
     */
    public static MapValueFCQ addRecord(MapValueFCQ mapValueFCQ, long expirationTime) {
        TransactionRecord txRecord = generateTxRecord(expirationTime);
        MapValueFCQ fcqValue = mapValueFCQ.addRecord(txRecord.getBalance(), txRecord, null, null, null);
        return fcqValue;
    }

    /**
     * Generate random content with byte array of length contentSize
     *
     * @return byte array content
     */
    public static byte[] generateRandomContent() {
        final byte[] content = new byte[contentSize];
        random.nextBytes(content);
        return content;
    }

    /**
     * Sign a transaction when payload is given
     *
     * @param payload
     * @param pttTransactionPool
     * @return
     */
    public static byte[] signTransaction(byte[] payload, PttTransactionPool pttTransactionPool) {
        return pttTransactionPool.signAndConcatenatePubKeySignature(
                payload, false, AppTransactionSignatureType.ECDSA_SECP256K1);
    }

    public static MapValueFCQ<TransactionRecord> deleteFirstRecord(MapValueFCQ mapValueFCQ) {
        int size = mapValueFCQ.getRecordsSize();
        if (size > 0) {
            return mapValueFCQ.deleteFirst();
        }
        return mapValueFCQ;
    }

    public static long getExpirationTime(long fcqTTL) {
        return Instant.now().getEpochSecond() + fcqTTL;
    }
}
