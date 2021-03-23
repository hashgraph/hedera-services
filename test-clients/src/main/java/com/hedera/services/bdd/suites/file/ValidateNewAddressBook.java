package com.hedera.services.bdd.suites.file;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.NodeAddressBook;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.suites.file.FetchSystemFiles.unchecked;

public class ValidateNewAddressBook extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(ValidateNewAddressBook.class);

	public static void main(String... args) {
		new ValidateNewAddressBook().runSuiteSync();
	}

	final String TARGET_DIR = "./remote-system-files";

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				fetchFiles()
		);
	}

	private HapiApiSpec fetchFiles() {
		return defaultHapiSpec("ValidateNewAddressBook").given()
				.when()
				.then(getFileContents(NODE_DETAILS)
								.saveTo(path("nodeDetails.bin"))
								.saveReadableTo(unchecked(NodeAddressBook::parseFrom), path("nodeDetails.txt")),
						getFileContents(ADDRESS_BOOK)
								.saveTo(path("addressBook.bin"))
								.saveReadableTo(unchecked(NodeAddressBook::parseFrom), path("addressBook.txt")));
	}

	private String path(String file) {
		return Path.of(TARGET_DIR, file).toString();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
