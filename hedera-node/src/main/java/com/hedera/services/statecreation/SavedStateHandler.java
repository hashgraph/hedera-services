package com.hedera.services.statecreation;


import com.hedera.services.statecreation.creationtxns.ServiceGCPUploadHelper;
import com.hedera.services.utils.FileUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public class SavedStateHandler {
	private static final Logger log = LogManager.getLogger(SavedStateHandler.class);

	private static String CREDENTIALS_PATH = ".ssh/gcp-credit.json";

	private final static String HEDERA_SERVICES_PROJECTID = "hedera-regression";
	private final static String SERVICES_REGRESSION_BUCKET = "services-regression-jrs-files";
	private final static String FIXED_PATH_TO_STATE = "data/saved/com.hedera.services.ServicesMain/0/123";
	private final static String FILE_TYPE = "text/plain";

	private static ServiceGCPUploadHelper serviceGCPUploadHelper = new ServiceGCPUploadHelper(CREDENTIALS_PATH, HEDERA_SERVICES_PROJECTID);
	private static String zipFileName;

	private static Path zipFilePath;

	public static void zipState() {
		Path currentDir = FileSystems.getDefault().getPath(".").toAbsolutePath();
		Path target = FileUtil.findNewestDirOrFileUnder(currentDir.resolve(FIXED_PATH_TO_STATE));
		zipFileName = target.getFileName().toString();

		log.info("Zip fle name base: " + zipFileName);
		zipFilePath = FileUtil.gzipDir(target, zipFileName+".gz");
	}

	public static void uploadStateFile() {
		try {
			serviceGCPUploadHelper.uploadFile(zipFileName, zipFilePath.toString(), FILE_TYPE, SERVICES_REGRESSION_BUCKET);
		} catch (IOException e) {
			log.error("Upload zipped state file to GCP cloud storage failed! {}", e);
		}
	}
}
