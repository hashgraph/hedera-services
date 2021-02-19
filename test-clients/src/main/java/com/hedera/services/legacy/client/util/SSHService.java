package com.hedera.services.legacy.client.util;

/*-
 * ‌
 * Hedera Services Test Clients
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

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.time.temporal.ChronoUnit.SECONDS;


public class SSHService {

	private static final Logger log = LogManager.getLogger(SSHService.class);
	private static final Marker MARKER = MarkerManager.getMarker("REGRESSION_TESTS");
	private static final Marker ERROR = MarkerManager.getMarker("EXCEPTION");

	private static final long MAX_COMMAND_OUTPUT_WATCH = 5000000000l;

	public static final int SSH_TEST_CMD_AFTER_SEC = 60;
	static final String REMOTE_EXPERIMENT_LOCATION = "remoteExperiment/";

	private String user;
	private String ipAddress;
	private ArrayList<String> files;
	private File keyFile;
	private SSHClient ssh;
	private Session session;

	private Instant lastExec;

	public SSHService(String user, String ipAddress, File keyFile) throws SocketException {
		this.user = user;
		this.ipAddress = ipAddress;
		this.keyFile = keyFile;

		ssh = buildSession();
		if (this.ssh == null) {
			throw new SocketException("Unable to connect to cloud instance via ssh.");
		}
		lastExec = Instant.now();
	}

	private String readStream(InputStream is) {
		String returnString = "";
		byte[] tmp = new byte[1024];
		try {
			while (is.available() > 0) {
				int i = is.read(tmp, 0, 1024);
				if (i < 0) {
					break;
				}
				returnString += new String(tmp, 0, i);
				log.info(MARKER, new String(tmp, 0, i));
			}
		} catch (IOException | NullPointerException e) {
			log.error(ERROR, "SSH Command failed! Could not read returned streams", e);
		}
		return returnString;
	}

	private ArrayList<String> readCommandOutput(Session.Command cmd) {
		ArrayList<String> returnArray = new ArrayList<>();
		if (cmd == null) {
			return returnArray;
		}

		String fullString = "";
		log.trace(MARKER, "reading command");

		InputStream is = cmd.getInputStream();
		InputStream es = cmd.getErrorStream();
		long startTime = System.nanoTime();

		while (true) {
			fullString += readStream(is);
			fullString += readStream(es);
			if (!cmd.isOpen()) {
				if (!isStreamEmpty(is)) {
					continue;
				}
				if (!isStreamEmpty(es)) {
					continue;
				}
				break;
			}
			if ((System.nanoTime() - startTime) > MAX_COMMAND_OUTPUT_WATCH) {
				log.info(MARKER, "monitored output for 5 seconds, exiting loop.");
				break;
			}
			try {
				Thread.sleep(1000);
			} catch (Exception ee) {
				ee.printStackTrace();
			}
		}

		String lines[] = fullString.split("\\r?\\n");
		log.trace(MARKER, "lines in output {}", lines.length);
		for (String line : lines) {
			returnArray.add(line);
			log.trace(MARKER, "Added {} to returnArray", line);
		}
		return returnArray;
	}

	private boolean isStreamEmpty(InputStream is) {
		try {
			if (is.available() == 0) {
				return true;
			}
		} catch (IOException | NullPointerException e) {
			log.error(ERROR, "SSH command failed! Failed to check if stream is empty", e);
		}
		return false;
	}

	Collection<String> getListOfFiles(ArrayList<String> extension) {
		Collection<String> returnCollection = new ArrayList<>();
		String extensions = "\\( ";
		for (int i = 0; i < extension.size(); i++) {
			if (i > 0) {
				extensions += " -o ";
			}
			extensions += "-name \"" + extension.get(i) + "\"";
		}
		extensions += " \\) ";
		String pruneDirectory = ""; //"-not -path \"*/data/*\"";
		String commandStr = "find . " + extensions + pruneDirectory;
		final Session.Command cmd = execCommand(commandStr, "Find list of Files based on extension", -1);
		log.info(MARKER, "Extensions to look for on node {}: ", ipAddress, extensions);
		returnCollection = readCommandOutput(cmd);

		return returnCollection;
	}

	public List<String> scpFrom(String topLevelFolders, ArrayList<String> downloadExtensions) {
		try {
			log.info(MARKER, "top level folder: {}", topLevelFolders);
			Collection<String> foundFiles = getListOfFiles(downloadExtensions);
			log.info(MARKER, "total files found:{}", foundFiles.size());
			List<String> localFiles = new LinkedList<>();
			for (String file : foundFiles) {
				if (file.isEmpty()){
					continue;
				}
				String currentLine = file;
				/* remove everything before remoteExperiments and "remoteExpeirments" add in experiment folder and node
				. */
				String cutOffString = REMOTE_EXPERIMENT_LOCATION;
				int cutOff = currentLine.indexOf(cutOffString) + cutOffString.length() - 1;

				log.info(MARKER,
						String.format("CutOff of '%d' computed for the line '%s' with cutOffString of " +
										"'%s'.",
								cutOff, currentLine, cutOffString));

				if (cutOff >= 0 && !currentLine.isEmpty() && cutOff < currentLine.length()) {
					currentLine = currentLine.substring(cutOff);
				} else {
					log.error(MARKER,
							String.format("Invalid cutOff of '%d' computed for the line '%s' with cutOffString of " +
											"'%s'.",
									cutOff, currentLine, cutOffString));
				}

				currentLine = topLevelFolders + currentLine;

				File fileToSplit = new File(currentLine);
				if (!fileToSplit.exists()) {

					/* has to be getParentFile().mkdirs() because if it is not JAVA will make a directory with the name
					of the file like remoteExperiments/swirlds.jar. This will cause scp to take the input as a filepath
					and not the file itself leaving the directory structure like remoteExperiments/swirlds.jar/swirlds
					.jar
					 */
					fileToSplit.getParentFile().mkdirs();

				}
				log.info(MARKER, "downloading {} from node {} putting it in {}", file, ipAddress,
						fileToSplit.getPath());
				ssh.newSCPFileTransfer().download(file, fileToSplit.getPath());
				localFiles.add(fileToSplit.getPath());
			}
			return localFiles;
		} catch (IOException | StringIndexOutOfBoundsException e) {
			log.error(ERROR, "Could not download files", e);
		}
		return null;
	}

	private Session.Command execCommand(String command, String description, int joinSec) {
		return execCommand(command, description, joinSec, true);
	}

	private Session.Command execCommand(String command, String description, int joinSec, boolean reconnectIfNeeded) {
		int returnValue = -1;
		try {
			if (reconnectIfNeeded) {
				reconnectIfNeeded();
			}
			session = ssh.startSession();
			final Session.Command cmd = session.exec(command);
			if (joinSec <= 0) {
				cmd.join();
			} else {
				cmd.join(joinSec, TimeUnit.SECONDS);
			}
			returnValue = cmd.getExitStatus();
			log.trace(MARKER, "'{}' command:\n{}\nexit status: {} :: {}",
					description, command, returnValue, cmd.getExitErrorMessage());
			lastExec = Instant.now();
			return cmd;
		} catch (ConnectionException e) {
			log.error(ERROR, " Join wait time out, joinSec={} command={} description={}", e, joinSec, command,
					description);
		} catch (IOException | NullPointerException e) {
			log.error(ERROR, "'{}' command failed!", description, e);
		} catch (Exception e) {
			log.error(ERROR, "Unexpected error, joinSec={} command={} description={}", e, joinSec, command,
					description);
		} finally {
			try {
				if (session != null) {
					session.close();
				}
			} catch (IOException e) {
				log.error(ERROR, "could not close node {} session when executing '{}'",
						ipAddress, description, e);
			}
		}
		return null;
	}

	SSHClient buildSession() {
		SSHClient client = new SSHClient();
		int count = 0;
		while (count < 10) {
			try {
				KeyProvider keys = client.loadKeys(keyFile.getPath());
				client.addHostKeyVerifier(new PromiscuousVerifier());
				client.useCompression();
				client.connect(this.ipAddress);
				client.authPublickey(this.user, keys);

				if (client.isConnected()) {
					return client;
				}
			} catch (IOException | IllegalThreadStateException e) {
				log.error(ERROR, "attempt {} to connect to node {} failed, will retry {} more times.", count,
						this.ipAddress, 10 - count, e);
			}
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				log.error(ERROR, "Unable to sleep thread before retrying to connect to {}", this.ipAddress, e);
			}
			count++;
		}
		log.error(ERROR, "Could not connect with the node over ssh");
		return null;
	}

	public ArrayList<String> getFiles() {
		return files;
	}

	public void setFiles(ArrayList<String> files) {
		this.files = files;
	}

	public boolean isConnected() {
		if (ssh == null) {
			return false;
		} else {
			return ssh.isConnected();
		}
	}

	private void reconnectIfNeeded() {
		if (lastExec.until(Instant.now(), SECONDS) > SSH_TEST_CMD_AFTER_SEC) {
			execCommand("echo test", "test if connection broken", -1, false);
		}
		if (ssh.isConnected()) {
			return;
		}
		close();
		log.debug(MARKER, "Reconnecting to node {}", this.ipAddress);
		ssh = buildSession();
		lastExec = Instant.now();
	}

	public void close() {
		try {
			ssh.close();
		} catch (Exception e) {
			log.error(ERROR, "Error while closing old connection to {}", this.ipAddress, e);
		}
	}

}
