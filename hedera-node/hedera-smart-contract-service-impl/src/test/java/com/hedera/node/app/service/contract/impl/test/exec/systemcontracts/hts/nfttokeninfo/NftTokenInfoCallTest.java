/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.nfttokeninfo;

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.*;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.nfttokeninfo.NftTokenInfoCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.nfttokeninfo.NftTokenInfoTranslator;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import com.hedera.node.config.data.LedgerConfig;
import com.swirlds.config.api.Configuration;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NftTokenInfoCallTest extends HtsCallTestBase {
    @Mock
    private Configuration config;

    @Mock
    private LedgerConfig ledgerConfig;

    @Test
    void returnsNftTokenInfoStatusForPresentToken() {
        final var ledgerId = com.hedera.pbj.runtime.io.buffer.Bytes.fromHex(LEDGER_ID);
        when(config.getConfigData(LedgerConfig.class)).thenReturn(ledgerConfig);
        when(ledgerConfig.id()).thenReturn(ledgerId);
        when(nativeOperations.getNft(FUNGIBLE_EVERYTHING_TOKEN.tokenId().tokenNum(), 2L))
                .thenReturn(CIVILIAN_OWNED_NFT);
        when(nativeOperations.getAccount(A_NEW_ACCOUNT_ID.accountNum())).thenReturn(A_NEW_ACCOUNT);

        final var subject =
                new NftTokenInfoCall(gasCalculator, mockEnhancement(), false, FUNGIBLE_EVERYTHING_TOKEN, 2L, config);

        String ledgerIdBytes = Bytes.wrap(ledgerId.toByteArray()).toString();
        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(NftTokenInfoTranslator.NON_FUNGIBLE_TOKEN_INFO
                        .getOutputs()
                        .encodeElements(
                                SUCCESS.protoOrdinal(),
                                Tuple.of(
                                        Tuple.of(
                                                Tuple.of(
                                                        "Fungible Everything Token",
                                                        "FET",
                                                        headlongAddressOf(SENDER_ID),
                                                        "The memo",
                                                        true,
                                                        88888888L,
                                                        true,
                                                        EXPECTE_KEYLIST.toArray(new Tuple[0]),
                                                        Tuple.of(100L, headlongAddressOf(SENDER_ID), 200L)),
                                                7777777L,
                                                false,
                                                true,
                                                true,
                                                EXPECTED_FIXED_CUSTOM_FEES.toArray(new Tuple[0]),
                                                EXPECTED_FRACTIONAL_CUSTOM_FEES.toArray(new Tuple[0]),
                                                EXPECTED_ROYALTY_CUSTOM_FEES.toArray(new Tuple[0]),
                                                ledgerIdBytes),
                                        2L,
                                        headlongAddressOf(A_NEW_ACCOUNT),
                                        1000000L,
                                        com.hedera.pbj.runtime.io.buffer.Bytes.wrap("SOLD")
                                                .toByteArray(),
                                        headlongAddressOf(B_NEW_ACCOUNT)))
                        .array()),
                result.getOutput());
    }

    @Test
    void returnsWhenTryingToFetchTokenWithInvalidSerialNumber() {
        when(config.getConfigData(LedgerConfig.class)).thenReturn(ledgerConfig);
        when(ledgerConfig.id()).thenReturn(com.hedera.pbj.runtime.io.buffer.Bytes.fromHex(LEDGER_ID));
        when(nativeOperations.getNft(9876L, 0L)).thenReturn(null);
        when(nativeOperations.getAccount(1234L)).thenReturn(A_NEW_ACCOUNT);

        final var subject =
                new NftTokenInfoCall(gasCalculator, mockEnhancement(), false, FUNGIBLE_EVERYTHING_TOKEN, 0L, config);
        final var result = subject.execute().fullResult().result();
        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
    }

    void returnsNftTokenInfoStatusForMissingTokenStaticCall() {
        final var subject = new NftTokenInfoCall(gasCalculator, mockEnhancement(), true, null, 0L, config);

        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(revertOutputFor(INVALID_TOKEN_ID), result.getOutput());
    }
}
