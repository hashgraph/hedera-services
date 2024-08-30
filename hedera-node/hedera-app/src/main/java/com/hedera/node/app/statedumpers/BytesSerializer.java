package com.hedera.node.app.statedumpers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.hedera.pbj.runtime.io.buffer.Bytes;

import java.io.IOException;

public class BytesSerializer extends JsonSerializer<Bytes> {
    @Override
    public void serialize(final Bytes value, final JsonGenerator gen, final SerializerProvider serializers) throws IOException {
        gen.writeString(value.toHex());
    }
}