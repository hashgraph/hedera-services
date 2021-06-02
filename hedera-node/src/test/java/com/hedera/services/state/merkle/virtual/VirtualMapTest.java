package com.hedera.services.state.merkle.virtual;

import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;

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

    // TODO Delete items... how to do that? Set them to null I guess...
    // TODO Test hashing the tree

    private ByteChunk asBlock(String s) {
        return new ByteChunk(Arrays.copyOf(s.getBytes(StandardCharsets.UTF_8), 256));
    }

    // Simple implementation for testing purposes
    private static final class InMemoryDataSource implements VirtualDataSource<ByteChunk, ByteChunk> {
        private HashMap<ByteChunk, ByteChunk> data = new HashMap<>();
        private HashMap<Path, VirtualRecord<ByteChunk>> records = new HashMap<>();
        private HashMap<Path, Hash> nodes = new HashMap<>();
        private HashMap<ByteChunk, Path> paths = new HashMap<>();
        private Path firstLeafPath;
        private Path lastLeafPath;
        private boolean closed = false;

        @Override
        public VirtualRecord<ByteChunk> getRecord(Path path) {
            return records.get(path);
        }

        @Override
        public void writeRecord(Path path, VirtualRecord<ByteChunk> record) {
            if (record == null) {
                records.remove(path);
            } else {
                records.put(path, record);
            }
        }

        @Override
        public Hash getHash(Path path) {
            return nodes.get(path);
        }

        @Override
        public void writeHash(Path path, Hash hash) {
            if (hash == null) {
                nodes.remove(path);
            } else {
                nodes.put(path, hash);
            }
        }

        @Override
        public ByteChunk getData(ByteChunk key) {
            return data.get(key);
        }

        @Override
        public void writeData(ByteChunk key, ByteChunk data) {
            if (data == null) {
                this.data.remove(key);
            } else {
                this.data.put(key, data);
            }
        }

        @Override
        public void deleteData(ByteChunk key) {
            // TODO
        }

        @Override
        public Path getPathForKey(ByteChunk key) {
            return paths.get(key);
        }

        @Override
        public void setPathForKey(ByteChunk key, Path path) {
            if (path == null) {
                paths.remove(key);
            } else {
                paths.put(key, path);
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
