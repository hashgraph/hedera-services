/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.airdrops;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.airdrops.TokenAirdropTranslator.TOKEN_AIRDROP;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_ACCOUNT_AS_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.token.TokenAirdropTransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.airdrops.TokenAirdropDecoder;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.TokensConfig;
import com.swirlds.config.api.Configuration;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TokenAirdropDecoderTest {

    @Mock
    private HtsCallAttempt attempt;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private Configuration configuration;

    @Mock
    private TokensConfig tokensConfig;

    @Mock
    private LedgerConfig ledgerConfig;

    private TokenAirdropDecoder subject;

    private final TokenAirdropTransactionBody tokenAirdrop = TokenAirdropTransactionBody.newBuilder()
            .tokenTransfers(TokenTransferList.newBuilder()
                    .token(FUNGIBLE_TOKEN_ID)
                    .transfers(
                            AccountAmount.newBuilder()
                                    .accountID(SENDER_ID)
                                    .amount(-10)
                                    .build(),
                            AccountAmount.newBuilder()
                                    .accountID(OWNER_ID)
                                    .amount(10)
                                    .build())
                    .build())
            .build();

    @BeforeEach
    void setUp() {
        subject = new TokenAirdropDecoder();
        given(addressIdConverter.convert(asHeadlongAddress(SENDER_ID.accountNum())))
                .willReturn(SENDER_ID);
        given(addressIdConverter.convert(OWNER_ACCOUNT_AS_ADDRESS)).willReturn(OWNER_ID);
    }

    @Test
    void tokenAirdropDecoderWorks() {
        final var tuple = new Tuple[] {
            Tuple.of(
                    FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                    new Tuple[] {
                        Tuple.of(asHeadlongAddress(SENDER_ID.accountNum()), -10L, false),
                        Tuple.of(OWNER_ACCOUNT_AS_ADDRESS, 10L, false)
                    },
                    new Tuple[] {})
        };
        final var encoded = Bytes.wrapByteBuffer(TOKEN_AIRDROP.encodeCall(Tuple.singleton(tuple)));
        given(attempt.inputBytes()).willReturn(encoded.toArrayUnsafe());
        given(attempt.configuration()).willReturn(configuration);
        given(attempt.addressIdConverter()).willReturn(addressIdConverter);
        given(configuration.getConfigData(TokensConfig.class)).willReturn(tokensConfig);
        given(configuration.getConfigData(LedgerConfig.class)).willReturn(ledgerConfig);
        given(tokensConfig.maxAllowedAirdropTransfersPerTx()).willReturn(10);
        given(ledgerConfig.tokenTransfersMaxLen()).willReturn(10);
        given(ledgerConfig.nftTransfersMaxLen()).willReturn(10);
        final var body = subject.decodeAirdrop(attempt);
        assertNotNull(body);
        assertNotNull(body.tokenAirdrop());
        assertNotNull(body.tokenAirdrop().tokenTransfers());
    }
}
