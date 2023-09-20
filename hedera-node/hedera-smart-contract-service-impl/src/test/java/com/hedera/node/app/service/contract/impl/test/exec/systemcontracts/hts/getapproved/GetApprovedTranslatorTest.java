package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.getapproved;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved.GetApprovedCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved.GetApprovedTranslator;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.*;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.fromHeadlongAddress;
import static org.mockito.BDDMockito.given;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
public class GetApprovedTranslatorTest {
    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private Token token;

    @Mock
    private HederaWorldUpdater.Enhancement enhancement;

    private GetApprovedTranslator subject;

    @BeforeEach
    void setUp() {
        subject = new GetApprovedTranslator();
    }

    @Test
    void matchesErcGetApprovedTest() {
        given(attempt.selector()).willReturn(GetApprovedTranslator.ERC_GET_APPROVED.selector());
        given(attempt.isTokenRedirect()).willReturn(true);
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void matchesHapiGetApprovedTest() {
        given(attempt.selector()).willReturn(GetApprovedTranslator.HAPI_GET_APPROVED.selector());
        given(attempt.isTokenRedirect()).willReturn(false);
        final var matches = subject.matches(attempt);
        assertThat(matches).isTrue();
    }

    @Test
    void callFromErcGetApprovedTest() {
        Tuple tuple = new Tuple(BigInteger.valueOf(123L));
        Bytes inputBytes = Bytes.wrapByteBuffer(GetApprovedTranslator.ERC_GET_APPROVED.encodeCall(tuple));
        given(attempt.selector()).willReturn(GetApprovedTranslator.ERC_GET_APPROVED.selector());
        given(attempt.input()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(enhancement);

        final var call = subject.callFrom(attempt);
        assertThat(call).isInstanceOf(GetApprovedCall.class);
    }

    @Test
    void callFromHapiGetApprovedTest() {
        Tuple tuple = new Tuple(NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS, BigInteger.valueOf(123L));
        Bytes inputBytes = Bytes.wrapByteBuffer(GetApprovedTranslator.HAPI_GET_APPROVED.encodeCall(tuple));
        given(attempt.selector()).willReturn(GetApprovedTranslator.HAPI_GET_APPROVED.selector());
        given(attempt.input()).willReturn(inputBytes);
        given(attempt.enhancement()).willReturn(enhancement);
        given(attempt.linkedToken(fromHeadlongAddress(NON_FUNGIBLE_TOKEN_HEADLONG_ADDRESS)))
                .willReturn(token);

        final var call = subject.callFrom(attempt);
        assertThat(call).isInstanceOf(GetApprovedCall.class);
    }
}
