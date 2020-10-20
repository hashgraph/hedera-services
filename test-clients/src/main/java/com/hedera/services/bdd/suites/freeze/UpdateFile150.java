package com.hedera.services.bdd.suites.freeze;

/*-
 * ‌
 * Hedera Services Test Clients
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;

public class UpdateFile150 extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(UpdateServerFiles.class);
	private static int FREEZE_LAST_MINUTES = 2;
	private static String fileIDString = "UPDATE_FEATURE"; // mnemonic for file 0.0.150

	public static void main(String... args) {
		new UpdateFile150().runSuiteSync();
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return allOf(
				positiveTests()
		);
	}

	private List<HapiApiSpec> positiveTests() {
		return List.of(new HapiApiSpec[] {
						updateWithWrongFileID(),
						getUpdateFileInfo(),
						missingFileHash(),
						updateWithShortContent(),
						updateWithLargeContent(),
						notAllowedToDelete(),
						emptyUpdateFile(),
						verifyFileHash(),
//						updateWithHash(),
				}
		);
	}

	private HapiApiSpec updateWithWrongFileID() {
		return defaultHapiSpec("updateWithWrongFileID")
				.given(
				).when(
						freeze().setFileID("0.0.152")
								.startingIn(60).seconds().andLasting(FREEZE_LAST_MINUTES).minutes()
								.hasPrecheck(ResponseCodeEnum.INVALID_FILE_ID)
				).then(
				);
	}

	private HapiApiSpec getUpdateFileInfo() {
		return defaultHapiSpec("getUpdateFileInfo")
				.given(
						withOpContext((spec, opLog) -> {
							ByteString zeroBytes = ByteString.copyFrom(new byte[0]);
							spec.registry().saveBytes("zeroBytes", zeroBytes);
						})
				).when(
						//on start, 0.0.150 should already exist and has empty content
						getFileInfo(fileIDString),
						getFileContents(fileIDString)
								.hasContents(spec -> spec.registry().getBytes("zeroBytes"))
				).then(
				);
	}

	/**
	 * Not setting file hash filed should get INVALID_FREEZE_TRANSACTION_BODY error
	 *
	 * @return
	 */
	private HapiApiSpec missingFileHash() {
		final byte[] new4k = TxnUtils.randomUtf8Bytes(TxnUtils.BYTES_4K);
		return defaultHapiSpec("missingFileHash")
				.given(
						UtilVerbs.updateLargeFile(GENESIS, fileIDString, ByteString.copyFrom(new4k))
				).when(
						freeze().setFileID(fileIDString)
								.startingIn(1)
								.minutes().andLasting(FREEZE_LAST_MINUTES).minutes()
								.hasPrecheck(ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY)
				).then(
				);
	}

	/**
	 * A correct update feature with file hash
	 */
	private HapiApiSpec updateWithShortContent() {
		final byte[] new4k = TxnUtils.randomUtf8Bytes(30 * 4096);
		return defaultHapiSpec("updateWithShortContent")
				.given(
						withOpContext((spec, opLog) -> {
							spec.registry().saveBytes("updateFileContent",
									ByteString.copyFrom(new4k));
						})
				).when(
						UtilVerbs.updateLargeFile(GENESIS, fileIDString, ByteString.copyFrom(new4k)),
						sleepFor(2000) //wait reach consensus
				).then(
						getFileContents(fileIDString)
								.hasContents(spec -> spec.registry().getBytes("updateFileContent"))
				);
	}

	/**
	 * A correct update feature with file hash
	 */
	private HapiApiSpec updateWithLargeContent() {
		final byte[] largeContent = TxnUtils.randomUtf8Bytes(80 * 1000);
		return defaultHapiSpec("updateWithLargeContent")
				.given(
						withOpContext((spec, opLog) -> {
							spec.registry().saveBytes("updateFileContent",
									ByteString.copyFrom(largeContent));
						})
				).when(
						UtilVerbs.updateLargeFile(GENESIS, fileIDString, ByteString.copyFrom(largeContent)),
						sleepFor(2000) //wait reach consensus
				).then(
						getFileContents(fileIDString)
								.hasContents(spec -> spec.registry().getBytes("updateFileContent"))
				);
	}

	// Last test try delete the special file
	private HapiApiSpec notAllowedToDelete() {
		return defaultHapiSpec("notAllowedToDelete")
				.given(
				).when(
						fileDelete(fileIDString)
								.hasPrecheck(ENTITY_NOT_ALLOWED_TO_DELETE)
				).then(
				);
	}

	private HapiApiSpec updateWithHash() {
		final byte[] largeContent = TxnUtils.randomUtf8Bytes(180 * 1000);
		final byte[] hash = sha384Digest(largeContent);
		return defaultHapiSpec("updateWithHash")
				.given(
						withOpContext((spec, opLog) -> {
							spec.registry().saveBytes("updateFileContent",
									ByteString.copyFrom(largeContent));
						})
				).when(
						UtilVerbs.updateLargeFile(GENESIS, fileIDString, ByteString.copyFrom(largeContent)),
						freeze().setFileID(fileIDString)
								.setFileHash(hash)
								.startingIn(1)
								.minutes().andLasting(FREEZE_LAST_MINUTES).minutes()
				).then(
				);
	}


	private HapiApiSpec emptyUpdateFile() {
		final byte[] notUsed = TxnUtils.randomUtf8Bytes(TxnUtils.BYTES_4K);
		final byte[] hash = sha384Digest(notUsed);
		return defaultHapiSpec("emptyUpdateFile")
				.given(
						freeze().setFileID(fileIDString)
								.startingIn(1)
								.setFileHash(hash)
								.minutes().andLasting(FREEZE_LAST_MINUTES).minutes()
						// check server log it should has error about empty file
				).when(
				).then(
				);
	}


	/**
	 * Intentionally set the wrong hash
	 */
	private HapiApiSpec verifyFileHash() {
		final byte[] notUsed = TxnUtils.randomUtf8Bytes(TxnUtils.BYTES_4K);
		final byte[] new4k = TxnUtils.randomUtf8Bytes(TxnUtils.BYTES_4K);
		final byte[] hash = sha384Digest(notUsed);
		return defaultHapiSpec("verifyFileHash")
				.given(
						UtilVerbs.updateLargeFile(GENESIS, fileIDString, ByteString.copyFrom(new4k))
				).when(
						freeze().setFileID(fileIDString)
								.setFileHash(hash)
								.startingIn(1)
								.minutes().andLasting(FREEZE_LAST_MINUTES).minutes()
						// check server log it should has error about hash mismatch
				).then(
				);
	}

	public static byte[] sha384Digest(byte[] message) {
		MessageDigest digest = null;
		try {
			digest = MessageDigest.getInstance("SHA-384");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		byte[] hash = digest.digest(message);
		return hash;
	}

}
