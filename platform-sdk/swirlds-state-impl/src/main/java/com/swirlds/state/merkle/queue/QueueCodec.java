package com.swirlds.state.merkle.queue;

import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;

public class QueueCodec implements Codec<QueueState> {

    public static final Codec<QueueState> INSTANCE = new QueueCodec();

    @NonNull
    @Override
    public QueueState parse(@NonNull ReadableSequentialData input, boolean strictMode, int maxDepth) throws ParseException {
        return new QueueState(input);
    }

    @Override
    public void write(@NonNull QueueState item, @NonNull WritableSequentialData output) throws IOException {
        item.writeTo(output);
    }

    @Override
    public int measure(@NonNull ReadableSequentialData input) throws ParseException {
        final var start = input.position();
        parse(input);
        final var end = input.position();
        return (int)(end - start);
    }

    @Override
    public int measureRecord(QueueState item) {
        return item.getSizeInBytes();
    }

    @Override
    public boolean fastEquals(@NonNull QueueState item, @NonNull ReadableSequentialData input) throws ParseException {
        return item.equals(parse(input));
    }
}
