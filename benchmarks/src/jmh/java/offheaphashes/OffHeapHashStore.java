package offheaphashes;

import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * An off-heap in memory store of hashes, it stores them in 46Mb direct buffers and adds buffers as needed
 */
public final class OffHeapHashStore {
    private static final int HASH_SIZE =  Integer.BYTES+ DigestType.SHA_384.digestLength();
    private static final int NUM_HASHES_PER_CHUNK = 1_000_000;
    private static final int MEMORY_CHUNK_SIZE = NUM_HASHES_PER_CHUNK*HASH_SIZE;
    private final List<ByteBuffer> data = new CopyOnWriteArrayList<>();
    private final AtomicLong maxPathThatCanBeStored = new AtomicLong(-1);

    public void printStats() {
        System.out.println("data.size() = " + data.size());
        System.out.println("maxPathThatCanBeStored = " + maxPathThatCanBeStored.get());
    }

    /**
     * Load hash for a node with given path
     *
     * @param path the path to get hash for
     * @return loaded hash or null if hash is not stored
     * @throws IOException if there was a problem loading hash
     */
    public Hash loadHash(long path) throws IOException {
        if (path <= maxPathThatCanBeStored.get()) {
            return Hash.fromByteBuffer(getBuffer(path));
        } else {
            return null;
        }
    }

    /**
     * Save a hash for a node
     *
     * @param path the path of the node to save hash for, if nothing has been stored for this path before it will be created.
     * @param hash a non-null hash to write
     */
    public void saveHash(long path, Hash hash) {
        // expand data if needed
        maxPathThatCanBeStored.updateAndGet(currentValue -> {
            while (path > currentValue) { // need to expand
                data.add(ByteBuffer.allocateDirect(MEMORY_CHUNK_SIZE));
                currentValue += NUM_HASHES_PER_CHUNK;
            }
            return currentValue;
        });
        // get the right buffer
        Hash.toByteBuffer(hash,getBuffer(path));
    }

    /**
     * Get the ByteBuffer for a given key, assumes the buffer is already created.
     *
     * @param path the path to buffer
     * @return The ByteBuffer contain that path
     */
    private ByteBuffer getBuffer(long path) {
        int bufferIndex = (int) (path / (long) NUM_HASHES_PER_CHUNK);
        ByteBuffer buffer = data.get(bufferIndex).slice();
        int subPath = (int)(path % NUM_HASHES_PER_CHUNK);
        int offset = HASH_SIZE * subPath;
        buffer.position(offset);
        buffer.limit(offset+HASH_SIZE);
        return buffer;
    }

}
