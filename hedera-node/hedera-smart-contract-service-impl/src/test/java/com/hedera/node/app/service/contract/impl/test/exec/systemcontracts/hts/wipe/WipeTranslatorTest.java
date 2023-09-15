package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.wipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.wipe.WipeDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.wipe.WipeTranslator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WipeTranslatorTest {

    @Mock
    private HtsCallAttempt attempt;

    private final WipeDecoder decoder = new WipeDecoder();

    private WipeTranslator subject;

    @BeforeEach
    void setup() {
        subject = new WipeTranslator(decoder);
    }

    @Test
    void matchesWipeFungibleV1Test() {
        given(attempt.selector()).willReturn(WipeTranslator.WIPE_FUNGIBLE_V1.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesWipeFungibleV2Test() {
        given(attempt.selector()).willReturn(WipeTranslator.WIPE_FUNGIBLE_V2.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesWipeNftTest() {
        given(attempt.selector()).willReturn(WipeTranslator.WIPE_NFT.selector());
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }
}
