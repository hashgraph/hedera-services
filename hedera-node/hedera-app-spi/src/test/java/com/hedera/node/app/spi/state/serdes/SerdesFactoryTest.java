package com.hedera.node.app.spi.state.serdes;

import com.hedera.node.app.spi.state.Serdes;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SerdesFactoryTest {
    @Mock
    private SerializableDataInputStream input;
    @Mock
    private SerializableDataOutputStream output;
    @Mock
    private PbjParser<String> parser;
    @Mock
    private PbjWriter<String> writer;

    private Serdes<String> subject;

    @BeforeEach
    void setUp() {
        subject = SerdesFactory.newInMemorySerdes(parser, writer);
    }

    @Test
    void unusedMethodsAreUnsupported() {
        assertThrows(UnsupportedOperationException.class, subject::typicalSize);
        assertThrows(UnsupportedOperationException.class, () -> subject.measure(input));
        assertThrows(UnsupportedOperationException.class, () -> subject.fastEquals("A", input));
    }

    @Test
    void delegatesWrite() throws IOException {
        subject.write("B", output);

        verify(writer).write(eq("B"), any());
    }

    @Test
    void delegatesParse() throws IOException {
        given(parser.parse(any())).willReturn("C");

        final var value = subject.parse(input);

        assertEquals("C", value);
    }
}