package com.hedera.node.app.service.util.impl.test.records;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hedera.node.app.service.util.impl.records.UtilPrngRecordBuilder;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UtilPrngRecordBuilderTest {
    private UtilPrngRecordBuilder subject;

    @BeforeEach
    void setUp() {
        subject = new UtilPrngRecordBuilder();
    }

    @Test
    void emptyConstructor() {
        assertNull(subject.getGeneratedBytes());
        assertNull(subject.getGeneratedNumber());
    }

}
