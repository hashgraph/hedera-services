package com.hedera.node.app.spi.state.serdes;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VirtualKeySerdesAdapterTest extends AbstractVirtualSerdesTest<VirtualBlobKey> {
    private static final int NUM_INSTANCES = 42;
    private static final SplittableRandom RANDOM = new SplittableRandom();
    private static final VirtualBlobKeySerializer SERIALIZER = new VirtualBlobKeySerializer();

    public VirtualKeySerdesAdapterTest() {
        super(MonoSerdesAdapter.serdesForVirtualKey(
                VirtualBlobKey.CURRENT_VERSION,
                VirtualBlobKey::new,
                SERIALIZER));
    }

    @Test
    void canMeasureKeySize() throws IOException {
        final var key = new VirtualBlobKey(VirtualBlobKey.Type.FILE_DATA, RANDOM.nextInt());
        final var bb = new ByteBufferDataInput(ByteBuffer.wrap(writeUsingBuffer(key)));
        final var expected = SERIALIZER.getSerializedSize();
        final var actual = subject.measure(bb);
        assertEquals(expected, actual);
    }

    @Test
    void canGetTypicalSize() {
        final var expected = SERIALIZER.getSerializedSize();
        final var actual = subject.typicalSize();
        assertEquals(expected, actual);
    }

    @Test
    void doesNotSupportFastEquals() {
        final var key = new VirtualBlobKey(VirtualBlobKey.Type.FILE_DATA, RANDOM.nextInt());
        final var bb = new ByteBufferDataInput(ByteBuffer.wrap(writeUsingBuffer(key)));
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.fastEquals(key, bb));
    }

    public static Stream<VirtualBlobKey> randomInstances() {
        return Stream.generate(() -> new VirtualBlobKey(randomType(), RANDOM.nextInt()))
                .limit(NUM_INSTANCES);
    }

    private static VirtualBlobKey.Type randomType() {
        return VirtualBlobKey.Type.values()[RANDOM.nextInt(VirtualBlobKey.Type.values().length)];
    }
}