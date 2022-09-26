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
package com.hedera.services.context;

import static com.hedera.services.state.enums.TokenType.FUNGIBLE_COMMON;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.state.submerkle.FcTokenAllowance;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SideEffectsTrackerTest {
    private SideEffectsTracker subject;

    @BeforeEach
    void setUp() {
        subject = new SideEffectsTracker();
    }

    @Test
    void tracksAndResetsNewTokenIdAsExpected() {
        final var changedToken = new Token(Id.fromGrpcToken(aToken));
        changedToken.setNew(true);

        subject.trackTokenChanges(changedToken);

        assertTrue(subject.hasTrackedNewTokenId());
        assertEquals(aToken, subject.getTrackedNewTokenId());

        subject.reset();
        assertFalse(subject.hasTrackedNewTokenId());
    }

    @Test
    void tracksNewContract() {
        final var id = IdUtils.asContract("0.0.123");
        final var addr = Address.BLAKE2B_F_COMPRESSION;

        subject.trackNewContract(id, addr);

        assertEquals(ByteString.copyFrom(addr.toArrayUnsafe()), subject.getNewEntityAlias());
        assertTrue(subject.hasTrackedContractCreation());
        assertEquals(id, subject.getTrackedNewContractId());

        subject.reset();
        assertFalse(subject.hasTrackedContractCreation());
    }

    @Test
    void tracksAndResetsTokenSupplyAsExpected() {
        final var newSupply = 1_234L;
        final var changedToken = new Token(Id.fromGrpcToken(aToken));
        changedToken.setTotalSupply(newSupply);

        subject.trackTokenChanges(changedToken);

        assertTrue(subject.hasTrackedTokenSupply());
        assertEquals(newSupply, subject.getTrackedTokenSupply());

        subject.reset();
        assertFalse(subject.hasTrackedTokenSupply());
    }

    @Test
    void tracksAndResetsNftMintsAsExpected() {
        final var changedToken = new Token(Id.fromGrpcToken(aToken));
        changedToken
                .mintedUniqueTokens()
                .add(new UniqueToken(Id.fromGrpcToken(cSN1.tokenId()), cSN1.serialNo()));

        subject.trackTokenChanges(changedToken);

        assertTrue(subject.hasTrackedNftMints());
        assertEquals(List.of(cSN1.serialNo()), subject.getTrackedNftMints());

        subject.reset();

        assertFalse(subject.hasTrackedNftMints());
        assertTrue(subject.getTrackedNftMints().isEmpty());
    }

    @Test
    void usesSingletonForNoAutoAssociations() {
        assertSame(Collections.emptyList(), subject.getTrackedAutoAssociations());
    }

    @Test
    void tracksAndResetsAutoAssociationsAsExpected() {
        final var expected =
                List.of(
                        new FcTokenAssociation(aToken.getTokenNum(), aAccount.getAccountNum()),
                        new FcTokenAssociation(bToken.getTokenNum(), bAccount.getAccountNum()));

        subject.trackAutoAssociation(aToken, aAccount);
        subject.trackAutoAssociation(bToken, bAccount);

        assertEquals(expected, subject.getTrackedAutoAssociations());
        assertNotSame(subject.getInternalAutoAssociations(), subject.getTrackedAutoAssociations());

        subject.reset();

        assertTrue(subject.getTrackedAutoAssociations().isEmpty());
    }

    @Test
    void tracksAndResetsNewAccountIdAsExpected() {
        final var createdAutoAccount =
                AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(20L).build();
        final var alias = ByteString.copyFromUtf8("abcdefg");

        subject.trackAutoCreation(createdAutoAccount, alias);

        assertTrue(subject.hasTrackedAutoCreation());
        assertFalse(subject.hasTrackedContractCreation());
        assertEquals(createdAutoAccount, subject.getTrackedAutoCreatedAccountId());
        assertEquals(alias, subject.getNewEntityAlias());

        subject.reset();
        assertFalse(subject.hasTrackedAutoCreation());
    }

    @Test
    void canClearJustTokenChanges() {
        final var newSupply = 1_234L;
        final var changedToken = new Token(Id.fromGrpcToken(aToken));
        changedToken.setNew(true);
        changedToken.setTotalSupply(newSupply);
        changedToken
                .mintedUniqueTokens()
                .add(new UniqueToken(Id.fromGrpcToken(cSN1.tokenId()), cSN1.serialNo()));

        subject.trackHbarChange(aAccount.getAccountNum(), aFirstBalanceChange);
        subject.trackTokenUnitsChange(bToken, cAccount, cOnlyBalanceChange);
        subject.trackNftOwnerChange(cSN1, aAccount, bAccount);
        subject.trackAutoAssociation(aToken, bAccount);
        subject.trackTokenChanges(changedToken);

        subject.resetTrackedTokenChanges();

        assertFalse(subject.hasTrackedNewTokenId());
        assertFalse(subject.hasTrackedTokenSupply());
        assertFalse(subject.hasTrackedNftMints());
        assertTrue(subject.getTrackedAutoAssociations().isEmpty());
        assertSame(Collections.emptyList(), subject.getNetTrackedTokenUnitAndOwnershipChanges());
        final var netChanges = subject.getNetTrackedHbarChanges();
        assertEquals(1, netChanges.getAccountNums().length);
        assertEquals(aAccount.getAccountNum(), netChanges.getAccountNums()[0]);
        assertEquals(aFirstBalanceChange, netChanges.getHbars()[0]);
    }

    @Test
    void tracksAndResetsTokenUnitAndOwnershipChangesAsExpected() {
        subject.trackNftOwnerChange(cSN1, aAccount, bAccount);
        subject.trackTokenUnitsChange(bToken, cAccount, cOnlyBalanceChange);
        subject.trackTokenUnitsChange(aToken, aAccount, aFirstBalanceChange);
        subject.trackTokenUnitsChange(aToken, bAccount, bOnlyBalanceChange);
        subject.trackTokenUnitsChange(aToken, aAccount, aSecondBalanceChange);
        subject.trackTokenUnitsChange(aToken, bAccount, -bOnlyBalanceChange);

        final var netTokenChanges = subject.getNetTrackedTokenUnitAndOwnershipChanges();

        assertEquals(3, netTokenChanges.size());

        final var netAChanges = netTokenChanges.get(0);
        assertEquals(aToken, netAChanges.getToken());
        assertEquals(1, netAChanges.getTransfersCount());
        final var aaChange = netAChanges.getTransfers(0);
        assertEquals(aAccount, aaChange.getAccountID());
        assertEquals(aFirstBalanceChange + aSecondBalanceChange, aaChange.getAmount());

        final var netBChanges = netTokenChanges.get(1);
        assertEquals(bToken, netBChanges.getToken());
        assertEquals(1, netBChanges.getTransfersCount());
        final var bcChange = netBChanges.getTransfers(0);
        assertEquals(cAccount, bcChange.getAccountID());
        assertEquals(cOnlyBalanceChange, bcChange.getAmount());

        final var netCChanges = netTokenChanges.get(2);
        assertEquals(cToken, netCChanges.getToken());
        assertEquals(1, netCChanges.getNftTransfersCount());
        final var abcChange = netCChanges.getNftTransfers(0);
        assertEquals(aAccount, abcChange.getSenderAccountID());
        assertEquals(bAccount, abcChange.getReceiverAccountID());
        assertEquals(1L, abcChange.getSerialNumber());

        subject.reset();
        assertSame(Collections.emptyList(), subject.getNetTrackedTokenUnitAndOwnershipChanges());
    }

    @Test
    void tracksAndResetsHbarChangesAsExpected() {
        subject.trackHbarChange(cAccount.getAccountNum(), cOnlyBalanceChange);
        subject.trackHbarChange(aAccount.getAccountNum(), aFirstBalanceChange);
        subject.trackHbarChange(bAccount.getAccountNum(), bOnlyBalanceChange);
        subject.trackHbarChange(aAccount.getAccountNum(), aSecondBalanceChange);
        subject.trackHbarChange(bAccount.getAccountNum(), -bOnlyBalanceChange);

        final var netChanges = subject.getNetTrackedHbarChanges();
        assertEquals(2, netChanges.getAccountNums().length);
        assertEquals(2, netChanges.getHbars().length);
        assertEquals(aAccount.getAccountNum(), netChanges.getAccountNums()[0]);
        assertEquals(aFirstBalanceChange + aSecondBalanceChange, netChanges.getHbars()[0]);
        assertEquals(cAccount.getAccountNum(), netChanges.getAccountNums()[1]);
        assertEquals(cOnlyBalanceChange, netChanges.getHbars()[1]);

        assertEquals(
                aFirstBalanceChange + aSecondBalanceChange + cOnlyBalanceChange,
                subject.getNetHbarChange());
        subject.reset();
        assertEquals(0, subject.getNetTrackedHbarChanges().getAccountNums().length);
        assertEquals(0, subject.getNetTrackedHbarChanges().getHbars().length);
        assertEquals(0, subject.getNetHbarChange());
    }

    @Test
    void zeroRewardHasNoEffect() {
        subject.trackRewardPayment(1234L, 0);
        assertEquals(0, subject.getNumRewardedAccounts());
    }

    @Test
    void tracksAndResetsRewardsPaidAsExpected() {
        final var rewardA = 1_000L;
        final var rewardB = 333L;
        final var rewardC = 1_234_567L;
        subject.trackRewardPayment(aAccount.getAccountNum(), rewardA);
        subject.trackRewardPayment(bAccount.getAccountNum(), rewardB);
        subject.trackRewardPayment(cAccount.getAccountNum(), rewardC);

        final var rewardsPaid = subject.getStakingRewardsPaid();
        assertEquals(3, rewardsPaid.getAccountNums().length);
        assertEquals(3, rewardsPaid.getHbars().length);
        assertEquals(aAccount.getAccountNum(), rewardsPaid.getAccountNums()[0]);
        assertEquals(rewardA, rewardsPaid.getHbars()[0]);
        assertEquals(bAccount.getAccountNum(), rewardsPaid.getAccountNums()[1]);
        assertEquals(rewardB, rewardsPaid.getHbars()[1]);
        assertEquals(cAccount.getAccountNum(), rewardsPaid.getAccountNums()[2]);
        assertEquals(rewardC, rewardsPaid.getHbars()[2]);

        subject.reset();
        final var emptyRewards = subject.getStakingRewardsPaid();
        assertEquals(0, emptyRewards.getAccountNums().length);
        assertEquals(0, emptyRewards.getHbars().length);
    }

    @Test
    void prioritizesExplicitOwnershipChanges() {
        final var tracker = new OwnershipTracker();
        tracker.add(
                Id.fromGrpcToken(cToken),
                new OwnershipTracker.Change(
                        Id.fromGrpcAccount(aAccount), Id.fromGrpcAccount(bAccount), 1L));
        tracker.add(
                Id.fromGrpcToken(aToken),
                new OwnershipTracker.Change(
                        Id.fromGrpcAccount(bAccount), Id.fromGrpcAccount(cAccount), 2L));

        subject.trackTokenOwnershipChanges(tracker);

        final var tokenChanges = subject.getNetTrackedTokenUnitAndOwnershipChanges();
        assertEquals(2, tokenChanges.size());
        final var aChange = tokenChanges.get(0);
        assertEquals(aToken, aChange.getToken());
        final var aTransfer = aChange.getNftTransfers(0);
        assertEquals(2L, aTransfer.getSerialNumber());
        final var cChange = tokenChanges.get(1);
        assertEquals(cToken, cChange.getToken());
        final var cTransfer = cChange.getNftTransfers(0);
        assertEquals(1L, cTransfer.getSerialNumber());
    }

    @Test
    void emptyOwnershipTrackerChangesNothing() {
        subject.trackTokenOwnershipChanges(new OwnershipTracker());

        assertFalse(subject.hasTrackedNftMints());
    }

    @Test
    void prioritizesExplicitTokenBalanceChanges() {
        final var aaRelChange =
                new TokenRelationship(
                        new Token(Id.fromGrpcToken(aToken)),
                        new Account(Id.fromGrpcAccount(aAccount)));
        aaRelChange.getToken().setType(FUNGIBLE_COMMON);
        aaRelChange.setBalance(aFirstBalanceChange);
        final var bbRelChange =
                new TokenRelationship(
                        new Token(Id.fromGrpcToken(bToken)),
                        new Account(Id.fromGrpcAccount(bAccount)));
        bbRelChange.getToken().setType(FUNGIBLE_COMMON);
        final var ccRelChange =
                new TokenRelationship(
                        new Token(Id.fromGrpcToken(cToken)),
                        new Account(Id.fromGrpcAccount(cAccount)));
        ccRelChange.setBalance(cOnlyBalanceChange);
        ccRelChange.getToken().setType(FUNGIBLE_COMMON);

        subject.trackNftOwnerChange(cSN1, aAccount, bAccount);
        subject.trackTokenBalanceChanges(List.of(ccRelChange, bbRelChange, aaRelChange));

        final var tokenChanges = subject.getNetTrackedTokenUnitAndOwnershipChanges();
        assertEquals(2, tokenChanges.size());
        final var aChange = tokenChanges.get(0);
        assertEquals(aToken, aChange.getToken());
        assertEquals(1, aChange.getTransfersCount());
        assertEquals(aFirstBalanceChange, aChange.getTransfers(0).getAmount());
        final var cChange = tokenChanges.get(1);
        assertEquals(cToken, cChange.getToken());
        assertEquals(1, cChange.getTransfersCount());
        assertEquals(cOnlyBalanceChange, cChange.getTransfers(0).getAmount());
    }

    @Test
    void purgesZeroChangesSuccessfully() {
        final var accountNums = new long[] {100L, 200L, 300L, 400L, 200L};
        final var balanceChanges = new long[] {1000L, 2000L, 0L, 300L, -100L};
        final var result = subject.purgeZeroChanges(accountNums, balanceChanges, 4);
        assertEquals(3, result);
    }

    @Test
    void includesOrderedFungibleChanges() {
        final var accountNums = new long[] {100L, 200L, 300L, 400L, 200L};
        final var balanceChanges = new long[] {1000L, 2000L, 0L, 300L, -100L};
        var result =
                subject.includeOrderedFungibleChange(accountNums, balanceChanges, 4, 300L, 100L);
        assertEquals(4, result);
        result = subject.includeOrderedFungibleChange(accountNums, balanceChanges, 4, 500L, 100L);
        assertEquals(5, result);
    }

    @Test
    void resetsTouchedNumsCorrectly() {
        subject.trackHbarChange(100L, 200L);
        subject.trackHbarChange(200L, 200L);
        assertEquals(2, subject.getNumHbarChangesSoFar());
        subject.reset();
        assertEquals(0, subject.getNumHbarChangesSoFar());
    }

    @Test
    void resetsRewardedNumsCorrectly() {
        subject.trackRewardPayment(100L, 200L);
        subject.trackRewardPayment(200L, 200L);
        subject.trackRewardPayment(800L, -400L);
        assertEquals(3, subject.getNumRewardedAccounts());
        subject.reset();
        assertEquals(0, subject.getNumRewardedAccounts());
    }

    @Test
    void tracksAndResetsPseudoRandomDataCorrectly() {
        final var num = 100;
        final var bytes = TxnUtils.randomUtf8Bytes(48);

        subject.trackRandomNumber(num);
        subject.trackRandomBytes(bytes);

        assertTrue(subject.hasTrackedRandomData());
        assertEquals(100, subject.getPseudorandomNumber());
        assertEquals(bytes, subject.getPseudorandomBytes());

        subject.reset();
        assertFalse(subject.hasTrackedRandomData());
        assertEquals(-1, subject.getPseudorandomNumber());
        assertEquals(null, subject.getPseudorandomBytes());
    }

    @Test
    void checksPseudoRandomDataIsSetCorrectly() {
        final var num = 100;
        final var bytes = TxnUtils.randomUtf8Bytes(48);

        subject.trackRandomNumber(num);
        assertTrue(subject.hasTrackedRandomData());
        subject.reset();

        subject.trackRandomBytes(bytes);
        assertTrue(subject.hasTrackedRandomData());
        subject.reset();

        subject.trackRandomBytes(new byte[0]);
        assertFalse(subject.hasTrackedRandomData());
        subject.reset();

        subject.trackRandomNumber(-1);
        assertFalse(subject.hasTrackedRandomData());
        subject.reset();
    }

    private static final long aFirstBalanceChange = 1_000L;
    private static final long aSecondBalanceChange = 9_000L;
    private static final long bOnlyBalanceChange = 7_777L;
    private static final long cOnlyBalanceChange = 8_888L;
    private static final long initialAllowance = 100L;
    private static final TokenID aToken = IdUtils.asToken("0.0.666");
    private static final TokenID bToken = IdUtils.asToken("0.0.777");
    private static final TokenID cToken = IdUtils.asToken("0.0.888");
    private static final NftId cSN1 = new NftId(0, 0, 888, 1);
    private static final AccountID owner = IdUtils.asAccount("0.0.11111");
    private static final AccountID aAccount = IdUtils.asAccount("0.0.12345");
    private static final AccountID bAccount = IdUtils.asAccount("0.0.23456");
    private static final AccountID cAccount = IdUtils.asAccount("0.0.34567");

    private static final FcTokenAllowance nftAllowance1 = FcTokenAllowance.from(true);
    private static final FcTokenAllowance nftAllowance2 = FcTokenAllowance.from(List.of(1L, 2L));
    private static final FcTokenAllowanceId fungibleAllowanceId =
            FcTokenAllowanceId.from(
                    EntityNum.fromTokenId(aToken), EntityNum.fromAccountId(aAccount));
    private static final FcTokenAllowanceId nftAllowanceId =
            FcTokenAllowanceId.from(
                    EntityNum.fromTokenId(bToken), EntityNum.fromAccountId(aAccount));
    private static final Map<EntityNum, Map<EntityNum, Long>> cryptoAllowances =
            new TreeMap<>() {
                {
                    put(
                            EntityNum.fromAccountId(owner),
                            new TreeMap<>() {
                                {
                                    put(EntityNum.fromAccountId(aAccount), initialAllowance);
                                }
                            });
                }
            };
    private static final Map<EntityNum, Long> cryptoAllowance =
            new TreeMap<>() {
                {
                    put(EntityNum.fromAccountId(aAccount), initialAllowance);
                }
            };
    private static final Map<EntityNum, Map<FcTokenAllowanceId, Long>> fungibleAllowances =
            new TreeMap<>() {
                {
                    put(
                            EntityNum.fromAccountId(owner),
                            new TreeMap<>() {
                                {
                                    put(fungibleAllowanceId, initialAllowance);
                                }
                            });
                }
            };
    private static final Map<FcTokenAllowanceId, Long> fungibleAllowance =
            new TreeMap<>() {
                {
                    put(fungibleAllowanceId, initialAllowance);
                }
            };
    private static final Map<EntityNum, Map<FcTokenAllowanceId, FcTokenAllowance>> nftAllowances =
            new TreeMap<>() {
                {
                    put(
                            EntityNum.fromAccountId(owner),
                            new TreeMap<>() {
                                {
                                    put(fungibleAllowanceId, nftAllowance1);
                                    put(nftAllowanceId, nftAllowance2);
                                }
                            });
                }
            };
    private static final Set<FcTokenAllowanceId> nftAllowance =
            new TreeSet<>() {
                {
                    add(fungibleAllowanceId);
                }
            };
    private static final Map<EntityNum, Map<FcTokenAllowanceId, FcTokenAllowance>>
            expectedNftAllowances =
                    new TreeMap<>() {
                        {
                            put(
                                    EntityNum.fromAccountId(owner),
                                    new TreeMap<>() {
                                        {
                                            put(fungibleAllowanceId, nftAllowance1);
                                            put(nftAllowanceId, nftAllowance2);
                                        }
                                    });
                        }
                    };
    private static final UniqueToken nft1 =
            new UniqueToken(Id.fromGrpcToken(bToken), 1L, Id.fromGrpcAccount(owner));
    private static final UniqueToken nft2 =
            new UniqueToken(Id.fromGrpcToken(bToken), 2L, Id.fromGrpcAccount(owner));

    static {
        nft1.setSpender(Id.fromGrpcAccount(aAccount));
        nft2.setSpender(Id.fromGrpcAccount(aAccount));
    }
}
