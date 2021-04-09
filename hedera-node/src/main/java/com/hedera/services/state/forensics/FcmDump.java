package com.hedera.services.state.forensics;

import com.hedera.services.ServicesMain;
import com.hedera.services.ServicesState;
import com.swirlds.common.NodeId;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.io.MerkleDataOutputStream;
import com.swirlds.common.merkle.io.MerkleTreeSerializationOptions;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

public class FcmDump {
	static Logger log = LogManager.getLogger(FcmDump.class);

	private static final MerkleTreeSerializationOptions serOptions =
			MerkleTreeSerializationOptions.defaults().setAbbreviated(true);
	static final String FC_DUMP_LOC_TPL = "data/saved/%s/%d/%s-round%d.fcm";
	static final String DUMP_IO_WARNING = "Couldn't dump %s FCM!";

	private final List<Pair<String, Function<ServicesState, MerkleNode>>> fcmFuncs = List.of(
			Pair.of("accounts", ServicesState::accounts),
			Pair.of("storage", ServicesState::storage),
			Pair.of("topics", ServicesState::topics),
			Pair.of("tokens", ServicesState::tokens),
			Pair.of("tokenAssociations", ServicesState::tokenAssociations),
			Pair.of("scheduleTxs", ServicesState::scheduleTxs));

	static Function<String, MerkleDataOutputStream> merkleOutFn = dumpLoc -> {
		try {
			Files.createDirectories(Path.of(dumpLoc).getParent());
			return new MerkleDataOutputStream(Files.newOutputStream(Path.of(dumpLoc)), serOptions);
		} catch (Exception e) {
			log.warn("Unable to use suggested dump location {}, falling back to STDOUT!", dumpLoc, e);
			return new MerkleDataOutputStream(System.out, true);
		}
	};

	public void dumpFrom(ServicesState state, NodeId self, long round) {
		for (var fcmMeta : fcmFuncs) {
			var node = fcmMeta.getRight().apply(state);
			dump(node, fcmMeta.getLeft(), self, round);
		}
	}

	private void dump(MerkleNode fcm, String name, NodeId self, long round) {
		var loc = String.format(FC_DUMP_LOC_TPL, ServicesMain.class.getName(), self.getId(), name, round);
		try (MerkleDataOutputStream out = merkleOutFn.apply(loc)) {
			out.writeMerkleTree(fcm);
		} catch (IOException e) {
			log.warn(String.format(DUMP_IO_WARNING, name));
		}
	}
}
