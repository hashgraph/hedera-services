/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.test.fixtures;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.HashBuilder;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialBinaryMerkleInternal;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.datasource.VirtualHashRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafBytes;
import com.swirlds.virtualmap.internal.cache.VirtualNodeCache;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

@SuppressWarnings("jol")
public class VirtualTestBase {

    protected static final Cryptography CRYPTO = CryptographyHolder.get();

    private static final HashBuilder HASH_BUILDER = new HashBuilder(Cryptography.DEFAULT_DIGEST_TYPE);

    // Keys that we will use repeatedly in these tests.
    protected static final Bytes A_KEY = TestKey.charToKey('A');
    protected static final Bytes B_KEY = TestKey.charToKey('B');
    protected static final Bytes C_KEY = TestKey.charToKey('C');
    protected static final Bytes D_KEY = TestKey.charToKey('D');
    protected static final Bytes E_KEY = TestKey.charToKey('E');
    protected static final Bytes F_KEY = TestKey.charToKey('F');
    protected static final Bytes G_KEY = TestKey.charToKey('G');

    protected static final Bytes APPLE = TestValue.stringToValue("Apple");
    protected static final Bytes BANANA = TestValue.stringToValue("Banana");
    protected static final Bytes CHERRY = TestValue.stringToValue("Cherry");
    protected static final Bytes DATE = TestValue.stringToValue("Date");
    protected static final Bytes EGGPLANT = TestValue.stringToValue("Eggplant");
    protected static final Bytes FIG = TestValue.stringToValue("Fig");
    protected static final Bytes GRAPE = TestValue.stringToValue("Grape");

    protected static final Bytes AARDVARK = TestValue.stringToValue("Aardvark");
    protected static final Bytes BEAR = TestValue.stringToValue("Bear");
    protected static final Bytes CUTTLEFISH = TestValue.stringToValue("Cuttlefish");
    protected static final Bytes DOG = TestValue.stringToValue("Dog");
    protected static final Bytes EMU = TestValue.stringToValue("Emu");
    protected static final Bytes FOX = TestValue.stringToValue("Fox");
    protected static final Bytes GOOSE = TestValue.stringToValue("Goose");

    protected static final Bytes ASTRONAUT = TestValue.stringToValue("Astronaut");
    protected static final Bytes BLASTOFF = TestValue.stringToValue("Blastoff");
    protected static final Bytes COMET = TestValue.stringToValue("Comet");
    protected static final Bytes DRACO = TestValue.stringToValue("Draco");
    protected static final Bytes EXOPLANET = TestValue.stringToValue("Exoplanet");
    protected static final Bytes FORCE = TestValue.stringToValue("Force");
    protected static final Bytes GRAVITY = TestValue.stringToValue("Gravity");

    protected static final Bytes ASTRONOMY = TestValue.stringToValue("Astronomy");
    protected static final Bytes BIOLOGY = TestValue.stringToValue("Biology");
    protected static final Bytes CHEMISTRY = TestValue.stringToValue("Chemistry");
    protected static final Bytes DISCIPLINE = TestValue.stringToValue("Discipline");
    protected static final Bytes ECOLOGY = TestValue.stringToValue("Ecology");
    protected static final Bytes FIELDS = TestValue.stringToValue("Fields");
    protected static final Bytes GEOMETRY = TestValue.stringToValue("Geometry");

    protected static final Bytes AUSTRALIA = TestValue.stringToValue("Australia");
    protected static final Bytes BRAZIL = TestValue.stringToValue("Brazil");
    protected static final Bytes CHAD = TestValue.stringToValue("Chad");
    protected static final Bytes DENMARK = TestValue.stringToValue("Denmark");
    protected static final Bytes ESTONIA = TestValue.stringToValue("Estonia");
    protected static final Bytes FRANCE = TestValue.stringToValue("France");
    protected static final Bytes GHANA = TestValue.stringToValue("Ghana");

    protected static final long D_PATH = 6;
    protected static final long A_PATH = 7;
    protected static final long E_PATH = 8;
    protected static final long C_PATH = 9;
    protected static final long F_PATH = 10;
    protected static final long B_PATH = 11;
    protected static final long G_PATH = 12;
    protected static final long ROOT_PATH = 0;
    protected static final long LEFT_PATH = 1;
    protected static final long RIGHT_PATH = 2;
    protected static final long LEFT_LEFT_PATH = 3;
    protected static final long LEFT_RIGHT_PATH = 4;
    protected static final long RIGHT_LEFT_PATH = 5;

    protected List<VirtualNodeCache> rounds;
    protected VirtualNodeCache cache;
    private VirtualNodeCache lastCache;

    private VirtualHashRecord rootInternal;
    private VirtualHashRecord leftInternal;
    private VirtualHashRecord rightInternal;
    private VirtualHashRecord leftLeftInternal;
    private VirtualHashRecord leftRightInternal;
    private VirtualHashRecord rightLeftInternal;
    private VirtualLeafBytes lastALeaf;
    private VirtualLeafBytes lastBLeaf;
    private VirtualLeafBytes lastCLeaf;
    private VirtualLeafBytes lastDLeaf;
    private VirtualLeafBytes lastELeaf;
    private VirtualLeafBytes lastFLeaf;
    private VirtualLeafBytes lastGLeaf;

    @BeforeAll
    static void globalSetup() throws Exception {
        // Ensure VirtualNodeCache.release() returns clean
        System.setProperty("syncCleaningPool", "true");
        final ConstructableRegistry registry = ConstructableRegistry.getInstance();
        registry.registerConstructable(new ClassConstructorPair(TestInternal.class, TestInternal::new));
        registry.registerConstructable(new ClassConstructorPair(TestLeaf.class, TestLeaf::new));
        registry.registerConstructables("com.swirlds.virtualmap");
        registry.registerConstructables("com.swirlds.virtualmap.test.fixtures");
        registry.registerConstructables("com.swirlds.common.crypto");
        registry.registerConstructable(new ClassConstructorPair(VirtualMap.class, VirtualMap::new));
    }

    @BeforeEach
    public void setup() {
        rounds = new ArrayList<>();
        cache = new VirtualNodeCache();
        rounds.add(cache);
        lastCache = null;
    }

    // NOTE: If nextRound automatically causes hashing, some tests in VirtualNodeCacheTest will fail or be invalid.
    protected void nextRound() {
        lastCache = cache;
        cache = cache.copy();
        rounds.add(cache);
    }

    protected VirtualHashRecord rootInternal() {
        rootInternal = rootInternal == null ? new VirtualHashRecord(ROOT_PATH) : copy(rootInternal);
        return rootInternal;
    }

    protected VirtualHashRecord leftInternal() {
        leftInternal = leftInternal == null ? new VirtualHashRecord(LEFT_PATH) : copy(leftInternal);
        return leftInternal;
    }

    protected VirtualHashRecord rightInternal() {
        rightInternal = rightInternal == null ? new VirtualHashRecord(RIGHT_PATH) : copy(rightInternal);
        return rightInternal;
    }

    protected VirtualHashRecord leftLeftInternal() {
        leftLeftInternal = leftLeftInternal == null ? new VirtualHashRecord(LEFT_LEFT_PATH) : copy(leftLeftInternal);
        return leftLeftInternal;
    }

    protected VirtualHashRecord leftRightInternal() {
        leftRightInternal =
                leftRightInternal == null ? new VirtualHashRecord(LEFT_RIGHT_PATH) : copy(leftRightInternal);
        return leftRightInternal;
    }

    protected VirtualHashRecord rightLeftInternal() {
        rightLeftInternal =
                rightLeftInternal == null ? new VirtualHashRecord(RIGHT_LEFT_PATH) : copy(rightLeftInternal);
        return rightLeftInternal;
    }

    protected VirtualLeafBytes leaf(long path, long key, long value) {
        return new VirtualLeafBytes(path, TestKey.longToKey(key), TestValue.longToValue(value));
    }

    protected VirtualLeafBytes appleLeaf(long path) {
        lastALeaf = lastALeaf == null ? new VirtualLeafBytes(path, A_KEY, APPLE) : copyWithPath(lastALeaf, APPLE, path);
        return lastALeaf;
    }

    protected VirtualLeafBytes bananaLeaf(long path) {
        lastBLeaf =
                lastBLeaf == null ? new VirtualLeafBytes(path, B_KEY, BANANA) : copyWithPath(lastBLeaf, BANANA, path);
        return lastBLeaf;
    }

    protected VirtualLeafBytes cherryLeaf(long path) {
        lastCLeaf =
                lastCLeaf == null ? new VirtualLeafBytes(path, C_KEY, CHERRY) : copyWithPath(lastCLeaf, CHERRY, path);
        return lastCLeaf;
    }

    protected VirtualLeafBytes dateLeaf(long path) {
        lastDLeaf = lastDLeaf == null ? new VirtualLeafBytes(path, D_KEY, DATE) : copyWithPath(lastDLeaf, DATE, path);
        return lastDLeaf;
    }

    protected VirtualLeafBytes eggplantLeaf(long path) {
        lastELeaf = lastELeaf == null
                ? new VirtualLeafBytes(path, E_KEY, EGGPLANT)
                : copyWithPath(lastELeaf, EGGPLANT, path);
        return lastELeaf;
    }

    protected VirtualLeafBytes figLeaf(long path) {
        lastFLeaf = lastFLeaf == null ? new VirtualLeafBytes(path, F_KEY, FIG) : copyWithPath(lastFLeaf, FIG, path);
        return lastFLeaf;
    }

    protected VirtualLeafBytes grapeLeaf(long path) {
        lastGLeaf = lastGLeaf == null ? new VirtualLeafBytes(path, G_KEY, GRAPE) : copyWithPath(lastGLeaf, GRAPE, path);
        return lastGLeaf;
    }

    protected VirtualLeafBytes aardvarkLeaf(long path) {
        lastALeaf = lastALeaf == null
                ? new VirtualLeafBytes(path, A_KEY, AARDVARK)
                : copyWithPath(lastALeaf, AARDVARK, path);
        return lastALeaf;
    }

    protected VirtualLeafBytes bearLeaf(long path) {
        lastBLeaf = lastBLeaf == null ? new VirtualLeafBytes(path, B_KEY, BEAR) : copyWithPath(lastBLeaf, BEAR, path);
        return lastBLeaf;
    }

    protected VirtualLeafBytes cuttlefishLeaf(long path) {
        lastCLeaf = lastCLeaf == null
                ? new VirtualLeafBytes(path, C_KEY, CUTTLEFISH)
                : copyWithPath(lastCLeaf, CUTTLEFISH, path);
        return lastCLeaf;
    }

    protected VirtualLeafBytes dogLeaf(long path) {
        lastDLeaf = lastDLeaf == null ? new VirtualLeafBytes(path, D_KEY, DOG) : copyWithPath(lastDLeaf, DOG, path);
        return lastDLeaf;
    }

    protected VirtualLeafBytes emuLeaf(long path) {
        lastELeaf = lastELeaf == null ? new VirtualLeafBytes(path, E_KEY, EMU) : copyWithPath(lastELeaf, EMU, path);
        return lastELeaf;
    }

    protected VirtualLeafBytes foxLeaf(long path) {
        lastFLeaf = lastFLeaf == null ? new VirtualLeafBytes(path, F_KEY, FOX) : copyWithPath(lastFLeaf, FOX, path);
        return lastFLeaf;
    }

    protected VirtualLeafBytes gooseLeaf(long path) {
        lastGLeaf = lastGLeaf == null ? new VirtualLeafBytes(path, G_KEY, GOOSE) : copyWithPath(lastGLeaf, GOOSE, path);
        return lastGLeaf;
    }

    protected VirtualHashRecord hash(VirtualLeafBytes rec) {
        return new VirtualHashRecord(rec.path(), rec.hash(HASH_BUILDER));
    }

    private VirtualLeafBytes copyWithPath(VirtualLeafBytes leaf, Bytes value, long path) {
        return new VirtualLeafBytes(path, leaf.keyBytes(), value);
    }

    private VirtualHashRecord copy(VirtualHashRecord rec) {
        return new VirtualHashRecord(rec.path(), rec.hash());
    }

    public static final class TestInternal extends PartialBinaryMerkleInternal implements MerkleInternal {
        private String label;

        public TestInternal() {
            // For serialization
        }

        public TestInternal(String label) {
            this.label = label;
        }

        private TestInternal(TestInternal other) {
            super(other);
            this.label = other.label;
        }

        @Override
        public long getClassId() {
            return 1234;
        }

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public TestInternal copy() {
            return new TestInternal(this);
        }

        @Override
        public String toString() {
            return "TestInternal{" + "label='" + label + '\'' + '}';
        }
    }

    public static final class TestLeaf extends PartialMerkleLeaf implements MerkleLeaf {
        private String value;

        // For serialization engine
        public TestLeaf() {}

        public TestLeaf(String value) {
            this.value = value;
        }

        private TestLeaf(TestLeaf other) {
            super(other);
            this.value = other.value;
        }

        @Override
        public long getClassId() {
            return 2345;
        }

        @Override
        public int getVersion() {
            return 1;
        }

        @Override
        public void serialize(SerializableDataOutputStream out) throws IOException {
            out.writeUTF(value);
        }

        @Override
        public void deserialize(SerializableDataInputStream in, int version) throws IOException {
            this.value = in.readUTF();
        }

        @Override
        public TestLeaf copy() {
            return new TestLeaf(this);
        }

        @Override
        public String toString() {
            return "TestLeaf{" + "value='" + value + '\'' + '}';
        }
    }
}
