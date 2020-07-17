package com.hedera.services.state.forensics;

import com.hedera.services.ServicesMain;
import com.hedera.services.ServicesState;
import com.hedera.services.context.domain.trackers.IssEventInfo;
import com.swirlds.common.AddressBook;
import com.swirlds.common.InvalidSignedStateListener;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.SwirldState;
import com.swirlds.common.events.Event;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.io.MerkleDataOutputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.function.Function;

import static org.apache.commons.codec.binary.Hex.encodeHexString;

public class IssListener implements InvalidSignedStateListener {
	static Logger log = LogManager.getLogger(IssListener.class);

	static final String FC_DUMP_LOC_TPL = "data/saved/%s/%d/%s-round%d.fcm";
	static final String ISS_ERROR_MSG_PATTERN =
			"In round %d, node %d received a signed state from node %d with " +
					"a signature different than %s on %s [accounts :: %s | storage :: %s | topics :: %s]!";
	static final String ISS_FALLBACK_ERROR_MSG_PATTERN =
			"In round %d, node %s received a signed state from node %s differing from its local "
					+ "signed state; could not provide all details!";

	private final IssEventInfo issEventInfo;

	static Function<String, MerkleDataOutputStream> merkleOutFn = dumpLoc -> {
		try {
			return new MerkleDataOutputStream(Files.newOutputStream(Path.of(dumpLoc)), false);
		} catch (Exception e) {
			log.warn("Unable to use suggested dump location {}, falling back to STDOUT!", dumpLoc, e);
			return new MerkleDataOutputStream(System.out, false);
		}
	};

	public IssListener(IssEventInfo issEventInfo) {
		this.issEventInfo = issEventInfo;
	}

	@Override
	public void notifyError(
			Platform platform,
			AddressBook addressBook,
			SwirldState swirldsState, Event[] events,
			NodeId self, NodeId other,
			long round, Instant consensusTime, long numConsEvents,
			byte[] sig,
			byte[] hash
	) {
		try {
			ServicesState issState = (ServicesState) swirldsState;
			issEventInfo.alert(consensusTime);
			if (issEventInfo.shouldDumpThisRound()) {
				var msg = String.format(
						ISS_ERROR_MSG_PATTERN,
						round, self.getId(), other.getId(),
						encodeHexString(sig), encodeHexString(hash),
						encodeHexString(issState.accounts().getRootHash().getValue()),
						encodeHexString(issState.storage().getRootHash().getValue()),
						encodeHexString(issState.topics().getRootHash().getValue()));
				log.error(msg);
				dumpFcms(issState, self, round);
			}
		} catch (Exception any) {
			String fallbackMsg = String.format(
					ISS_FALLBACK_ERROR_MSG_PATTERN, round, String.valueOf(self), String.valueOf(other));
			log.warn(fallbackMsg, any);
		}
	}

	public static void dumpFcms(ServicesState state, NodeId self, long round) throws IOException {
		dump(state.accounts(), "accounts", self, round);
		dump(state.storage(), "storage", self, round);
		dump(state.topics(), "topics", self, round);
	}

	private static void dump(MerkleNode fcm, String name, NodeId self, long round) throws IOException {
		var out = merkleOutFn.apply(String.format(IssListener.FC_DUMP_LOC_TPL,
				ServicesMain.class.getName(), self.getId(), name, round));
		out.writeMerkleTree(fcm);
		out.close();
	}

	public static class LoggedIssMeta {
		public final long round, self, other;
		public final byte[] sig, hash, accountsHash, storageHash, topicsHash;

		public LoggedIssMeta(
				long round, long self, long other,
				byte[] sig, byte[] hash, byte[] accountsHash, byte[] storageHash, byte[] topicsHash
		) {
			this.self = self;
			this.other = other;
			this.round = round;
			this.sig = sig;
			this.hash = hash;
			this.storageHash = storageHash;
			this.accountsHash = accountsHash;
			this.topicsHash = topicsHash;
		}
	}
}
