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

package contract;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToBesuAddress;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TimestampSeconds;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Instant;
import org.hyperledger.besu.datatypes.Address;

/**
 * Common constants used in "x-test" classes.
 */
class XTestConstants {
    /**
     * The exchange rate long used in dev environments to run HAPI spec; expected to be in effect for
     * some x-tests to pass.
     */
    static final ExchangeRate TRADITIONAL_HAPI_SPEC_RATE = ExchangeRate.newBuilder()
            .hbarEquiv(1)
            .centEquiv(12)
            .expirationTime(TimestampSeconds.newBuilder()
                    .seconds(Instant.MAX.getEpochSecond())
                    .build())
            .build();

    static final ExchangeRateSet SET_OF_TRADITIONAL_RATES = ExchangeRateSet.newBuilder()
            .currentRate(TRADITIONAL_HAPI_SPEC_RATE)
            .nextRate(TRADITIONAL_HAPI_SPEC_RATE)
            .build();
    static final AccountID MISC_PAYER_ID =
            AccountID.newBuilder().accountNum(950L).build();

    static final TransactionBody PLACEHOLDER_CALL_BODY = TransactionBody.newBuilder()
            .transactionID(TransactionID.newBuilder().accountID(MISC_PAYER_ID).build())
            .contractCall(ContractCallTransactionBody.DEFAULT)
            .build();

    static final AccountID SENDER_ID =
            AccountID.newBuilder().accountNum(12345789L).build();
    static final Bytes SENDER_ADDRESS =
            com.hedera.pbj.runtime.io.buffer.Bytes.fromHex("f91e624b8b8ea7244e8159ba7c0deeea2b6be990");
    static final Address SENDER_BESU_ADDRESS = pbjToBesuAddress(SENDER_ADDRESS);
    static final AccountID RECEIVER_ID =
            AccountID.newBuilder().accountNum(987654321L).build();
    static final com.esaulpaugh.headlong.abi.Address RECEIVER_HEADLONG_ADDRESS =
            asHeadlongAddress(asEvmAddress(RECEIVER_ID.accountNumOrThrow()));
    static final TokenID ERC721_TOKEN_ID = TokenID.newBuilder().tokenNum(1028L).build();
    static final NftID SN_1234 =
            NftID.newBuilder().tokenId(ERC721_TOKEN_ID).serialNumber(1234L).build();
    static final NftID SN_2345 =
            NftID.newBuilder().tokenId(ERC721_TOKEN_ID).serialNumber(2345L).build();
    static final Bytes SN_1234_METADATA = Bytes.wrap("https://example.com/721/" + 1234);
    static final Bytes SN_2345_METADATA = Bytes.wrap("https://example.com/721/" + 2345);
    static final com.esaulpaugh.headlong.abi.Address ERC721_TOKEN_ADDRESS = AbstractContractXTest.asHeadlongAddress(
            asLongZeroAddress(ERC721_TOKEN_ID.tokenNum()).toArray());
    static final TokenID ERC20_TOKEN_ID = TokenID.newBuilder().tokenNum(1027L).build();
}
