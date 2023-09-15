package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.freeze;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_HEADLONG_ADDRESS;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.freeze.FreezeUnfreezeDecoder;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.freeze.FreezeUnfreezeTranslator;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FreezeUnfreezeDecoderTest {
    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private HtsCallAttempt attempt;
    private final FreezeUnfreezeDecoder subject = new FreezeUnfreezeDecoder();

    @Test
    void freezeWorks() {
        final var encoded = FreezeUnfreezeTranslator.FREEZE
                .encodeCallWithArgs(OWNER_HEADLONG_ADDRESS, FUNGIBLE_TOKEN_HEADLONG_ADDRESS)
                .array();
        given(attempt.inputBytes()).willReturn(encoded);
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        final var body = subject.decodeFreeze(attempt);
        assertFreezePresent(body, FUNGIBLE_TOKEN_ID);
    }

    @Test
    void unfreezeWorks() {
        final var encoded = FreezeUnfreezeTranslator.UNFREEZE
                .encodeCallWithArgs(OWNER_HEADLONG_ADDRESS, FUNGIBLE_TOKEN_HEADLONG_ADDRESS)
                .array();
        given(attempt.inputBytes()).willReturn(encoded);
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        final var body = subject.decodeUnfreeze(attempt);
        assertUnfreezePresent(body, FUNGIBLE_TOKEN_ID);
    }


    private void assertFreezePresent(@NonNull final TransactionBody body, @NonNull final TokenID tokenId) {
        final var freeze = body.tokenFreezeOrThrow();
        org.assertj.core.api.Assertions.assertThat(freeze.token()).isEqualTo(tokenId);
    }

    private void assertUnfreezePresent(@NonNull final TransactionBody body, @NonNull final TokenID tokenId) {
        final var unfreeze = body.tokenUnfreezeOrThrow();
        org.assertj.core.api.Assertions.assertThat(unfreeze.token()).isEqualTo(tokenId);
    }
}
