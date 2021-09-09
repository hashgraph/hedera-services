package com.hedera.services.statecreation.creationtxns;

import com.google.auth.Credentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class ServiceGCPUploadHelper {
	private static Logger log = LogManager.getLogger(ServiceGCPUploadHelper.class);

	private static final String DEFAULT_GSUTIL_CMD="gsutil";
	private static final String USER_HOME_PROPERTY = "user.home";

	private Storage storage;

	public ServiceGCPUploadHelper(String pathToConfig, String projectId)  {
		try {
			log.info("User.home: {}", System.getProperty(USER_HOME_PROPERTY));
			String absolutePathToConfig = Paths.get(System.getProperty(USER_HOME_PROPERTY))
					.resolve(pathToConfig).toString();
			log.info("Path to credential file: {}", absolutePathToConfig);

			Credentials credentials = GoogleCredentials.fromStream(new FileInputStream(absolutePathToConfig));
			storage = StorageOptions.newBuilder().setCredentials(credentials).setProjectId(
					projectId).build().getService();
		} catch (IOException e) {
			log.warn("Couldn't get correct credentials ", e);
		}
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
		Bucket bucket = storage.get(bucketName);
		if (bucket == null) {
			log.warn("Bucket name {} doesn't exist!", bucketName);
		}
		return bucket;
	}

	public void uploadFileWithGsutil(final String gzFile, final String bucketName,
			final String targetDir, final Properties properties) {
		String gsutilCmd = properties.getProperty("gsutil.command");
		if(gsutilCmd == null || gsutilCmd.isEmpty()) {
			gsutilCmd = DEFAULT_GSUTIL_CMD;
		}
		ProcessBuilder pb = new ProcessBuilder( gsutilCmd, "-m", "cp", gzFile, "gs://"+bucketName + "/" + targetDir);

		try {
			Process process = pb.start();
			process.waitFor();
		} catch(IOException e) {
			log.warn("Error while starting the process to upload state file {}", gzFile, e);
		} catch (InterruptedException ie) {
			log.warn("Upload process was interrupted while uploading state file {}:", gzFile, ie);
			Thread.currentThread().interrupt();
		}

		log.info("Done uploading state file {}", gzFile);
	}


	// For test purpose. Can be removed when finished
	public static void main(String[] args) throws Exception {
		final String DEFAULT_CREDENTIALS_PATH = ".ssh/gcp-credit.json";
		final String DEFAULT_HEDERA_SERVICES_PROJECTID = "hedera-regression";
		final String DEFAULT_SERVICES_REGRESSION_BUCKET = "services-regression-jrs-files";


		ServiceGCPUploadHelper gcpUploader =
				new ServiceGCPUploadHelper(DEFAULT_CREDENTIALS_PATH, DEFAULT_HEDERA_SERVICES_PROJECTID);

		Path currentDir = FileSystems.getDefault().getPath(".").toAbsolutePath();

		String fileToUpload = currentDir.resolve("hedera-node/15589.gz").toString();
		String absolutePathToFile = Paths.get(System.getProperty(USER_HOME_PROPERTY)).resolve(fileToUpload).toString();

		log.info("file to upload: {}", absolutePathToFile);

		String tmpFileName = FilenameUtils.getName(absolutePathToFile);
		log.info("Just file name: {}", tmpFileName);
		String targetFileName = "auto-upload-test-dir/" + tmpFileName;
		gcpUploader.uploadFile(targetFileName, absolutePathToFile, "text/plain", DEFAULT_SERVICES_REGRESSION_BUCKET);
	}
}

