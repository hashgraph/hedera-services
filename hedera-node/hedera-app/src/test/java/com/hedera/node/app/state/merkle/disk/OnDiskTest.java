/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.state.merkle.disk;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.node.app.state.merkle.MerkleStateRegistry;
import com.hedera.node.app.state.merkle.MerkleTestBase;
import com.hedera.node.app.state.merkle.StateMetadata;
import com.hedera.node.app.state.merkle.StateUtils;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.jasperdb.VirtualLeafRecordSerializer;
import com.swirlds.jasperdb.files.DataFileCommon;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A variety of robust tests for the on-disk merkle data structure, especially including
 * serialization to/from disk (under normal operation) and to/from saved state. These tests use a
 * more complex map, with full objects to store and retrieve objects from the virtual map, and when
 * serializing for hashing, and for serializing when saving state.
 */
class OnDiskTest extends MerkleTestBase {
    private static final String SERVICE_NAME = "CryptoService";
    private static final String ACCOUNT_STATE_KEY = "Account";
    private static final MerkleCryptography CRYPTO = MerkleCryptoFactory.getInstance();

    private StateMetadata<AccountID, Account> md;
    private VirtualMap<OnDiskKey<AccountID>, OnDiskValue<Account>> virtualMap;
    @TempDir Path storageDir;

    @Override
    @BeforeEach
    protected void setUp() {
        super.setUp();
        md =
                new StateMetadata<>(
                        SERVICE_NAME,
                        ACCOUNT_STATE_KEY,
                        OnDiskTest::parseAccountID,
                        OnDiskTest::parseAccount,
                        OnDiskTest::writeAccountID,
                        OnDiskTest::writeAccount,
                        OnDiskTest::measureAccountID);

        final var builder =
                new JasperDbBuilder<OnDiskKey<AccountID>, OnDiskValue<Account>>()
                        // Force all hashes to disk, to make sure we're going through all the
                        // serialization paths we can
                        .internalHashesRamToDiskThreshold(0)
                        .storageDir(storageDir)
                        .maxNumOfKeys(100)
                        .preferDiskBasedIndexes(true)
                        .keySerializer(new OnDiskKeySerializer<>(md))
                        .virtualLeafRecordSerializer(
                                new VirtualLeafRecordSerializer<>(
                                        (short) 1,
                                        DigestType.SHA_384,
                                        (short) 1,
                                        DataFileCommon.VARIABLE_DATA_SIZE,
                                        new OnDiskKeySerializer<>(md),
                                        (short) 1,
                                        DataFileCommon.VARIABLE_DATA_SIZE,
                                        new OnDiskValueSerializer<>(md),
                                        true));

        virtualMap =
                new VirtualMap<>(StateUtils.computeLabel(SERVICE_NAME, ACCOUNT_STATE_KEY), builder);
    }

    <K extends Comparable<K>, V> VirtualMap<OnDiskKey<K>, OnDiskValue<V>> copyHashAndFlush(
            VirtualMap<OnDiskKey<K>, OnDiskValue<V>> map) {
        // Make the fast copy
        final var copy = map.copy();

        // Hash the now immutable map
        CRYPTO.digestTreeSync(map);

        // Flush to disk
        final VirtualRootNode<?, ?> root = map.getChild(1);
        root.enableFlush();
        map.release();
        try {
            root.waitUntilFlushed();
        } catch (InterruptedException e) {
            System.err.println("Unable to complete the test, the root node never flushed!");
            throw new RuntimeException(e);
        }

        // And we're done
        return copy;
    }

    @Test
    void populateTheMapAndFlushToDiskAndReadBack(@TempDir Path dir) throws IOException {
        // Populate the data set and flush it all to disk
        final var ws = new OnDiskWritableState<>(md, virtualMap);
        for (int i = 0; i < 10; i++) {
            final var id = new AccountID(0, 0, i);
            final var acct = new Account(id, "Account " + i, i);
            ws.put(id, acct);
        }
        ws.commit();
        virtualMap = copyHashAndFlush(virtualMap);

        // We will now make another fast copy of our working copy of the tree.
        // Then we will hash the immutable copy and write it out. Then we will
        // release the immutable copy.
        virtualMap.copy(); // throw away the copy, we won't use it
        CRYPTO.digestTreeSync(virtualMap);
        final byte[] serializedBytes = writeTree(virtualMap, dir);

        // Before we can read the data back, we need to register the data types
        // I plan to deserialize.
        final var r =
                new MerkleStateRegistry(
                        registry,
                        storageDir,
                        SERVICE_NAME,
                        new BasicSoftwareVersion(1),
                        new BasicSoftwareVersion(1));
        r.register(ACCOUNT_STATE_KEY)
                .keyLength(OnDiskTest::measureAccountID)
                .keyParser(OnDiskTest::parseAccountID)
                .keyWriter(OnDiskTest::writeAccountID)
                .valueParser(OnDiskTest::parseAccount)
                .valueWriter(OnDiskTest::writeAccount)
                .disk()
                .maxNumOfKeys(100)
                .complete();

        // read it back now as our map and validate the data come back fine
        virtualMap = parseTree(serializedBytes, dir);
        final var rs = new OnDiskReadableState<>(md, virtualMap);
        for (int i = 0; i < 10; i++) {
            final var id = new AccountID(0, 0, i);
            final var opt = rs.get(id);
            assertThat(opt).isPresent();
            final var acct = opt.get();
            assertThat(acct.accountID()).isEqualTo(id);
            assertThat(acct.memo()).isEqualTo("Account " + i);
            assertThat(acct.balance).isEqualTo(i);
        }
    }

    @Test
    void populateFlushToDisk() {
        final var ws = new OnDiskWritableState<>(md, virtualMap);
        for (int i = 0; i < 10; i++) {
            final var id = new AccountID(0, 0, i);
            final var acct = new Account(id, "Account " + i, i);
            ws.put(id, acct);
        }
        ws.commit();
        virtualMap = copyHashAndFlush(virtualMap);

        final var rs = new OnDiskReadableState<>(md, virtualMap);
        for (int i = 0; i < 10; i++) {
            final var id = new AccountID(0, 0, i);
            final var opt = rs.get(id);
            assertThat(opt).isPresent();
            final var acct = opt.get();
            assertThat(acct.accountID()).isEqualTo(id);
            assertThat(acct.memo()).isEqualTo("Account " + i);
            assertThat(acct.balance).isEqualTo(i);
        }
    }

    /*****************************************************************************
     * The classes and method below this point are helpers for this test. They
     * include fake objects and serialization methods, emulating what a service
     * would do.
     ****************************************************************************/

    private record AccountID(long shard, long realm, long num) implements Comparable<AccountID> {
        @Override
        public int compareTo(AccountID other) {
            if (shard != other.shard) {
                return Long.compare(shard, other.shard);
            }

            if (realm != other.realm) {
                return Long.compare(realm, other.realm);
            }

            if (num != other.num) {
                return Long.compare(num, other.num);
            }

            return 0;
        }
    }

    private record Account(
            @NonNull OnDiskTest.AccountID accountID, @NonNull String memo, long balance) {}

    public static int measureAccountID(@NonNull DataInput ignored) {
        // This implementation doesn't need to read from input.
        return Long.BYTES * 3;
    }

    public static void writeAccountID(@Nullable AccountID value, DataOutput output)
            throws IOException {
        if (value != null) {
            output.writeLong(value.shard);
            output.writeLong(value.realm);
            output.writeLong(value.num);
        }
    }

    @NonNull
    public static AccountID parseAccountID(@NonNull DataInput input) throws IOException {
        final long shard = input.readLong();
        final long realm = input.readLong();
        final long num = input.readLong();
        return new AccountID(shard, realm, num);
    }

    public static void writeAccount(@Nullable Account acct, DataOutput output) throws IOException {
        if (acct != null) {
            // id
            writeAccountID(acct.accountID(), output);
            // memo
            final var bb = StandardCharsets.UTF_8.encode(acct.memo());
            output.writeInt(bb.limit());
            output.write(bb.array(), 0, bb.limit());
            // balance
            output.writeLong(acct.balance);
        }
    }

    @NonNull
    public static Account parseAccount(@NonNull DataInput input) throws IOException {
        final var id = parseAccountID(input);
        final int memoLen = input.readInt();
        final byte[] memoBytes = new byte[memoLen];
        input.readFully(memoBytes);
        final var memo = new String(memoBytes, StandardCharsets.UTF_8);
        final var balance = input.readLong();
        return new Account(id, memo, balance);
    }
}
