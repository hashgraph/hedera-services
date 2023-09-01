package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.ownerof;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ownerof.OwnerOfCall;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ALIASED_SOMEBODY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CIVILIAN_OWNED_NFT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NFT_SERIAL_NO;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.TREASURY_OWNED_NFT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.revertOutputFor;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

class OwnerOfCallTest extends HtsCallTestBase {

    private OwnerOfCall subject;

    @Test
    void revertsWithMissingToken() {
        subject = new OwnerOfCall(mockEnhancement(), null, NFT_SERIAL_NO);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(revertOutputFor(INVALID_TOKEN_ID), result.getOutput());
    }

    @Test
    void revertsWithMissingNft() {
        subject = new OwnerOfCall(mockEnhancement(), NON_FUNGIBLE_TOKEN, NFT_SERIAL_NO);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(revertOutputFor(INVALID_NFT_ID), result.getOutput());
    }

    @Test
    void revertsWithMissingOwner() {
        subject = new OwnerOfCall(mockEnhancement(), NON_FUNGIBLE_TOKEN, NFT_SERIAL_NO);
        given(nativeOperations.getNft(NON_FUNGIBLE_TOKEN_ID.tokenNum(), NFT_SERIAL_NO))
                .willReturn(CIVILIAN_OWNED_NFT);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(revertOutputFor(INVALID_ACCOUNT_ID), result.getOutput());
    }

    @Test
    void returnsUnaliasedOwnerLongZeroForPresentTokenAndNonTreasuryNft() {
        subject = new OwnerOfCall(mockEnhancement(), NON_FUNGIBLE_TOKEN, NFT_SERIAL_NO);
        given(nativeOperations.getNft(NON_FUNGIBLE_TOKEN_ID.tokenNum(), NFT_SERIAL_NO))
                .willReturn(CIVILIAN_OWNED_NFT);
        final long ownerNum = CIVILIAN_OWNED_NFT.ownerIdOrThrow().accountNumOrThrow();
        given(nativeOperations.getAccount(ownerNum)).willReturn(TestHelpers.SOMEBODY);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(OwnerOfCall.OWNER_OF
                        .getOutputs()
                        .encodeElements(asHeadlongAddress(asLongZeroAddress(ownerNum)))
                        .array()),
                result.getOutput());
    }

    @Test
    void returnsAliasedOwnerLongZeroForPresentTokenAndTreasuryNft() {
        subject = new OwnerOfCall(mockEnhancement(), NON_FUNGIBLE_TOKEN, NFT_SERIAL_NO);
        given(nativeOperations.getNft(NON_FUNGIBLE_TOKEN_ID.tokenNum(), NFT_SERIAL_NO))
                .willReturn(TREASURY_OWNED_NFT);
        final long ownerNum = NON_FUNGIBLE_TOKEN.treasuryAccountIdOrThrow().accountNumOrThrow();
        given(nativeOperations.getAccount(ownerNum)).willReturn(TestHelpers.ALIASED_SOMEBODY);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(OwnerOfCall.OWNER_OF
                        .getOutputs()
                        .encodeElements(asHeadlongAddress(ALIASED_SOMEBODY.alias().toByteArray()))
                        .array()),
                result.getOutput());
    }
}