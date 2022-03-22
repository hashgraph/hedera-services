package com.hedera.services.state.migration;

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.virtual.UniqueTokenKey;
import com.hedera.services.state.virtual.UniqueTokenValue;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.utils.EntityNumPair;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

import static com.hedera.services.state.migration.StateVersions.RELEASE_0250_VERSION;
import static com.hedera.services.utils.MiscUtils.forEach;

public class ReleaseTwentyFiveMigration {
	private static final Logger log = LogManager.getLogger(ReleaseTwentyTwoMigration.class);

	/**
	 * Migrate tokens from MerkleMap data structure to VirtualMap data structure.
	 *
	 * @param initializingState the ServicesState containing the MerkleMap to migrate.
	 * @param deserializedVersion the version number of the state to migrate.
	 */
	public static void migrateFromUniqueTokenMerkleMap(
			final ServicesState initializingState,
			final int deserializedVersion) {
		log.info("Migrating state from version {} to {}", deserializedVersion, RELEASE_0250_VERSION);

		final var virtualMapFactory = new VirtualMapFactory(JasperDbBuilder::new);
		final MerkleMap<EntityNumPair, MerkleUniqueToken> legacyUniqueTokens = initializingState.getChild(
				StateChildIndices.UNIQUE_TOKENS);
		final VirtualMap<UniqueTokenKey, UniqueTokenValue> vmUniqueTokens =
				virtualMapFactory.newVirtualizedUniqueTokenStorage();
		AtomicInteger count = new AtomicInteger();

		forEach(legacyUniqueTokens, (entityNumPair, legacyToken) -> {
			var numSerialPair = entityNumPair.asTokenNumAndSerialPair();
			var newTokenKey = new UniqueTokenKey(numSerialPair.getLeft(), numSerialPair.getRight());
			var newTokenValue = new UniqueTokenValue(
					legacyToken.getOwner().num(),
					legacyToken.getCreationTime(),
					legacyToken.getMetadata());
			vmUniqueTokens.put(newTokenKey, newTokenValue);
			count.incrementAndGet();
		});

		initializingState.setChild(StateChildIndices.UNIQUE_TOKENS, vmUniqueTokens);
		log.info("Migrated {} unique tokens", count.get());
	}

	private ReleaseTwentyFiveMigration() { /* disallow construction */ }
}
