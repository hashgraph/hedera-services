package com.hedera.node.app.workflows.handle.validation;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static com.hedera.hapi.node.base.ResponseCodeEnum.BAD_ENCODING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ZERO_BYTE_IN_STRING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hedera.node.app.workflows.handle.validation.StandardizedAttributeValidator.MAX_NESTED_KEY_LEVELS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;


@ExtendWith(MockitoExtension.class)
class StandardizedAttributeValidatorTest {
    private static final byte[] MOCK_ED25519_KEY = "abcdefghabcdefghabcdefghabcdefgh".getBytes();

    @Mock
    private GlobalDynamicProperties dynamicProperties;

    private StandardizedAttributeValidator subject;

    @BeforeEach
    void setUp() {
        subject = new StandardizedAttributeValidator(dynamicProperties);
    }

    @Test
    void memoCheckWorks() {
        given(dynamicProperties.maxMemoUtf8Bytes()).willReturn(100);

        final char[] aaa = new char[101];
        Arrays.fill(aaa, 'a');

        assertDoesNotThrow(() -> subject.validateMemo("OK"));
        assertFailsWith(MEMO_TOO_LONG, () -> subject.validateMemo(new String(aaa)));
        assertFailsWith(INVALID_ZERO_BYTE_IN_STRING, () -> subject.validateMemo("Not s\u0000 ok!"));
    }

    @Test
    void rejectsOverlyNestedKey() {
        final var acceptablyNested = nestKeys(Key.newBuilder(), MAX_NESTED_KEY_LEVELS - 1).build();
        final var overlyNested = nestKeys(Key.newBuilder(), MAX_NESTED_KEY_LEVELS).build();
        assertDoesNotThrow(() -> subject.validateKey(acceptablyNested));
        assertFailsWith(BAD_ENCODING, () -> subject.validateKey(overlyNested));
    }

    @Test
    void unsetKeysAreNotValid() {
        assertFailsWith(BAD_ENCODING, () -> subject.validateKey(Key.DEFAULT));
    }

    private void assertFailsWith(final ResponseCodeEnum expected, final Executable action) {
        final var e = assertThrows(HandleException.class, action);
        // expect:
        assertEquals(expected, e.getStatus());
    }

    private static Key.Builder nestKeys(
            final Key.Builder builder,
            final int additionalLevels) {
        if (additionalLevels == 0) {
            builder.ed25519(Bytes.wrap(MOCK_ED25519_KEY));
            return builder;
        } else {
            var nestedBuilder = Key.newBuilder();
            nestKeys(nestedBuilder, additionalLevels - 1);
            if (additionalLevels % 2 == 0) {
                builder.keyList(KeyList.newBuilder().keys(nestedBuilder.build()));
            } else {
                builder.thresholdKey(ThresholdKey.newBuilder()
                        .threshold(1)
                        .keys(KeyList.newBuilder().keys(nestedBuilder.build())));
            }
            return builder;
        }
    }
}