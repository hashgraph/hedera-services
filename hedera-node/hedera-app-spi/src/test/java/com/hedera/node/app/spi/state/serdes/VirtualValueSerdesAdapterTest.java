package com.hedera.node.app.spi.state.serdes;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class VirtualValueSerdesAdapterTest extends AbstractVirtualSerdesTest<VirtualBlobValue> {
    private static final int NUM_INSTANCES = 42;
    private static final SplittableRandom RANDOM = new SplittableRandom();

    public VirtualValueSerdesAdapterTest() {
        super(MonoMapSerdesAdapter.serdesForVirtualValue(
                VirtualBlobValue.CURRENT_VERSION,
                VirtualBlobValue::new));
    }

    @Test
    void doesNotSupportMeasuring() {
        final var bytes = randomBytes();
        final var value = new VirtualBlobValue(bytes);
        final var bb = new ByteBufferDataInput(ByteBuffer.wrap(writeUsingBuffer(value)));
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.measure(bb));
    }

    @Test
    void doesNotSupportFastEquals() {
        final var value = new VirtualBlobValue(randomBytes());
        final var bb = new ByteBufferDataInput(ByteBuffer.wrap(writeUsingBuffer(value)));
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.fastEquals(value, bb));
    }

    @Test
    void doesNotSupportTypicalSize() {
        assertThrows(UnsupportedOperationException.class, subject::typicalSize);
    }

    public static Stream<VirtualBlobValue> randomInstances() {
        return Stream.generate(() -> new VirtualBlobValue(randomBytes()))
                .limit(NUM_INSTANCES);
    }

    private static byte[] randomBytes() {
        final var bytes = new byte[RANDOM.nextInt(1, MAX_SUPPORTED_SERIALIZED_SIZE / 32)];
        RANDOM.nextBytes(bytes);
        return bytes;
    }
}
