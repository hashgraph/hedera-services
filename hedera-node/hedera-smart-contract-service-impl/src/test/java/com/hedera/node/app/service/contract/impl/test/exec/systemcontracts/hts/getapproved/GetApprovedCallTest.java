package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.getapproved;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved.GetApprovedCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.getapproved.GetApprovedTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isapprovedforall.IsApprovedForAllCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ownerof.OwnerOfTranslator;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.*;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class GetApprovedCallTest extends HtsCallTestBase {

    private GetApprovedCall subject;

    @Test
    void revertsWithFungibleToken() {
        subject = new GetApprovedCall(mockEnhancement(), FUNGIBLE_TOKEN, 123L, true);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(revertOutputFor(INVALID_TOKEN_ID), result.getOutput());
    }

    @Test
    void getApprovedErc() {
        subject = new GetApprovedCall(mockEnhancement(), NON_FUNGIBLE_TOKEN, 123L, true);
        given(nativeOperations.getNft(9898L, 123)).willReturn(CIVILIAN_OWNED_NFT);
        given(nativeOperations.getAccount(B_NEW_ACCOUNT_ID.accountNumOrThrow())).willReturn(OPERATOR);


        final var result = subject.execute().fullResult().result();
        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(GetApprovedTranslator.ERC_GET_APPROVED
                        .getOutputs()
                        .encodeElements(headlongAddressOf(OPERATOR))
                        .array()),
                result.getOutput());
    }

    @Test
    void getApprovedHapi() {
        subject = new GetApprovedCall(mockEnhancement(), NON_FUNGIBLE_TOKEN, 123L, false);
        given(nativeOperations.getNft(9898L, 123)).willReturn(CIVILIAN_OWNED_NFT);
        given(nativeOperations.getAccount(B_NEW_ACCOUNT_ID.accountNumOrThrow())).willReturn(OPERATOR);


        final var result = subject.execute().fullResult().result();
        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(GetApprovedTranslator.HAPI_GET_APPROVED
                        .getOutputs()
                        .encodeElements(SUCCESS.getNumber(), headlongAddressOf(OPERATOR))
                        .array()),
                result.getOutput());
    }

}
