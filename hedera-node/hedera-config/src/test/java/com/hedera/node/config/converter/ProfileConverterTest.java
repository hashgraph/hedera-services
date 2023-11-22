package com.hedera.node.config.converter;

import com.hedera.node.config.types.Profile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ProfileConverterTest {
    @Test
    void testNullParam() {
        // given
        final ProfileConverter converter = new ProfileConverter();

        // then
        assertThatThrownBy(() -> converter.convert(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInvalidParam() {
        // given
        final ProfileConverter converter = new ProfileConverter();

        // then
        assertThatThrownBy(() -> converter.convert("null")).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @EnumSource(Profile.class)
    void testValidParam(final Profile value) {
        // given
        final ProfileConverter converter = new ProfileConverter();

        // when
        final Profile profile = converter.convert(value.name());

        // then
        assertThat(profile).isEqualTo(value);
    }
}
