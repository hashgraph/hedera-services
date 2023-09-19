package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.setapproval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval.SetApprovalForAllDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.setapproval.SetApprovalForAllTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SetApprovalForAllTranslatorTest {

    @Mock
    private HtsCallAttempt attempt;

    private final SetApprovalForAllDecoder decoder = new SetApprovalForAllDecoder();

    private SetApprovalForAllTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new SetApprovalForAllTranslator(decoder);
    }

    @Test
    void matchesTest() {
        given(attempt.selector()).willReturn(SetApprovalForAllTranslator.SET_APPROVAL_FOR_ALL.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

}
