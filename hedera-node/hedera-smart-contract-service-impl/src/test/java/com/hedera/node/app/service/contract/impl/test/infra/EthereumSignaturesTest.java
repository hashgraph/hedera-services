package com.hedera.node.app.service.contract.impl.test.infra;

import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import com.hedera.node.app.service.contract.impl.infra.EthereumSignatures;
import org.junit.jupiter.api.Test;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ETH_DATA_WITH_TO_ADDRESS;
import static org.junit.jupiter.api.Assertions.assertEquals;

class EthereumSignaturesTest {
    private final EthereumSignatures subject = new EthereumSignatures();

    @Test
    void reportsExpectedSignatures() {
        final var expectedSigs = EthTxSigs.extractSignatures(ETH_DATA_WITH_TO_ADDRESS);

        assertEquals(expectedSigs, subject.impliedBy(ETH_DATA_WITH_TO_ADDRESS));
    }
}