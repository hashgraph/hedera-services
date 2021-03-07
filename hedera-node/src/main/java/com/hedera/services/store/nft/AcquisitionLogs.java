package com.hedera.services.store.nft;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.context.SingletonContextsManager;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleNftOwnership;
import com.hederahashgraph.api.proto.java.NftID;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class AcquisitionLogs {
	private static final Logger log = LogManager.getLogger(AcquisitionLogs.class);

	private final Supplier<Instant> signedStateTime;
	private final Supplier<StateView> signedStateView;

	private final Set<Pair<NftID, ByteString>> seenNfts = new HashSet<>();

	private final ScheduledExecutorService gcService = Executors.newScheduledThreadPool(1);
	private final Map<MerkleEntityId, AcquisitionLog> acquisitionLogs = new ConcurrentHashMap<>();

	public AcquisitionLogs(
			long gcPeriodSecs,
			Supplier<Instant> signedStateTime,
			Supplier<StateView> signedStateView
	) {
		this.signedStateTime = signedStateTime;
		this.signedStateView = signedStateView;

		gcService.scheduleAtFixedRate(this::clean, gcPeriodSecs, gcPeriodSecs, TimeUnit.SECONDS);

		Runtime.getRuntime().addShutdownHook(new Thread(gcService::shutdown));
	}

	public void logAcquisition(
			MerkleEntityId from,
			MerkleEntityId to,
			Pair<NftID, ByteString> nft,
			Instant consensusTime
	) {
		var aEvent = Triple.of(nft.getLeft(), nft.getRight(), consensusTime);

		var fromLog = acquisitionLogs.get(from);
		/* May be minting, in which case the NFT comes ab initio from the zero account. */
		if (fromLog != null) {
			fromLog.markUpdated(consensusTime);
		}

		var toLog = acquisitionLogs.computeIfAbsent(to, ignore -> new AcquisitionLog());
		toLog.getAcquisitionEvents().add(aEvent);
		toLog.markUpdated(consensusTime);

		if (SingletonContextsManager.CONTEXTS.lookup(0L).acquisitionLogs() == this) {
			log.info("Logged acquisition event {} for {}", aEvent, to.toAbbrevString());
		}
	}

	private void clean() {
		var ss = signedStateView.get();
		var ssTime = signedStateTime.get();
		if (SingletonContextsManager.CONTEXTS.lookup(0L).acquisitionLogs() == this) {
			log.info("Now cleaning {} account acquisition logs at signed state time {}", ss.accounts().size(), ssTime);
		}
		for (var entry : ss.accounts().entrySet()) {
			var id = entry.getKey();
			var account = entry.getValue();
			if (account.isDeleted()) {
				acquisitionLogs.remove(id);
			} else {
				cleanAccount(id, ssTime, ss.nftOwnerships());
			}
		}
	}

	private void cleanAccount(
			MerkleEntityId id,
			Instant ssTime,
			FCMap<MerkleNftOwnership, MerkleEntityId> nftOwnerships
	) {
		if (ssTime == null) {
			return;
		}

		var aLog = acquisitionLogs.get(id);
		if (aLog == null) {
			return;
		}

		if (SingletonContextsManager.CONTEXTS.lookup(0L).acquisitionLogs() == this) {
			log.info("  - {} log is {}", id.toAbbrevString(), aLog);
		}
		if (!aLog.getLastUpdated().get().isAfter(aLog.getLastCleaned().get())) {
			return;
		}

		seenNfts.clear();
		if (SingletonContextsManager.CONTEXTS.lookup(0L).acquisitionLogs() == this) {
			log.info("  - Cleaning {} acquisition events for {}", aLog.acquisitionEvents.size(), id.toAbbrevString());
		}
		for (var iter = aLog.getAcquisitionEvents().iterator(); iter.hasNext(); ) {
			var event = iter.next();
			if (SingletonContextsManager.CONTEXTS.lookup(0L).acquisitionLogs() == this) {
				var nft = Pair.of(event.getLeft(), event.getMiddle());
				log.info("    * {}? ({} now owns [{}, {}])",
						event,
						nftOwnerships.get(MerkleNftOwnership.fromPair(nft)).toAbbrevString(),
						nft.getLeft().getNftNum(), nft.getRight().toStringUtf8());
			}
			if (event.getRight().isAfter(ssTime)) {
				break;
			}

			var nft = Pair.of(event.getLeft(), event.getMiddle());
			if (seenNfts.contains(nft)) {
				iter.remove();
				if (SingletonContextsManager.CONTEXTS.lookup(0L).acquisitionLogs() == this) {
					log.info("Cleaning redundant event {} from {}'s acquisition log", event, id.toAbbrevString());
				}
			} else {
				var owner = nftOwnerships.get(MerkleNftOwnership.fromPair(nft));
				if (owner != null) {
					seenNfts.add(nft);
					if (!id.equals(owner)) {
						if (SingletonContextsManager.CONTEXTS.lookup(0L).acquisitionLogs() == this) {
							log.info("Cleaning outdated event {} from {}'s acquisition log", event, id.toAbbrevString());
						}
						iter.remove();
					}
				}
			}
		}

		aLog.markCleaned(ssTime);
	}

	private static class AcquisitionLog {
		final AtomicReference<Instant> lastCleaned = new AtomicReference<>(Instant.ofEpochSecond(0L));
		final AtomicReference<Instant> lastUpdated = new AtomicReference<>(Instant.ofEpochSecond(0L));
		final Queue<Triple<NftID, ByteString, Instant>> acquisitionEvents = new ConcurrentLinkedQueue<>();

		Queue<Triple<NftID, ByteString, Instant>> getAcquisitionEvents() {
			return acquisitionEvents;
		}

		AtomicReference<Instant> getLastCleaned() {
			return lastCleaned;
		}

		AtomicReference<Instant> getLastUpdated() {
			return lastUpdated;
		}

		public void markUpdated(Instant at)	{
			lastUpdated.set(at);
		}

		public void markCleaned(Instant at)	{
			lastCleaned.set(at);
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this)
					.add("lastCleaned", lastCleaned.get())
					.add("lastUpdated", lastUpdated.get())
					.add("# of events", acquisitionEvents.size())
					.toString();
		}
	}
}
