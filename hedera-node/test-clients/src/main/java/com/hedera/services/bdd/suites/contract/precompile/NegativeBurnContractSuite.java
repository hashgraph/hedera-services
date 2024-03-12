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

package com.hedera.services.bdd.suites.contract.precompile;

import static com.google.protobuf.ByteString.copyFromUtf8;
import static com.hedera.services.bdd.junit.TestTags.SMART_CONTRACT;
import static com.hedera.services.bdd.junit.TestTags.TOKEN;
import static com.hedera.services.bdd.spec.HapiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCall;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.contractCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.mintToken;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.uploadInitCode;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.childRecordsCheck;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sourcing;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.CONTRACT_REVERT_EXECUTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_BURN_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.HapiTestSuite;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.suites.HapiSuite;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.Tag;

@HapiTestSuite
@Tag(SMART_CONTRACT)
public class NegativeBurnContractSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(NegativeBurnContractSuite.class);
    public static final String CONTRACT = "NegativeBurnContract";
    private static final long GAS_TO_OFFER = 4_000_000L;
    private static final String NFT = "NFT";
    private static final String FIRST = "First!";
    private static final String SECOND = "Second!";
    private static final String CONTRACT_KEY = "ContractKey";

    public static void main(String... args) {
        new NegativeBurnContractSuite().runSuiteAsync();
    }

    @Override
    public boolean canRunConcurrent() {
        return true;
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(burnWithNegativeAmount());
    }

    @HapiTest
    final HapiSpec burnWithNegativeAmount() {
        final var negativeBurnFungible = "negativeBurnFungible";
        final var negativeBurnNFT = "negativeBurnNFT";
        final AtomicReference<Address> tokenAddress = new AtomicReference<>();
        final AtomicReference<Address> nftAddress = new AtomicReference<>();
        return defaultHapiSpec("burnWithNegativeAmount")
                .given(
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1_000L)
                                .exposingAddressTo(tokenAddress::set),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0L)
                                .supplyKey(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .exposingAddressTo(nftAddress::set),
                        mintToken(NFT, List.of(copyFromUtf8(FIRST), copyFromUtf8(SECOND))))
                .when(
                        sourcing(() -> contractCall(CONTRACT, "burnFungibleNegativeLong", tokenAddress.get())
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .via(negativeBurnFungible)
                                .logged()),
                        newKeyNamed(CONTRACT_KEY).shape(KeyShape.CONTRACT.signedWith(CONTRACT)),
                        tokenUpdate(NFT).supplyKey(CONTRACT_KEY),
                        sourcing(() -> contractCall(
                                        CONTRACT, "burnNFTNegativeLong", nftAddress.get(), new long[] {1L, 2L})
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(SUCCESS)
                                .payingWith(TOKEN_TREASURY)
                                .signingWith(TOKEN_TREASURY)
                                .via(negativeBurnNFT)
                                .logged()))
                .then(
                        childRecordsCheck(
                                negativeBurnFungible,
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_BURN_AMOUNT)),
                        childRecordsCheck(negativeBurnNFT, SUCCESS, recordWith().status(SUCCESS)),
                        getAccountBalance(TOKEN_TREASURY)
                                .hasTokenBalance(TOKEN, 1_000)
                                .hasTokenBalance(NFT, 0));
    }

    @HapiTest
    final HapiSpec burnAboveMaxLongAmount() {
        final AtomicReference<Address> tokenAddress = new AtomicReference<>();
        final AtomicReference<Address> nftAddress = new AtomicReference<>();
        return defaultHapiSpec("burnAboveMaxLongAmount")
                .given(
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1_000)
                                .exposingAddressTo(tokenAddress::set),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0L)
                                .supplyKey(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .exposingAddressTo(nftAddress::set),
                        mintToken(NFT, List.of(copyFromUtf8(FIRST), copyFromUtf8(SECOND))))
                .when(
                        sourcing(() -> contractCall(CONTRACT, "burnFungibleAboveMaxLong", tokenAddress.get())
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .via("asd")
                                .logged()),
                        newKeyNamed(CONTRACT_KEY).shape(KeyShape.CONTRACT.signedWith(CONTRACT)),
                        tokenUpdate(NFT).supplyKey(CONTRACT_KEY),
                        sourcing(() -> contractCall(
                                        CONTRACT, "burnNFTAboveMaxLong", nftAddress.get(), new long[] {1L, 2L})
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .payingWith(TOKEN_TREASURY)
                                .signingWith(TOKEN_TREASURY)
                                .via("aboveMaxLong")
                                .logged()))
                .then(
                        getTxnRecord("asd").andAllChildRecords().logged(),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 1_000),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(NFT, 2));
    }

    @HapiTest
    final HapiSpec burnWithZeroAddress() {
        return defaultHapiSpec("burnWithZeroAddress")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(
                        sourcing(() -> contractCall(CONTRACT, "burnFungibleZeroAddress")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .via("zeroAddress")
                                .logged()),
                        sourcing(() -> contractCall(CONTRACT, "burnNFTZeroAddress", new long[] {1L, 2L})
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .via("zeroAddressNFT")
                                .logged()))
                .then(
                        childRecordsCheck(
                                "zeroAddress",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)),
                        childRecordsCheck(
                                "zeroAddressNFT",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)));
    }

    @HapiTest
    final HapiSpec burnWithInvalidAddress() {
        return defaultHapiSpec("burnWithInvalidAddress")
                .given(uploadInitCode(CONTRACT), contractCreate(CONTRACT))
                .when(
                        sourcing(() -> contractCall(CONTRACT, "burnFungibleInvalidAddress")
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .via("invalidAddress")
                                .logged()),
                        sourcing(() -> contractCall(CONTRACT, "burnNFTInvalidAddress", new long[] {1L, 2L})
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .via("invalidAddressNFT")
                                .logged()))
                .then(
                        childRecordsCheck(
                                "invalidAddress",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)),
                        childRecordsCheck(
                                "invalidAddressNFT",
                                CONTRACT_REVERT_EXECUTED,
                                recordWith().status(INVALID_TOKEN_ID)));
    }

    @HapiTest
    final HapiSpec burnWithInvalidSerials() {
        final AtomicReference<Address> tokenAddress = new AtomicReference<>();
        final AtomicReference<Address> nftAddress = new AtomicReference<>();
        return defaultHapiSpec("burnWithInvalidSerials")
                .given(
                        uploadInitCode(CONTRACT),
                        contractCreate(CONTRACT),
                        cryptoCreate(TOKEN_TREASURY),
                        tokenCreate(TOKEN)
                                .tokenType(FUNGIBLE_COMMON)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(1_000)
                                .exposingAddressTo(tokenAddress::set),
                        tokenCreate(NFT)
                                .tokenType(NON_FUNGIBLE_UNIQUE)
                                .treasury(TOKEN_TREASURY)
                                .initialSupply(0L)
                                .supplyKey(TOKEN_TREASURY)
                                .adminKey(TOKEN_TREASURY)
                                .exposingAddressTo(nftAddress::set),
                        mintToken(NFT, List.of(copyFromUtf8(FIRST), copyFromUtf8(SECOND))))
                .when(
                        sourcing(() -> contractCall(CONTRACT, "burnFungibleWithInvalidSerials", tokenAddress.get())
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .logged()),
                        sourcing(() -> contractCall(CONTRACT, "burnNFTWithInvalidSerials", nftAddress.get())
                                .gas(GAS_TO_OFFER)
                                .hasKnownStatus(CONTRACT_REVERT_EXECUTED)
                                .payingWith(TOKEN_TREASURY)
                                .signingWith(TOKEN_TREASURY)
                                .logged()))
                .then(
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(TOKEN, 1_000),
                        getAccountBalance(TOKEN_TREASURY).hasTokenBalance(NFT, 2));
    }
}
