package com.hedera.services.state.merkle.virtual;

import com.hedera.services.state.merkle.virtual.persistence.VirtualDataSource;
import com.hedera.services.state.merkle.virtual.persistence.VirtualRecord;
import com.swirlds.common.crypto.Hash;
import io.netty.buffer.ByteBufHolder;
import org.checkerframework.checker.units.qual.K;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class VirtualMapTest {

    @Test
    public void lookupNonExistent() {
        final var ds = new InMemoryDataSource();
        final var v = new VirtualMap<ByteChunk, ByteChunk>();
        v.setDataSource(ds);

        final var key = asBlock("DoesNotExist");
        Assertions.assertNull(v.getValue(key));
    }

    @Test
    public void putAndGetItem() {
        final var ds = new InMemoryDataSource();
        final var v = new VirtualMap<ByteChunk, ByteChunk>();
        v.setDataSource(ds);

        final var key = asBlock("fruit");
        final var value = asBlock("apple");

        v.putValue(key, value);
        Assertions.assertEquals(value, v.getValue(key));
    }

    @Test
    public void replaceItem() {
        final var ds = new InMemoryDataSource();
        final var v = new VirtualMap<ByteChunk, ByteChunk>();
        v.setDataSource(ds);

        final var key = asBlock("fruit");
        final var value = asBlock("apple");
        final var value2 = asBlock("banana");

        v.putValue(key, value);
        v.putValue(key, value2);
        Assertions.assertEquals(value2, v.getValue(key));
    }

    @Test
    public void addManyItemsAndFindThemAll() {
        final var ds = new InMemoryDataSource();
        final var v = new VirtualMap<ByteChunk, ByteChunk>();
        v.setDataSource(ds);

        final var expected = new HashMap<ByteChunk, ByteChunk>();
        for (int i=0; i<10_000_000; i++) {
            final var key = asBlock(i + "");
            final var value = asBlock((i + 100_000_000) + "");
            expected.put(key, value);
            v.putValue(key, value);
        }

        expected.forEach((key, value) -> Assertions.assertEquals(value, v.getValue(key)));
    }

    @Test
    public void addManyItemsUsingManyMapsAndReadThemAllFromOne() {
        final var ds = new InMemoryDataSource();
        var v = new VirtualMap<ByteChunk, ByteChunk>();
        v.setDataSource(ds);

        final var expected = new HashMap<ByteChunk, ByteChunk>();
        for (int i=0; i<1_000_000; i++) {
            if (i > 0 && i % 15 == 0) {
                v = new VirtualMap<ByteChunk, ByteChunk>();
                v.setDataSource(ds);
            }
            final var key = asBlock(i + "");
            final var value = asBlock((i + 100_000_000) + "");
            expected.put(key, value);
            v.putValue(key, value);
//            System.out.println(v.getAsciiArt());
        }

        final var fv = v;
        expected.forEach((key, value) -> Assertions.assertEquals(value, fv.getValue(key)));
    }

    @Test
    public void quickAndDirty() {
        final var ds = new InMemoryDataSource();
        VirtualMap<ByteChunk, ByteChunk> map = new VirtualMap<>();
        map.setDataSource(ds);
        for (int i=0; i<1_000_000; i++) {
            final var key = asKey(i);
            final var value = asValue(i);
            map.putValue(key, value);
//            System.out.println(map.getAsciiArt());
        }

        Random rand = new Random();
        for (int idx=0; idx<10_000; idx++) {
            map = new VirtualMap<ByteChunk, ByteChunk>();
            map.setDataSource(ds);
            for (int j = 0; j < 100; j++) {
                final var i = rand.nextInt(1_000_000);
                map.putValue(asKey(i), asValue(i + 1_000_000));
            }
        }
    }

    private ByteChunk asKey(int index) {
        return new ByteChunk(Arrays.copyOf(("key" + index).getBytes(), 32));
    }

    private ByteChunk asValue(int index) {
        return new ByteChunk(Arrays.copyOf(("val" + index).getBytes(), 32));
    }

    // TODO Delete items... how to do that? Set them to null I guess...
    // TODO Test hashing the tree

    private ByteChunk asBlock(String s) {
        return new ByteChunk(Arrays.copyOf(s.getBytes(StandardCharsets.UTF_8), 32));
    }

    // Simple implementation for testing purposes
    private static final class InMemoryDataSource implements VirtualDataSource<ByteChunk, ByteChunk> {
        private Map<ByteChunk, VirtualRecord<ByteChunk, ByteChunk>> recordsByKey = new HashMap<>();
        private Map<Path, VirtualRecord<ByteChunk, ByteChunk>> recordsByPath = new HashMap<>();
        private Path firstLeafPath;
        private Path lastLeafPath;
        private boolean closed = false;

        @Override
        public VirtualRecord<ByteChunk, ByteChunk> getRecord(ByteChunk key) {
            return recordsByKey.get(key);
        }

        @Override
        public VirtualRecord<ByteChunk, ByteChunk> getRecord(Path path) {
            return recordsByPath.get(path);
        }

        @Override
        public void deleteRecord(VirtualRecord<ByteChunk, ByteChunk> record) {
            if (record != null) {
                recordsByPath.remove(record.getPath());
                recordsByKey.remove(record.getKey());
            }
        }

        @Override
        public void setRecord(VirtualRecord<ByteChunk, ByteChunk> record) {
            if (record != null) {
                if (record.isLeaf()) {
                    recordsByKey.put(record.getKey(), record);
                }
                recordsByPath.put(record.getPath(), record);
            }
        }

        @Override
        public void writeLastLeafPath(Path path) {
            this.lastLeafPath = path;
        }

        @Override
        public Path getLastLeafPath() {
            return lastLeafPath;
        }

        @Override
        public void writeFirstLeafPath(Path path) {
            this.firstLeafPath = path;
        }

        @Override
        public Path getFirstLeafPath() {
            return firstLeafPath;
        }

        @Override
        public void close() throws IOException {
            if (closed) {
                throw new IOException("Already closed");
            }
            closed = true;
        }
    }
}
