package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsFunctionSelectors;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.LegibleHtsCall;
import com.swirlds.common.utility.CommonUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsFunctionSelectors.REDIRECT_FOR_TOKEN;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.LegibleHtsCall.Type.TRANSFER;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_LONG_ZERO_ADDRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LegibleHtsCallTest {
    @ParameterizedTest
    @CsvSource({
            "false,"+ HtsFunctionSelectors.CRYPTO_TRANSFER,
            "false,"+ HtsFunctionSelectors.CRYPTO_TRANSFER_V2,
            "false,"+ HtsFunctionSelectors.TRANSFER_TOKEN,
            "false,"+ HtsFunctionSelectors.TRANSFER_TOKENS,
            "false,"+ HtsFunctionSelectors.TRANSFER_NFT,
            "false,"+ HtsFunctionSelectors.TRANSFER_NFTS,
            "false,"+ HtsFunctionSelectors.TRANSFER_FROM,
            "false,"+ HtsFunctionSelectors.TRANSFER_FROM_NFT,
            "true,"+ HtsFunctionSelectors.ERC_TRANSFER,
            "true,"+ HtsFunctionSelectors.ERC_TRANSFER_FROM,
    })
    void classifiesTransferAsExpected(boolean isRedirect, String hexedSelector) {
        final var selector = Address.fromHexString(hexedSelector).getInt(0);
        final var input = isRedirect ? bytesForRedirect(selector) : bytesFor(selector);
        final var subject = new LegibleHtsCall(input);

        assertEquals(TRANSFER, subject.type());
        assertEquals(selector, subject.selector());
        assertEquals(isRedirect, subject.isTokenRedirect());
        if (isRedirect) {
            assertEquals(selector, subject.redirectSelector());
            assertEquals(NON_SYSTEM_LONG_ZERO_ADDRESS, subject.redirectTokenAddress());
        } else {
            assertThrows(IllegalStateException.class, subject::redirectSelector);
            assertThrows(IllegalStateException.class, subject::redirectTokenAddress);
        }
    }

    private Bytes bytesFor(int selector) {
        return Bytes.ofUnsignedInt(selector);
    }

    private Bytes bytesForRedirect(int subSelector) {
        return Bytes.concatenate(
                Bytes.ofUnsignedInt(REDIRECT_FOR_TOKEN),
                NON_SYSTEM_LONG_ZERO_ADDRESS,
                Bytes.ofUnsignedInt(subSelector));
    }
}