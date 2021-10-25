package com.hedera.services.legacy.core;

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

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.Assertions;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Optional;

import static java.lang.Boolean.parseBoolean;

/**
 * Common utilities.
 */
public class CommonUtils {

	/**
	 * Sleep for the given time in milli seconds
	 *
	 * @param timeInMillis
	 * 		given time in milli seconds
	 */
	public static void napMillis(long timeInMillis) {
		try {
			Thread.sleep(timeInMillis);
		} catch (Exception ignore) {
		}
	}

	/**
	 * Sleep for the given time in seconds
	 *
	 * @param timeInSec
	 * 		given time in seconds
	 */
	public static void nap(int timeInSec) {
		try {
			Thread.sleep(timeInSec * 1000L);
		} catch (Exception ignore) {
		}
	}

	/**
	 * Write bytes to a file
	 *
	 * @param path
	 * 		the file path to write bytes to
	 * @param data
	 * 		the byte array to be written to the file
	 * @throws IOException
	 * 		when failing to write the byte array to the file
	 */
	public static void writeToFile(String path, byte[] data) throws IOException {
		writeToFile(path, data, false);
	}

	/**
	 * Write bytes to a file
	 *
	 * @param path
	 * 		the file path to write bytes to
	 * @param data
	 * 		the byte array to be written to the file
	 * @param append
	 * 		the flag to control whether to append to or to overwrite the file current contents
	 * @throws IOException
	 * 		when failing to write the byte array to the file
	 */
	public static void writeToFile(String path, byte[] data, boolean append) throws IOException {
		File f = new File(path);
		File parent = f.getParentFile();
		if (!parent.exists()) {
			parent.mkdirs();
		}

		FileOutputStream fos = new FileOutputStream(f, append);
		fos.write(data);
		fos.flush();
		fos.close();
	}

	/**
	 * Decode base64 string to bytes
	 *
	 * @param base64string
	 * 		base64 string to be decoded
	 * @return decoded bytes
	 */
	public static byte[] base64decode(String base64string) {
		byte[] rv = null;
		rv = Base64.getDecoder().decode(base64string);
		return rv;
	}

	/**
	 * Deserialize a Java object from given bytes
	 *
	 * @param bytes
	 * 		given byte array to be deserialized
	 * @return the Java object constructed after deserialization
	 */
	public static Object convertFromBytes(byte[] bytes) {
		try (ByteArrayInputStream bis = new ByteArrayInputStream(
				bytes); ObjectInput in = new ObjectInputStream(bis)) {
			return in.readObject();
		} catch (IOException | ClassNotFoundException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Copy a given number of bytes from a byte array
	 *
	 * @param start
	 * 		starting position from which bytes need to be copied
	 * @param length
	 * 		number of bytes to copy
	 * @param bytes
	 * 		source byte array to be copied from
	 * @return copied bytes
	 */
	public static byte[] copyBytes(int start, int length, byte[] bytes) {
		byte[] rv = new byte[length];
		for (int i = 0; i < length; i++) {
			rv[i] = bytes[start + i];
		}
		return rv;
	}

	/**
	 * Reads the bytes of a small binary file as a resource file.
	 *
	 * @param filePath
	 * 		path of the small binary file
	 * @return the byte array of the file contents
	 * @throws IOException
	 * 		when failing to read the file
	 * @throws URISyntaxException
	 * 		when the given file path could not be parsed as a URI reference
	 */
	public static byte[] readBinaryFileAsResource(String filePath)
			throws IOException, URISyntaxException {
		if (parseBoolean(Optional.ofNullable(System.getenv("IN_CIRCLE_CI")).orElse("false"))) {
			if (filePath.contains("/")) {
				int baseIndex = filePath.lastIndexOf('/') + 1;
				int len = filePath.length();
				filePath = filePath.substring(baseIndex, len);
			}
			String path = String.format("/repo/test-clients/testfiles/%s", filePath);
			File f = new File(path);
			System.out.println("[CommonUtils#readBinaryfileAsResource] path = " + f.getAbsolutePath());
			return Files.readAllBytes(new File(path).toPath());
		} else {
			if (ClassLoader.getSystemResource("") == null) {
				return Files.readAllBytes(Paths.get("", filePath));

			} else {
				URI uri = ClassLoader.getSystemResource("").toURI();
				String rootPath = Paths.get(uri).toString();
				Path path = Paths.get(rootPath, filePath);

				return Files.readAllBytes(path);
			}
		}
	}

	/**
	 * Reads a binary file as a resource file.
	 *
	 * @param filePath
	 * 		the binary file to be read
	 * @param myClass
	 * 		the calling class
	 * @param <T>
	 * 		calling class type
	 * @return all bytes read from the resource file
	 * @throws IOException
	 * 		when failing to read the file
	 * @throws URISyntaxException
	 * 		when the given file path could not be parsed as a URI reference
	 */
	public static <T> byte[] readBinaryFileAsResource(String filePath, Class<T> myClass)
			throws IOException, URISyntaxException {
		Path path = Paths.get(myClass.getClassLoader().getResource(filePath).toURI());
		return Files.readAllBytes(path);
	}

	/**
	 * Calculates the hex string of a 20-byte solidity address
	 *
	 * @param indicator
	 * 		indicator value
	 * @param realmNum
	 * 		realm number
	 * @param accountNum
	 * 		solidity contract number
	 * @return the hex string of the 20-byte solidity address
	 */
	public static String calculateSolidityAddress(int indicator, long realmNum, long accountNum) {
		byte[] solidityByteArray = new byte[20];
		byte[] indicatorBytes = Ints.toByteArray(indicator);
		copyArray(0, solidityByteArray, indicatorBytes);
		byte[] realmNumBytes = Longs.toByteArray(realmNum);
		copyArray(4, solidityByteArray, realmNumBytes);
		byte[] accountNumBytes = Longs.toByteArray(accountNum);
		copyArray(12, solidityByteArray, accountNumBytes);
		return com.swirlds.common.CommonUtils.hex(solidityByteArray);
	}

	private static void copyArray(int startInToArray, byte[] toArray, byte[] fromArray) {
		if (fromArray == null || toArray == null) {
			return;
		}
		for (int i = 0; i < fromArray.length; i++) {
			toArray[i + startInToArray] = fromArray[i];
		}
	}

	/**
	 * Checks the record fields against the originating transaction body.
	 *
	 * @param record
	 * 		transaction record whose fields need to be checked
	 * @param body
	 * 		the originating transaction body for comparing transaction record fields
	 */
	public static void checkRecord(TransactionRecord record, TransactionBody body) {
		TransactionID txID = body.getTransactionID();
		System.out.println("$$$$ record=" + record + ", txID=" + txID);
		Assertions.assertEquals(txID, record.getTransactionID());
		Assertions.assertEquals(body.getMemo(), record.getMemo());
		Assertions.assertEquals(true, record.getConsensusTimestamp().getSeconds() > 0);
		//in some cases contract create /contract call could charge more than the declared fee
		if (!body.hasContractCall() && !body.hasContractCreateInstance()) {
			Assertions.assertEquals(true, body.getTransactionFee() >= record.getTransactionFee());
		}

		Assertions.assertEquals(true, record.getTransactionHash().size() > 0);

		if (ResponseCodeEnum.INVALID_PAYER_SIGNATURE.equals(record.getReceipt().getStatus())) {
			Assertions.assertEquals(false, record.hasTransferList());
		} else {
			Assertions.assertEquals(true, record.hasTransferList());
		}

		if (body.hasCryptoTransfer() && ResponseCodeEnum.SUCCESS.equals(record.getReceipt().getStatus())) {
			TransferList transferList = body.getCryptoTransfer().getTransfers();
			Assertions.assertEquals(true, record.getTransferList().toString().contains(transferList.toString()));
		}

		System.out.println(":) record check success!");
	}
}
