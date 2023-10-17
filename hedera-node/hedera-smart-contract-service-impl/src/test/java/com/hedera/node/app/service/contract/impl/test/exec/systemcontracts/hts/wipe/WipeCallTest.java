package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.wipe;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asBytesResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.wipe.WipeCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.wipe.WipeTranslator;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import java.math.BigInteger;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class WipeCallTest extends HtsCallTestBase {

    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private CryptoTransferRecordBuilder recordBuilder;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Test
    void wipeCall_works() {
        // Given
        given(attempt.enhancement()).willReturn(mockEnhancement());
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(attempt.addressIdConverter().convertSender(any())).willReturn(OWNER_ID);

        final var subject =
                new WipeCall(attempt, TransactionBody.newBuilder().build());

        given(systemContractOperations.dispatch(any(), any(), any(), any())).willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(ResponseCodeEnum.SUCCESS);

        // When
        final var result = subject.execute().fullResult().result();

        // Then
        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(WipeTranslator.WIPE_FUNGIBLE_V1
                        .getOutputs()
                        .encodeElements(BigInteger.valueOf(ResponseCodeEnum.SUCCESS.protoOrdinal()))),
                result.getOutput());
    }
}