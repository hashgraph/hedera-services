package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.burn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.burn.BurnTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BurnTranslatorTest {

    @Mock
    private HtsCallAttempt attempt;

    private BurnTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new BurnTranslator();
    }

    @Test
    void matchesBurnTokenV1() {
        given(attempt.selector()).willReturn(BurnTranslator.BURN_TOKEN_V1.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesBurnTokenV2() {
        given(attempt.selector()).willReturn(BurnTranslator.BURN_TOKEN_V2.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }
}
