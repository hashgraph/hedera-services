package com.hedera.services.state.migration;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.ServicesState;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.state.virtual.IterableStorageUtils;
import com.hedera.services.store.contracts.SizeLimitedStorage;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.services.stream.RecordStreamManager;
import com.hedera.services.stream.RecordStreamObject;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.RandomExtended;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.hedera.services.state.migration.StateChildIndices.CONTRACT_STORAGE;
import static com.hedera.services.state.submerkle.RichInstant.MISSING_INSTANT;
import static com.hedera.services.state.submerkle.TxnId.USER_TRANSACTION_NONCE;
import static com.hedera.services.txns.crypto.AutoCreationLogic.THREE_MONTHS_IN_SECONDS;
import static com.hedera.services.utils.MiscUtils.synthFromBody;

public class ReleaseTwentySixMigration {
	private static final Logger log = LogManager.getLogger(ReleaseTwentySixMigration.class);

	public static final int THREAD_COUNT = 32;
	public static final int INSERTIONS_PER_COPY = 100;
	public static final int SEVEN_DAYS_IN_SECONDS = 604800;

	public static Map<EntityNum, ContractKey> makeStorageIterable(
			final ServicesState initializingState,
			final MigratorFactory migratorFactory,
			final MigrationUtility migrationUtility,
			final VirtualMap<ContractKey, IterableContractValue> iterableContractStorage
	) {
		final var contracts = initializingState.accounts();
		final VirtualMap<ContractKey, ContractValue> contractStorage = initializingState.getChild(CONTRACT_STORAGE);
		final var migrator = migratorFactory.from(
				INSERTIONS_PER_COPY, contracts, IterableStorageUtils::upsertMapping, iterableContractStorage);
		try {
			log.info("Migrating contract storage into iterable VirtualMap with {} threads", THREAD_COUNT);
			final var watch = StopWatch.createStarted();
			migrationUtility.extractVirtualMapData(contractStorage, migrator, THREAD_COUNT);
			log.info("Done in {}ms", watch.getTime(TimeUnit.MILLISECONDS));
		} catch (InterruptedException e) {
			log.error("Interrupted while making contract storage iterable", e);
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
		migrator.finish();
		initializingState.setChild(CONTRACT_STORAGE, migrator.getMigratedStorage());
		return migrator.getContractKeys();
	}

	public static void grantFreeAutoRenew(
			final ServicesState initializingState,
			final Set<EntityNum> contractKeys,
			final Instant upgradeTime,
			final RecordStreamManager streamManager) throws NoSuchAlgorithmException {
		SyntheticTxnFactory factory = new SyntheticTxnFactory();
		final var contracts = initializingState.accounts();

		log.info("Granting free auto renewal for all smart contracts by ~90 days");

//		final var rand = SecureRandom.getInstance("SHA1PRNG"); // pseudo random on hash ??
		final var rand = new RandomExtended();
		for (var key : contractKeys) {
			final var newExpiry = setNewExpiry(upgradeTime, contracts, key, rand);
			streamRecord(factory, key, newExpiry, upgradeTime, streamManager);
		}
	}

	private static long setNewExpiry(
			final Instant upgradeTime,
			final MerkleMap<EntityNum, MerkleAccount> contracts,
			final EntityNum key,
			final RandomExtended rand) {
		final var account = contracts.getForModify(key);
		final var currentExpiry = account.getExpiry();
		final var newExpiry = Math.max(currentExpiry,
				upgradeTime.getEpochSecond()
						+ THREE_MONTHS_IN_SECONDS
						+ rand.nextLong(0, SEVEN_DAYS_IN_SECONDS));
		account.setExpiry(newExpiry);
		return newExpiry;
	}

	private static void streamRecord(
			final SyntheticTxnFactory factory,
			final EntityNum contractNum,
			final long newExpiry,
			final Instant upgradeTime,
			final RecordStreamManager streamManager) {
		final var synthBody = factory.synthContractAutoRenew(contractNum, newExpiry);
		final var at = RichInstant.fromJava(upgradeTime);
		final var id = contractNum.toEntityId();
		final var receipt = new TxnReceipt();
		receipt.setAccountId(id);

		final var txnId = new TxnId(id, MISSING_INSTANT, false, USER_TRANSACTION_NONCE);
		final var memo =
				"Contract " + contractNum.toIdString() + " was automatically renewed. New expiration time: " + newExpiry + ".";
		final var expiringRecord = ExpirableTxnRecord.newBuilder()
				.setTxnId(txnId)
				.setMemo(memo)
				.setReceipt(receipt)
				.setConsensusTime(at)
				.build();
		final var rso = new RecordStreamObject(expiringRecord, synthFromBody(synthBody.build()), upgradeTime);
		streamManager.addRecordStreamObject(rso);
	}

	@FunctionalInterface
	public interface MigratorFactory {
		KvPairIterationMigrator from(
				int insertionsPerCopy,
				MerkleMap<EntityNum, MerkleAccount> contracts,
				SizeLimitedStorage.IterableStorageUpserter storageUpserter,
				VirtualMap<ContractKey, IterableContractValue> iterableContractStorage);
	}

	@FunctionalInterface
	public interface MigrationUtility {
		void extractVirtualMapData(
				VirtualMap<ContractKey, ContractValue> source,
				InterruptableConsumer<Pair<ContractKey, ContractValue>> handler,
				int threadCount) throws InterruptedException;
	}

	private ReleaseTwentySixMigration() {
		throw new UnsupportedOperationException("Utility class");
	}
}
