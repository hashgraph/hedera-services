package com.hedera.node.app.workflows.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.Test;

class InsufficientBalanceExceptionTest {

    @Test
    void testConstructor() {
        // when
        final InsufficientBalanceException exception =
                new InsufficientBalanceException(ResponseCodeEnum.UNAUTHORIZED, 42L);

        // then
        assertThat(exception.responseCode()).isEqualTo(ResponseCodeEnum.UNAUTHORIZED);
        assertThat(exception.getEstimatedFee()).isEqualTo(42L);
        assertThat(exception.getMessage()).isNull();
    }

    @SuppressWarnings("ThrowableNotThrown")
    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new InsufficientBalanceException(null, 42L))
                .isInstanceOf(NullPointerException.class);
    }
}
