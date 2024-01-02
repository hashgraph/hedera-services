/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asEvmAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToBesuAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.state.common.EntityIDPair;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.TokenRelation;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.utility.CommonUtils;
import java.util.Map;
import java.util.function.Consumer;
import org.hyperledger.besu.datatypes.Address;

/**
 * Common constants used in "x-test" classes.
 */
public class XTestConstants {

    public static final long THREE_MONTHS_IN_SECONDS = 7776000L;
    public static final AccountID MISC_PAYER_ID =
            AccountID.newBuilder().accountNum(950L).build();

    public static final TransactionBody PLACEHOLDER_CALL_BODY = TransactionBody.newBuilder()
            .transactionID(TransactionID.newBuilder().accountID(MISC_PAYER_ID).build())
            .contractCall(ContractCallTransactionBody.DEFAULT)
            .build();

    public static final AccountID SENDER_ID =
            AccountID.newBuilder().accountNum(12345789L).build();

    public static final Key SENDER_CONTRACT_ID_KEY = Key.newBuilder()
            .contractID(ContractID.newBuilder()
                    .contractNum(SENDER_ID.accountNumOrThrow())
                    .build())
            .build();
    public static final Bytes SENDER_ADDRESS =
            com.hedera.pbj.runtime.io.buffer.Bytes.fromHex("f91e624b8b8ea7244e8159ba7c0deeea2b6be990");
    public static final com.esaulpaugh.headlong.abi.Address SENDER_HEADLONG_ADDRESS =
            asHeadlongAddress(SENDER_ADDRESS.toByteArray());
    public static final Address SENDER_BESU_ADDRESS = pbjToBesuAddress(SENDER_ADDRESS);
    public static final Bytes BAD_SENDER =
            com.hedera.pbj.runtime.io.buffer.Bytes.fromHex("e22e624b8b8ea7244e8159ba7c0deeea2b6be991");
    public static final com.esaulpaugh.headlong.abi.Address INVALID_SENDER_HEADLONG_ADDRESS =
            asHeadlongAddress(BAD_SENDER.toByteArray());
    public static final AccountID RECEIVER_ID =
            AccountID.newBuilder().accountNum(987654321L).build();
    public static final AccountID INVALID_ID =
            AccountID.newBuilder().accountNum(Long.MAX_VALUE).build();
    public static final com.esaulpaugh.headlong.abi.Address RECEIVER_HEADLONG_ADDRESS =
            asHeadlongAddress(asEvmAddress(RECEIVER_ID.accountNumOrThrow()));
    public static final com.esaulpaugh.headlong.abi.Address LAZY_CREATE_TARGET_1_HEADLONG_ADDRESS =
            asHeadlongAddress(CommonUtils.unhex("abcdef1234567890abcdef1234567890abcdef12"));
    public static final com.esaulpaugh.headlong.abi.Address LAZY_CREATE_TARGET_2_HEADLONG_ADDRESS =
            asHeadlongAddress(CommonUtils.unhex("aaaaaa1234567890bbbbbb1234567890cccccc12"));
    public static final Address RECEIVER_BESU_ADDRESS =
            pbjToBesuAddress(Bytes.wrap(asEvmAddress(RECEIVER_ID.accountNumOrThrow())));
    public static final TokenID ERC721_TOKEN_ID =
            TokenID.newBuilder().tokenNum(1028L).build();
    public static final NftID SN_1234 =
            NftID.newBuilder().tokenId(ERC721_TOKEN_ID).serialNumber(1234L).build();
    public static final NftID SN_2345 =
            NftID.newBuilder().tokenId(ERC721_TOKEN_ID).serialNumber(2345L).build();
    public static final NftID SN_3456 =
            NftID.newBuilder().tokenId(ERC721_TOKEN_ID).serialNumber(3456L).build();
    public static final Bytes SN_1234_METADATA = Bytes.wrap("https://example.com/721/" + 1234);
    public static final Bytes SN_2345_METADATA = Bytes.wrap("https://example.com/721/" + 2345);
    public static final Bytes SN_3456_METADATA = Bytes.wrap("https://example.com/721/" + 3456);
    public static final com.esaulpaugh.headlong.abi.Address ERC721_TOKEN_ADDRESS =
            AbstractContractXTest.asHeadlongAddress(
                    asLongZeroAddress(ERC721_TOKEN_ID.tokenNum()).toArray());
    public static final TokenID ERC20_TOKEN_ID =
            TokenID.newBuilder().tokenNum(1027L).build();
    public static final com.esaulpaugh.headlong.abi.Address ERC20_TOKEN_ADDRESS =
            AbstractContractXTest.asHeadlongAddress(
                    asLongZeroAddress(ERC20_TOKEN_ID.tokenNum()).toArray());
    public static final TokenID OTHER_TOKEN_ID =
            TokenID.newBuilder().tokenNum(1777L).build();
    public static final com.esaulpaugh.headlong.abi.Address OTHER_TOKEN_ADDRESS =
            AbstractContractXTest.asHeadlongAddress(
                    asLongZeroAddress(OTHER_TOKEN_ID.tokenNum()).toArray());
    public static final AccountID OWNER_ID =
            AccountID.newBuilder().accountNum(121212L).build();
    public static final Bytes OWNER_ADDRESS = Bytes.fromHex("a213624b8b83a724438159ba7c0d333a2b6b3990");
    public static final Address OWNER_BESU_ADDRESS = pbjToBesuAddress(OWNER_ADDRESS);
    public static final com.esaulpaugh.headlong.abi.Address OWNER_HEADLONG_ADDRESS =
            asHeadlongAddress(OWNER_ADDRESS.toByteArray());
    public static final Bytes INVALID_ACCOUNT_ADDRESS = Bytes.wrap(asEvmAddress(INVALID_ID.accountNumOrThrow()));
    public static final com.esaulpaugh.headlong.abi.Address INVALID_ACCOUNT_HEADLONG_ADDRESS =
            asHeadlongAddress(asEvmAddress(INVALID_ID.accountNumOrThrow()));
    public static final Key AN_ED25519_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("0101010101010101010101010101010101010101010101010101010101010101"))
            .build();

    public static final TokenID INVALID_TOKEN_ID =
            TokenID.newBuilder().tokenNum(Long.MAX_VALUE).build();
    public static final com.esaulpaugh.headlong.abi.Address INVALID_TOKEN_ADDRESS =
            AbstractContractXTest.asHeadlongAddress(
                    asLongZeroAddress(INVALID_TOKEN_ID.tokenNum()).toArray());
    public static final Bytes SENDER_ALIAS =
            Bytes.fromHex("3a21030edcc130e13fb5102e7c883535af8c2b0a5a617231f77fd127ce5f3b9a620591");
    public static final long ONE_HBAR = 100_000_000L;
    public static final AccountID COINBASE_ID =
            AccountID.newBuilder().accountNum(98L).build();
    public static final Account TYPICAL_SENDER_ACCOUNT = Account.newBuilder()
            .accountId(SENDER_ID)
            .alias(SENDER_ALIAS)
            .key(AN_ED25519_KEY)
            .tinybarBalance(100 * ONE_HBAR)
            .build();
    public static final Account TYPICAL_SENDER_CONTRACT = Account.newBuilder()
            .accountId(OWNER_ID)
            .alias(SENDER_ADDRESS)
            .key(SENDER_CONTRACT_ID_KEY)
            .smartContract(true)
            .build();

    public static void addErc721Relation(
            final Map<EntityIDPair, TokenRelation> tokenRelationships, final AccountID accountID, final long balance) {
        tokenRelationships.put(
                EntityIDPair.newBuilder()
                        .tokenId(ERC721_TOKEN_ID)
                        .accountId(accountID)
                        .build(),
                TokenRelation.newBuilder()
                        .tokenId(ERC721_TOKEN_ID)
                        .accountId(accountID)
                        .balance(balance)
                        .kycGranted(true)
                        .build());
    }

    public static void addErc20Relation(
            final Map<EntityIDPair, TokenRelation> tokenRelationships, final AccountID accountID, final long balance) {
        tokenRelationships.put(
                EntityIDPair.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .accountId(accountID)
                        .build(),
                TokenRelation.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .accountId(accountID)
                        .balance(balance)
                        .kycGranted(true)
                        .build());
    }

    public static final org.apache.tuweni.bytes.Bytes SUCCESS_AS_BYTES =
            org.apache.tuweni.bytes.Bytes.wrap(ReturnTypes.encodedRc(SUCCESS).array());

    public static Consumer<org.apache.tuweni.bytes.Bytes> assertSuccess() {
        return output -> assertEquals(SUCCESS_AS_BYTES, output);
    }

    public static Consumer<org.apache.tuweni.bytes.Bytes> assertSuccess(String orElseMessage) {
        return output -> assertEquals(SUCCESS_AS_BYTES, output, orElseMessage);
    }
}
