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

	private Storage storage;
	private Bucket bucket;

	private static String CREDENTIALS_PATH = ".ssh/gcp-credit.json";
	private static String HEDERA_SERVICES_PROJECTID = "hedera-regression";
	private static String SERVICES_REGRESSION_BUCKET = "services-regression-jrs-files";

	public ServiceGCPUploadHelper(String pathToConfig, String projectId)  {
		try {
			System.out.println("User.home: " + System.getProperty("user.home"));
			String absolutePathToConfig = Paths.get(System.getProperty("user.home")).resolve(pathToConfig).toString();
			System.out.println("Path to credential file: " + absolutePathToConfig);

			//Credentials credentials = GoogleCredentials.getApplicationDefault();

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

		String fileToUpload = currentDir.resolve("hedera-node/15589.gz").toString();
		String absolutePathToFile = Paths.get(System.getProperty("user.home")).resolve(fileToUpload).toString();

		System.out.println("file to upload: " + absolutePathToFile);

		String tmpFileName = FilenameUtils.getName(absolutePathToFile);
		System.out.println("Just file name: " + tmpFileName);
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

	public void uploadFileWithGsutil(final String gzFile, final String bucketName,
			final String targetDir) {
		ProcessBuilder pb = new ProcessBuilder( "gsutil", "-m", "cp", gzFile, "gs://"+bucketName + "/" + targetDir);

		try {
			Process process = pb.start();
			process.waitFor();
		} catch(IOException e) {
			log.warn("Error while starting the process to upload state file {}", gzFile, e);
		} catch (InterruptedException ie) {
			log.warn("Upload process was interrupted while uploading state file {}:", gzFile, ie);
		}

		log.info("Done uploading state file {}", gzFile);
	}
}

