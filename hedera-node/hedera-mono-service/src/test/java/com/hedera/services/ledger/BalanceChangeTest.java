/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger;

import static com.hedera.services.ledger.BalanceChange.NO_TOKEN_FOR_HBAR_ADJUST;
import static com.hedera.services.ledger.BalanceChange.changingNftOwnership;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAccountWithAlias;
import static com.hedera.test.utils.IdUtils.asAliasAccount;
import static com.hedera.test.utils.IdUtils.nftXfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import org.junit.jupiter.api.Test;

class BalanceChangeTest {
    private final Id t = new Id(1, 2, 3);
    private final long delta = -1_234L;
    private final long serialNo = 1234L;
    private final AccountID a = asAccount("1.2.3");
    private final AccountID b = asAccount("2.3.4");
    private final AccountID payer = asAccount("0.0.1234");

    @Test
    void objectContractSanityChecks() {
        // given:
        final var hbarChange = IdUtils.hbarChange(a, delta);
        final var tokenChange = IdUtils.tokenChange(t, a, delta);
        final var nftChange =
                changingNftOwnership(t, t.asGrpcToken(), nftXfer(a, b, serialNo), payer);
        // and:
        final var hbarRepr =
                "BalanceChange{token=ℏ, account=1.2.3, alias=, units=-1234, expectedDecimals=-1}";
        final var tokenRepr =
                "BalanceChange{token=1.2.3, account=1.2.3, alias=, units=-1234,"
                        + " expectedDecimals=-1}";
        final var nftRepr =
                "BalanceChange{nft=1.2.3, serialNo=1234, from=1.2.3, to=2.3.4, counterPartyAlias=}";

        // expect:
        assertFalse(nftChange.isApprovedAllowance());
        assertNotEquals(hbarChange, tokenChange);
        assertNotEquals(hbarChange.hashCode(), tokenChange.hashCode());
        // and:
        assertEquals(hbarRepr, hbarChange.toString());
        assertEquals(tokenRepr, tokenChange.toString());
        assertEquals(nftRepr, nftChange.toString());
        // and:
        assertSame(a, hbarChange.accountId());
        assertEquals(delta, hbarChange.getAggregatedUnits());
        assertEquals(t.asGrpcToken(), tokenChange.tokenId());
    }

    @Test
    void recognizesFungibleTypes() {
        // given:
        final var hbarChange = IdUtils.hbarChange(a, delta);
        final var tokenChange = IdUtils.tokenChange(t, a, delta);

        assertTrue(hbarChange.isForHbar());
        assertFalse(tokenChange.isForHbar());
        assertFalse(hbarChange.isForToken());
        // and:
        assertFalse(hbarChange.isForNft());
        assertFalse(tokenChange.isForNft());
        assertTrue(tokenChange.isForToken());
    }

    @Test
    void noTokenForHbarAdjust() {
        final var hbarChange = IdUtils.hbarChange(a, delta);
        assertSame(NO_TOKEN_FOR_HBAR_ADJUST, hbarChange.tokenId());
    }

    @Test
    void hbarAdjust() {
        final var hbarAdjust = BalanceChange.hbarAdjust(Id.DEFAULT, 10);
        assertEquals(Id.DEFAULT, hbarAdjust.getAccount());
        assertTrue(hbarAdjust.isForHbar());
        assertFalse(hbarAdjust.isForToken());
        assertEquals(0, hbarAdjust.getAllowanceUnits());
        assertEquals(10, hbarAdjust.getAggregatedUnits());
        assertEquals(10, hbarAdjust.originalUnits());
        hbarAdjust.aggregateUnits(10);
        assertEquals(20, hbarAdjust.getAggregatedUnits());
        assertEquals(10, hbarAdjust.originalUnits());
    }

    @Test
    void objectContractWorks() {
        final var adjust = BalanceChange.hbarAdjust(Id.DEFAULT, 10);
        adjust.setCodeForInsufficientBalance(INSUFFICIENT_PAYER_BALANCE);
        assertEquals(INSUFFICIENT_PAYER_BALANCE, adjust.codeForInsufficientBalance());
        adjust.setExemptFromCustomFees(false);
        assertFalse(adjust.isExemptFromCustomFees());
    }

    @Test
    void tokenAdjust() {
        final var tokenAdjust =
                BalanceChange.tokenAdjust(
                        IdUtils.asModelId("1.2.3"), IdUtils.asModelId("3.2.1"), 10, null, true);
        assertEquals(10, tokenAdjust.getAggregatedUnits());
        assertEquals(0, tokenAdjust.getAllowanceUnits());
        assertEquals(new Id(1, 2, 3), tokenAdjust.getAccount());
        assertEquals(new Id(3, 2, 1), tokenAdjust.getToken());
    }

    @Test
    void ownershipChangeFactoryWorks() {
        // setup:
        final var xfer =
                NftTransfer.newBuilder()
                        .setSenderAccountID(a)
                        .setReceiverAccountID(b)
                        .setSerialNumber(serialNo)
                        .setIsApproval(true)
                        .build();

        // given:
        final var nftChange = changingNftOwnership(t, t.asGrpcToken(), xfer, payer);

        // expect:
        assertEquals(a, nftChange.accountId());
        assertEquals(b, nftChange.counterPartyAccountId());
        assertEquals(t.asGrpcToken(), nftChange.tokenId());
        assertEquals(serialNo, nftChange.serialNo());
        // and:
        assertTrue(nftChange.isForNft());
        assertTrue(nftChange.isForToken());
        assertFalse(nftChange.hasNonEmptyCounterPartyAlias());
        assertTrue(nftChange.isApprovedAllowance());
        assertEquals(new NftId(t.shard(), t.realm(), t.num(), serialNo), nftChange.nftId());
    }

    @Test
    void checksCounterPartyAliasExists() {
        var xfer =
                NftTransfer.newBuilder()
                        .setSenderAccountID(a)
                        .setReceiverAccountID(asAliasAccount(ByteString.copyFromUtf8("somebody")))
                        .setSerialNumber(serialNo)
                        .setIsApproval(true)
                        .build();

        var nftChange = changingNftOwnership(t, t.asGrpcToken(), xfer, payer);
        assertTrue(nftChange.isForNft());
        assertTrue(nftChange.isForToken());
        assertTrue(nftChange.hasNonEmptyCounterPartyAlias());

        xfer =
                NftTransfer.newBuilder()
                        .setSenderAccountID(asAliasAccount(ByteString.copyFromUtf8("somebody")))
                        .setReceiverAccountID(a)
                        .setSerialNumber(serialNo)
                        .setIsApproval(true)
                        .build();

        nftChange = changingNftOwnership(t, t.asGrpcToken(), xfer, payer);
        assertTrue(nftChange.isForNft());
        assertTrue(nftChange.isForToken());
        assertFalse(nftChange.hasNonEmptyCounterPartyAlias());
    }

    @Test
    void canReplaceAlias() {
        final var created = IdUtils.asAccount("0.0.1234");
        final var anAlias = ByteString.copyFromUtf8("abcdefg");
        final var subject =
                BalanceChange.changingHbar(
                        AccountAmount.newBuilder()
                                .setAmount(1234)
                                .setAccountID(AccountID.newBuilder().setAlias(anAlias))
                                .build(),
                        payer);

        subject.replaceNonEmptyAliasWith(EntityNum.fromAccountId(created));
        assertFalse(subject.hasAlias());
        assertEquals(created, subject.accountId());
        assertFalse(subject.hasNonEmptyCounterPartyAlias());
    }

    @Test
    void canReplaceCounterpartyAlias() {
        final var created = IdUtils.asAccount("0.0.1234");
        final var anAlias = ByteString.copyFromUtf8("abcdefg");
        final var xfer =
                NftTransfer.newBuilder()
                        .setSenderAccountID(asAccount("0.0.2000"))
                        .setReceiverAccountID(asAccountWithAlias(String.valueOf(anAlias)))
                        .setSerialNumber(serialNo)
                        .setIsApproval(true)
                        .build();
        final var subject = changingNftOwnership(t, t.asGrpcToken(), xfer, payer);
        assertTrue(subject.hasNonEmptyCounterPartyAlias());
        assertFalse(subject.counterPartyAlias().isEmpty());

        subject.replaceNonEmptyAliasWith(EntityNum.fromAccountId(created));
        assertFalse(subject.hasNonEmptyCounterPartyAlias());
        assertEquals(created, subject.counterPartyAccountId());
        assertNotEquals(0, subject.counterPartyAccountId().getAccountNum());
        assertTrue(subject.isForNft());
    }

    @Test
    void settersAndGettersOfDecimalsWorks() {
        final var created = new Id(1, 2, 3);
        final var token = new Id(4, 5, 6);
        final var subject =
                BalanceChange.changingFtUnits(
                        token,
                        token.asGrpcToken(),
                        AccountAmount.newBuilder()
                                .setAmount(1234)
                                .setAccountID(created.asGrpcAccount())
                                .build(),
                        payer);
        assertEquals(-1, subject.getExpectedDecimals());
        assertFalse(subject.hasExpectedDecimals());

        subject.setExpectedDecimals(2);

        assertEquals(2, subject.getExpectedDecimals());
        assertTrue(subject.hasExpectedDecimals());
    }
}
