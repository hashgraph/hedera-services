package com.hedera.services.state.forensics;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.ServicesMain;
import com.hedera.services.ServicesState;
import com.swirlds.common.io.streams.MerkleDataOutputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.system.NodeId;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.List;
import java.util.function.Function;

@Singleton
public class FcmDump {
	private static final Logger log = LogManager.getLogger(FcmDump.class);

	static final String FC_DUMP_LOC_TPL = "data/saved/%s/%d/%s-round%d.fcm";
	static final String DUMP_IO_WARNING = "Couldn't dump %s FCM!";

	@FunctionalInterface
	interface DirectoryCreation {
		Path createDirectories(Path dir, FileAttribute<?>... attrs) throws IOException;
	}

	private DirectoryCreation directoryCreation = Files::createDirectories;

	private Function<String, MerkleDataOutputStream> merkleOutFn = dumpLoc -> {
		try {
			directoryCreation.createDirectories(Path.of(dumpLoc).getParent());
			return new MerkleDataOutputStream(Files.newOutputStream(Path.of(dumpLoc))).setExternal(true);
		} catch (IOException e) {
			/* State dumps cannot be safely enabled in production, so if we get here it will
			be in a dev environment where we can fix the location and re-run the test. */
			log.error("Unable to use suggested dump location {}, please fix", dumpLoc, e);
			throw new UncheckedIOException(e);
		}
	};


	@Inject
	public FcmDump() {
		// Default constructor
	}

	private final List<Pair<String, Function<ServicesState, MerkleNode>>> fcmFuncs = List.of(
			Pair.of("accounts", ServicesState::accounts),
			Pair.of("storage", ServicesState::storage),
			Pair.of("topics", ServicesState::topics),
			Pair.of("tokens", ServicesState::tokens),
			Pair.of("tokenAssociations", ServicesState::tokenAssociations),
			Pair.of("scheduleTxs", ServicesState::scheduleTxs));

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

	/* --- Only used by unit tests --- */
	void setMerkleOutFn(final Function<String, MerkleDataOutputStream> merkleOutFn) {
		this.merkleOutFn = merkleOutFn;
	}

	Function<String, MerkleDataOutputStream> getMerkleOutFn() {
		return merkleOutFn;
	}

	void setDirectoryCreation(final DirectoryCreation directoryCreation) {
		this.directoryCreation = directoryCreation;
	}
}
