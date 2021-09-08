package com.hedera.services.statecreation;


import com.hedera.services.statecreation.creationtxns.ServiceGCPUploadHelper;
import com.hedera.services.utils.FileUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public final class SavedStateHandler {
	private static final Logger log = LogManager.getLogger(SavedStateHandler.class);

	private SavedStateHandler() {}

	private static final String CREDENTIALS_PATH = ".ssh/gcp-credit.json";

	private static final String PROJECT_ID = "hedera-regression";
	private static final String SERVICES_REGRESSION_BUCKET = "services-regression-jrs-files";
	private static final String FIXED_PATH_TO_STATE = "data/saved/com.hedera.services.ServicesMain/0/123";
	private static final String FILE_TYPE = "text/plain";

	private static ServiceGCPUploadHelper serviceGCPUploadHelper =
			new ServiceGCPUploadHelper(CREDENTIALS_PATH, PROJECT_ID);
	private static String zipFileName;

	private static Path zipFilePath;

	public static void zipState() {
		Path currentDir = FileSystems.getDefault().getPath(".").toAbsolutePath();
		Path target = FileUtil.findNewestDirOrFileUnder(currentDir.resolve(FIXED_PATH_TO_STATE));
		zipFileName = target.getFileName().toString();

		log.info("Zip fle name base: {}", zipFileName);
		zipFilePath = FileUtil.gzipDir(target, zipFileName+".gz");
	}

	public static void uploadStateFileGsutil(final String buckerName,
			final String targetDir, final Properties properties) {
		serviceGCPUploadHelper.uploadFileWithGsutil(zipFilePath.toString(), buckerName, targetDir, properties);
	}

	public static void uploadStateFile() {
		try {
			serviceGCPUploadHelper.uploadFile(zipFileName, zipFilePath.toString(), FILE_TYPE, SERVICES_REGRESSION_BUCKET);
		} catch (IOException e) {
			log.error("Upload zipped state file to GCP cloud storage failed! ", e);
		}
	}

	public static void uploadStateFileTest() {
		try {

			Path currentDir = FileSystems.getDefault().getPath(".").toAbsolutePath();

			String fileToUpload = currentDir.resolve("2644.gz").toString();
			String absolutePathToFile = Paths.get(System.getProperty("user.home")).resolve(fileToUpload).toString();

			log.info("file to upload: {}", absolutePathToFile);

			String tmpFileName = FilenameUtils.getName(absolutePathToFile);
			String targetFileName = "auto-upload-test-dir/" + tmpFileName;

			serviceGCPUploadHelper.uploadFile(targetFileName, absolutePathToFile, FILE_TYPE, SERVICES_REGRESSION_BUCKET);
		} catch (IOException e) {
			log.error("Upload zipped state file to GCP cloud storage failed! ", e);
		}
	}
}
