package com.hedera.services.bdd.suites.freeze;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileContents;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.freeze;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.utils.ZipUtil.createZip;
import static com.hedera.services.legacy.bip39utils.CryptoUtils.sha384Digest;
import static junit.framework.TestCase.fail;

public class UpdateFile150 extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(UpdateServerFiles.class);
	private static String zipFile = "Archive.zip";
	private static final String DEFAULT_SCRIPT = "src/main/resource/testfiles/updateFeature/updateSettings/exec.sh";

	private static String uploadPath = "updateFiles/";

	private static int FREEZE_LAST_MINUTES = 2;
	private static String fileIDString = "UPDATE_FEATURE"; // mnemonic for file 0.0.150

	public static void main(String... args) {

		if (args.length > 0) {
			uploadPath = args[0];
		}
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
		return Arrays.asList(
//				getUpdateFileInfo(),
//				emptyUpdateFile(),
//				updateWithWrongFileID()
//				missingFileHash(),
				verifyFileHash()
		);
	}

	private byte[] createZipFileData() {
		log.info("Creating zip file from " + uploadPath);
		//create directory if uploadPath doesn't exist
		if (!new File(uploadPath).exists()) {
			new File(uploadPath).mkdirs();
		}
		final String temp_dir = "temp/";
		final String sdk_dir = temp_dir + "sdk/";
		byte[] data = null;
		try {
			//create a temp sdk directory
			File directory = new File(temp_dir);
			if (directory.exists()) {
				// delete everything in it recursively

				FileUtils.cleanDirectory(directory);

			} else {
				directory.mkdir();
			}

			(new File(sdk_dir)).mkdir();
			//copy files to sdk directory
			FileUtils.copyDirectory(new File(uploadPath), new File(sdk_dir));
			createZip(temp_dir, zipFile, DEFAULT_SCRIPT);
			String uploadFile = zipFile;

			log.info("Uploading file " + uploadFile);
			data = Files.readAllBytes(Paths.get(uploadFile));
		} catch (IOException e) {
			log.error("Directory creation failed", e);
			fail("Directory creation failed");
		}
		return data;
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
								.logging()
								.logged()
								.hasContents(spec -> spec.registry().getBytes("zeroBytes"))
				).then(
				);
	}

	private HapiApiSpec emptyUpdateFile() {
		final byte[] new4k = TxnUtils.randomUtf8Bytes(TxnUtils.BYTES_4K);
		final byte[] hash = sha384Digest(new4k);
		return defaultHapiSpec("emptyUpdateFile")
				.given(
						freeze().setFileID(fileIDString)
								.setFileHash(hash)
								.startingIn(1)
								.minutes().andLasting(FREEZE_LAST_MINUTES).minutes()
						// check server log it should has error about empty file
				).when(
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
						// set freeze start time many hours later so freeze will begin during the test
						freeze().setFileID(fileIDString)
								.startingIn(1)
								.minutes().andLasting(FREEZE_LAST_MINUTES).minutes()
								.hasPrecheck(ResponseCodeEnum.INVALID_FREEZE_TRANSACTION_BODY)
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
						// set freeze start time many hours later so freeze will begin during the test
						freeze().setFileID(fileIDString)
								.setFileHash(hash)
								.startingIn(1)
								.minutes().andLasting(FREEZE_LAST_MINUTES).minutes()
						// check server log it should has error about hash mismatch
				).then(
				);
	}

}
