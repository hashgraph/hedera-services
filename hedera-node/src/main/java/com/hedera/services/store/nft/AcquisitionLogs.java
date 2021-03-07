package com.hedera.services.store.nft;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.context.SingletonContextsManager;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleNftOwnership;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.OwnedNfts;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.hedera.services.ledger.HederaLedger.NFT_ID_COMPARATOR;
import static java.util.Comparator.comparing;

public class AcquisitionLogs {
	private static final Logger log = LogManager.getLogger(AcquisitionLogs.class);

	private static final Instant THE_EPOCH = Instant.ofEpochSecond(0L);
	private static final Comparator<Map.Entry<NftID, List<ByteString>>> OWNED_NFTS_CMP =
			comparing(Map.Entry::getKey, NFT_ID_COMPARATOR);

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

	public List<OwnedNfts> currentlyOwnedBy(AccountID aId) {
		var key = MerkleEntityId.fromAccountId(aId);
		var aLog = acquisitionLogs.get(key);
		if (aLog == null) {
			return Collections.emptyList();
		}

		var ssTime = Optional.ofNullable(signedStateTime.get()).orElse(THE_EPOCH);
		var ssNftOwnerships = signedStateView.get().nftOwnerships();
		Map<NftID, List<ByteString>> owned = new HashMap<>();
		Set<Pair<NftID, ByteString>> traversedNfts = new HashSet<>();
		for (var iter = aLog.getAcquisitionEvents().iterator(); iter.hasNext(); ) {
			var event = iter.next();
			if (event.getRight().isAfter(ssTime)) {
				break;
			}
			var nftType = event.getLeft();
			var nft = Pair.of(nftType, event.getMiddle());
			if (traversedNfts.contains(nft)) {
				continue;
			}
			var owner = ssNftOwnerships.get(MerkleNftOwnership.fromPair(nft));
			if (owner != null) {
				if (key.equals(owner)) {
					owned.computeIfAbsent(nftType, ignore -> new ArrayList<>()).add(event.getMiddle());
				}
				traversedNfts.add(nft);
			}
		}

		return owned.entrySet().stream()
				.sorted(OWNED_NFTS_CMP)
				.map(entry -> {
					var builder = OwnedNfts.newBuilder();
					builder.setNftId(entry.getKey());
					entry.getValue().stream()
							.sorted(ByteString.unsignedLexicographicalComparator())
							.forEach(builder::addSerialNo);
					return builder.build();
				})
				.collect(Collectors.toList());
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
		toLog.alterEstimatedSize(+1);
		toLog.markUpdated(consensusTime);
	}

	private void clean() {
		var ss = signedStateView.get();
		var ssTime = signedStateTime.get();
		long totalEstSize = 0L;
		for (var entry : ss.accounts().entrySet()) {
			var id = entry.getKey();
			var account = entry.getValue();
			if (account.isDeleted()) {
				acquisitionLogs.remove(id);
			} else {
				totalEstSize += cleanAccount(id, ssTime, ss.nftOwnerships());
			}
		}
		log.info("Estimated {} acquisition events in-memory @ consensus time {}", totalEstSize, ssTime);
	}

	private int cleanAccount(
			MerkleEntityId id,
			Instant ssTime,
			FCMap<MerkleNftOwnership, MerkleEntityId> nftOwnerships
	) {
		if (ssTime == null) {
			return 0;
		}

		var aLog = acquisitionLogs.get(id);
		if (aLog == null) {
			return 0;
		}

		if (!aLog.getLastUpdated().get().isAfter(aLog.getLastCleaned().get())) {
			return aLog.curEstimatedSize();
		}

		seenNfts.clear();
		int numCleaned = 0;
		for (var iter = aLog.getAcquisitionEvents().iterator(); iter.hasNext(); ) {
			var event = iter.next();
			if (event.getRight().isAfter(ssTime)) {
				break;
			}

			var nft = Pair.of(event.getLeft(), event.getMiddle());
			if (seenNfts.contains(nft)) {
				/* Redundant acquisition event */
				iter.remove();
				numCleaned++;
			} else {
				var owner = nftOwnerships.get(MerkleNftOwnership.fromPair(nft));
				if (owner != null) {
					seenNfts.add(nft);
					if (!id.equals(owner)) {
						/* Misleading acquisition event */
						iter.remove();
						numCleaned++;
					}
				}
			}
		}

		aLog.markCleaned(ssTime);
		return aLog.alterEstimatedSize(-numCleaned);
	}

	private static class AcquisitionLog {
		final AtomicInteger estSize = new AtomicInteger(0);
		final AtomicReference<Instant> lastCleaned = new AtomicReference<>(THE_EPOCH);
		final AtomicReference<Instant> lastUpdated = new AtomicReference<>(THE_EPOCH);
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

		void markUpdated(Instant at)	{
			lastUpdated.set(at);
		}

		void markCleaned(Instant at)	{
			lastCleaned.set(at);
		}

		int curEstimatedSize() {
			return estSize.get();
		}

		int alterEstimatedSize(int by) {
			return estSize.addAndGet(by);
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(this)
					.add("lastCleaned", lastCleaned.get())
					.add("lastUpdated", lastUpdated.get())
					.add("estimated # of events", estSize.get())
					.toString();
		}
	}
}
