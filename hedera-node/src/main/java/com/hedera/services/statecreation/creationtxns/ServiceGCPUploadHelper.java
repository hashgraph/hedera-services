package com.hedera.services.statecreation.creationtxns;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ServiceGCPUploadHelper {
	private static Logger log = LogManager.getLogger(PostCreateTask.class);

	private static String CREDENTIALS_PATH = ".ssh/gcp-credit.json";
	private Storage storage;
	private Bucket bucket;

	private static String HEDERA_SERVICES_PROJECTID = "hedera-regression";
	private static String SERVICES_REGRESSION_BUCKET = "services-regression-jrs-files";

	public ServiceGCPUploadHelper(String pathToConfig, String projectId)  {
		try {
			String absolutePathToConfig = Paths.get(System.getProperty("user.home")).resolve(CREDENTIALS_PATH).toString();
			log.info("Path: " + absolutePathToConfig);

			Credentials credentials = GoogleCredentials.fromStream(new FileInputStream(absolutePathToConfig));
			storage = StorageOptions.newBuilder().setCredentials(credentials).setProjectId(
					projectId).build().getService();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	// Just for test purpose. Can be removed when finished
	public static void main(String[] args) throws Exception {

		ServiceGCPUploadHelper gcpUploader =
				new ServiceGCPUploadHelper(CREDENTIALS_PATH, HEDERA_SERVICES_PROJECTID );

		Path currentDir = FileSystems.getDefault().getPath(".").toAbsolutePath();

		String fileToUpload = currentDir.resolve("hedera-node/9382.gz").toString();
		String absolutePathToFile = Paths.get(System.getProperty("user.home")).resolve(fileToUpload).toString();

		log.info("file to upload: " + absolutePathToFile);

		String tmpFileName = FilenameUtils.getName(absolutePathToFile);
		log.info("Just file name: " + tmpFileName);
		String targetFileName = "auto-upload-test-dir/" + tmpFileName;
		gcpUploader.uploadFile(targetFileName, absolutePathToFile, "text/plain", SERVICES_REGRESSION_BUCKET);
	}


	public String uploadFile(String fileName ,String filePath, String fileType, String bucketName) throws IOException {
		Bucket bucket = getBucket(bucketName);
		if(bucket == null) {
			return null;
		}
		InputStream inputStream = new FileInputStream(filePath);
		Blob blob = bucket.create(fileName, inputStream, fileType);
		return blob.getMediaLink();
	}

	private Bucket getBucket(String bucketName) {
		bucket = storage.get(bucketName);
		if (bucket == null) {
			log.warn("Bucket name " + bucketName + "Doesn't exist!");
		}
		return bucket;
	}
}

