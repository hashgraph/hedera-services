package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.grantrevokekyc;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantrevokekyc.GrantRevokeKycDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantrevokekyc.GrantRevokeKycTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class GrantRevokeKycTranslatorTest {
    @Mock
    private HtsCallAttempt attempt;

    private final GrantRevokeKycDecoder decoder = new GrantRevokeKycDecoder();
    private GrantRevokeKycTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new GrantRevokeKycTranslator(decoder);
    }

    @Test
    void matchesGrantKycTest() {
        given(attempt.selector()).willReturn(GrantRevokeKycTranslator.GRANT_KYC.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesRevokeKycTest() {
        given(attempt.selector()).willReturn(GrantRevokeKycTranslator.REVOKE_KYC.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }
}
