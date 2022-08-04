package com.hedera.services.state.migration;

import static com.hedera.services.state.merkle.MerkleUniqueToken.TREASURY_OWNER_CODE;
import static com.hedera.services.state.migration.ReleaseTwentySevenMigration.fixNftCounts;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.merkle.map.MerkleMap;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NftRationalizationTest {
  private MerkleMap<EntityNum, MerkleToken> tokens = new MerkleMap<>();
  private MerkleMap<EntityNum, MerkleAccount> accounts = new MerkleMap<>();
  private MerkleMap<EntityNumPair, MerkleUniqueToken> nfts = new MerkleMap<>();
  private MerkleMap<EntityNumPair, MerkleTokenRelStatus> tokenRels = new MerkleMap<>();

  @BeforeEach
  void setUp() throws ConstructableRegistryException {
    ConstructableRegistry.registerConstructable(
        new ClassConstructorPair(MerkleAccount.class, MerkleAccount::new));
  }

  @Test
  void fixesNftCounts() {
    final var aNonfungibleToken = 777L;
    final var bNonfungibleToken = 888L;
    final var cNonfungibleToken = 999L;
    final var missingNonfungibleToken = 111L;
    final var inflatedOwner = 1234L;
    final var typicalOwner = 2345L;
    final var missingTreasury = 3456L;

    addToken(aNonfungibleToken, inflatedOwner);
    addToken(bNonfungibleToken, typicalOwner);
    addToken(cNonfungibleToken, missingTreasury);

    /* There shouldn't ever be a missing token, but just to test handling that case */
    addNft(missingNonfungibleToken, 1, missingTreasury);

    addNft(aNonfungibleToken, 1, typicalOwner);
    addNft(bNonfungibleToken, 1, TREASURY_OWNER_CODE);
    addOwner(typicalOwner, 2);
    addRel(typicalOwner, aNonfungibleToken, 1);
    addRel(typicalOwner, bNonfungibleToken, 1);

    addNft(aNonfungibleToken, 2, inflatedOwner);
    addNft(aNonfungibleToken, 3, TREASURY_OWNER_CODE);
    addNft(bNonfungibleToken, 2, inflatedOwner);
    addNft(cNonfungibleToken, 1, inflatedOwner);
    /* There shouldn't ever be a missing treasury account, but just to test handling that case */
    addNft(cNonfungibleToken, 2, missingTreasury);
    addOwner(inflatedOwner, 7);
    addRel(inflatedOwner, aNonfungibleToken, 4);
    addRel(inflatedOwner, bNonfungibleToken, 2);
    addRel(inflatedOwner, cNonfungibleToken, 1);

    final var priorTypicalLeaf = accounts.get(EntityNum.fromLong(typicalOwner));
    final var priorTypicalALeaf = tokenRels.get(EntityNumPair.fromLongs(typicalOwner, aNonfungibleToken));
    final var priorTypicalBLeaf = tokenRels.get(EntityNumPair.fromLongs(typicalOwner, bNonfungibleToken));
    final var priorInflatedCLeaf = tokenRels.get(EntityNumPair.fromLongs(inflatedOwner, cNonfungibleToken));
    fixNftCounts(tokens, accounts, nfts, tokenRels);

    /* Correct leaves shouldn't be touched */
    assertSame(priorTypicalLeaf, accounts.get(EntityNum.fromLong(typicalOwner)));
    assertSame(priorTypicalALeaf, tokenRels.get(EntityNumPair.fromLongs(typicalOwner, aNonfungibleToken)));
    assertSame(priorTypicalBLeaf, tokenRels.get(EntityNumPair.fromLongs(typicalOwner, bNonfungibleToken)));
    assertSame(priorInflatedCLeaf, tokenRels.get(EntityNumPair.fromLongs(inflatedOwner, cNonfungibleToken)));

    /* Inflated counts should be fixed via g4m */
    assertEquals(4, accounts.get(EntityNum.fromLong(inflatedOwner)).getNftsOwned());
    assertEquals(2, tokenRels.get(EntityNumPair.fromLongs(inflatedOwner, aNonfungibleToken)).getBalance());
    assertEquals(1, tokenRels.get(EntityNumPair.fromLongs(inflatedOwner, bNonfungibleToken)).getBalance());
  }

  private void addToken(long num, long treasury) {
    final var token = new MerkleToken();
    token.setTreasury(new EntityId(0, 0, treasury));
    tokens.put(EntityNum.fromLong(num), token);
  }

  private void addRel(long account, long token, long balance) {
    final var rel = new MerkleTokenRelStatus(balance, false, true, false);
    tokenRels.put(EntityNumPair.fromLongs(account, token), rel);
  }

  private void addOwner(long num, long totalNfts) {
    final var account = new MerkleAccount();
    account.setNftsOwned(totalNfts);
    accounts.put(EntityNum.fromLong(num), account);
  }

  private void addNft(long tokenType, long serialNo, long owner) {
    nfts.put(EntityNumPair.fromLongs(tokenType, serialNo), nftOwnedBy(owner));
  }

  private MerkleUniqueToken nftOwnedBy(long num) {
    return new MerkleUniqueToken(
        BitPackUtils.codeFromNum(num),
        "Doesn't matter".getBytes(StandardCharsets.UTF_8),
        BitPackUtils.packedTime(1_234_567L, 890),
        0);
  }
}
