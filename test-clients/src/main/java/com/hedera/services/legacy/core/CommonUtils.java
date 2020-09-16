package com.hedera.services.legacy.core;

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

import com.google.gson.Gson;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.TextFormat;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hederahashgraph.api.proto.java.TransferList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.ethereum.util.ByteUtil;
import org.junit.Assert;

import static java.lang.Boolean.parseBoolean;

/**
 * Common utilities.
 *
 * @author hua
 */
public class CommonUtils {

	private static final int EVM_ACCOUNT_SIZE_BYTES = 20;

	/**
	 * Read UTF-8 content from a file given a path on disk.
	 *
	 * @param filePath
	 * 		the path of the file
	 */
	public static String readFileContentUTF8(String filePath) {
		String fileString = null;
		try {
			fileString = new String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8);
		} catch (IOException e) {
			e.printStackTrace();
		}

		return fileString;
	}

	/**
	 * Extracts the string data from a ByteBuffer.
	 */
	public static String extractData(ByteBuffer peerAppData) {
		byte[] bytes = extractBytes(peerAppData);
		String rv = convert2String(bytes);
		return rv;
	}

	/**
	 * Converts bytes to String.
	 */
	public static String convert2String(byte[] bytes) {
		return new String(bytes);
	}

	/**
	 * Extracts the byte data from a ByteBuffer.
	 */
	public static byte[] extractBytes(ByteBuffer peerAppData) {
		int pos = peerAppData.position();
		int limit = peerAppData.limit();
		int len = limit - pos;
		byte[] bytes = new byte[len];
		peerAppData.get(bytes, pos, len);
		return bytes;
	}

	/**
	 * Sleep given seconds.
	 */
	public static void napMillis(long timeInMillis) {
		try {
			Thread.sleep(timeInMillis);
		} catch (Exception e) {
		}
	}

	/**
	 * Sleep given seconds.
	 */
	public static void nap(int timeInSec) {
		try {
			Thread.sleep(timeInSec * 1000);
		} catch (Exception e) {
		}
	}

	public static void nap(double timeInSec) {
		try {
			Thread.sleep((long) timeInSec * 1000);
		} catch (Exception e) {
		}
	}

	/**
	 * Get string representation of an object array.
	 */
	public static String array2String(Object[] array) {
		StringBuffer sb = new StringBuffer("\n");
		int i = 1;
		for (Object e : array) {
			sb.append(i + ". ").append(e).append("\n");
			i++;
		}
		return sb.toString();
	}

	/**
	 * Write bytes to a file.
	 *
	 * @param path
	 * 		the file path to write bytes
	 * @param data
	 * 		the byte array data
	 */
	public static void writeToFile(String path, byte[] data) throws IOException {
		writeToFile(path, data, false);
	}

	/**
	 * Write bytes to a file.
	 *
	 * @param append
	 * 		append to existing file if true
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
	 * Write string to a file using UTF_8 encoding.
	 *
	 * @param path
	 * 		the file path to write bytes
	 * @param data
	 * 		the byte array data
	 */
	public static void writeToFileUTF8(String path, String data) throws IOException {
		byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
		writeToFile(path, bytes);
	}

	/**
	 * Write string to a file using UTF_8 encoding.
	 *
	 * @param append
	 * 		append to existing file if true
	 */
	public static void writeToFileUTF8(String path, String data, boolean append) throws IOException {
		byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
		writeToFile(path, bytes, append);
	}

	/**
	 * Encode bytes as base64.
	 *
	 * @param bytes
	 * 		to be encoded
	 * @return base64 string
	 */
	public static String base64encode(byte[] bytes) {
		String rv = null;
		rv = Base64.getEncoder().encodeToString(bytes);
		return rv;
	}

	/**
	 * Decode base64 string to bytes.
	 *
	 * @param base64string
	 * 		to be decoded
	 * @return decoded bytes
	 */
	public static byte[] base64decode(String base64string) {
		byte[] rv = null;
		rv = Base64.getDecoder().decode(base64string);
		return rv;
	}

	/**
	 * Convert long value to bytes.
	 *
	 * @param x
	 * 		long to be converted
	 * @return byte array
	 */
	public static byte[] longToBytes(long x) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.putLong(x);
		return buffer.array();
	}

	/**
	 * Convert int value to bytes.
	 *
	 * @param x
	 * 		int to be converted
	 * @return byte array
	 */
	public static byte[] intToBytes(int x) {
		ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
		buffer.putInt(x);
		return buffer.array();
	}

	/**
	 * Convert bytes to int value.
	 *
	 * @param bytes
	 * 		input bytes
	 * @return the int value
	 */
	public static int bytesToInt(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.allocate(Integer.BYTES);
		buffer.put(bytes);
		buffer.flip();// need flip
		return buffer.getInt();
	}

	/**
	 * Convert bytes to long value.
	 *
	 * @param bytes
	 * 		input bytes
	 * @return the long value
	 */
	public static long bytesToLong(byte[] bytes) {
		ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
		buffer.put(bytes);
		buffer.flip();// need flip
		return buffer.getLong();
	}

	/**
	 * Serialize a Java object to bytes.
	 *
	 * @param object
	 * 		the Java object to be serialized
	 * @return converted bytes
	 */
	public static byte[] convertToBytes(Object object) throws IOException {
		try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); ObjectOutput out = new ObjectOutputStream(
				bos)) {
			out.writeObject(object);
			return bos.toByteArray();
		}
	}

	/**
	 * Deserialize a Java object to bytes.
	 *
	 * @param bytes
	 * 		to be deserialized
	 * @return the Java object constructed
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
	 * Copy bytes.
	 *
	 * @param start
	 * 		from position
	 * @param length
	 * 		number of bytes to copy
	 * @param bytes
	 * 		source byte array
	 */
	public static byte[] copyBytes(int start, int length, byte[] bytes) {
		byte[] rv = new byte[length];
		for (int i = 0; i < length; i++) {
			rv[i] = bytes[start + i];
		}
		return rv;
	}

	/**
	 * Concatenate two byte arrays into one.
	 *
	 * @return the combined byte array.
	 */
	public static byte[] mergeByteArray(byte[] a, byte[] b) {
		int al = a.length;
		int bl = b.length;
		byte[] rv = new byte[al + bl];

		for (int i = 0; i < al; i++) {
			rv[i] = a[i];
		}
		for (int i = al; i < (al + bl); i++) {
			rv[i] = b[i];
		}

		return rv;
	}

	/**
	 * Returns a short form of a string in the form of display first and last n characters.
	 */
	public static String shortForm(String string, int n) {
		int d = 2 * n;
		int len = string.length();
		if (string.length() <= d) {
			return string;
		}
		String rv = "length=" + len + ": content=" + string.substring(0, n) + " ... " + string
				.substring(len - d, len);
		return rv;
	}

	/**
	 * Serialize a Java object into Json string.
	 *
	 * @param obj
	 * 		Java object
	 * @return Json string
	 */
	public static String serialize2Json(Object obj) {
		Gson gsonObj = new Gson();
		String json = gsonObj.toJson(obj);
		return json;
	}

	/**
	 * Deserialize a Json string into a Java object.
	 *
	 * @param json
	 * 		Java object
	 * @return Json string
	 */
	@SuppressWarnings({ "unchecked" })
	public static List<String> deserializeListOfStringFromJson(String json) {
		Gson gsonObj = new Gson();
		List<String> rv = gsonObj.fromJson(json, ArrayList.class);
		return rv;
	}

	public static byte[] hexToBytes(String data) {
		byte[] rv = new byte[0];
		try {
			rv = Hex.decodeHex(data);
		} catch (DecoderException e) {
			e.printStackTrace();
		}
		return rv;
	}

	/**
	 * Reads the bytes of a small binary file as a resource file.
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
	 * Reads a resource file.
	 *
	 * @param filePath
	 * 		the resource file to be read
	 * @param myClass
	 * 		the calling class
	 */
	public static <T> byte[] readBinaryFileAsResource(String filePath, Class<T> myClass)
			throws IOException, URISyntaxException {
		Path path = Paths.get(myClass.getClassLoader().getResource(filePath).toURI());
		return Files.readAllBytes(path);
	}

	public static String[] splitLine(String line) {
		String[] elms = line.split(",");

		for (int i = 0; i < elms.length; ++i) {
			elms[i] = elms[i].trim();
		}

		return elms;
	}

	/**
	 * method to calculate 20 bytes Solidity Address
	 *
	 * @return hexString
	 */
	public static String calculateSolidityAddress(int indicator, long realmNum, long accountNum) {
		byte[] solidityByteArray = new byte[20];
		byte[] indicatorBytes = ByteUtil.intToBytes(indicator);
		copyArray(0, solidityByteArray, indicatorBytes);
		byte[] realmNumBytes = ByteUtil.longToBytes(realmNum);
		copyArray(4, solidityByteArray, realmNumBytes);
		byte[] accountNumBytes = ByteUtil.longToBytes(accountNum);
		copyArray(12, solidityByteArray, accountNumBytes);
		return ByteUtil.toHexString(solidityByteArray);
	}

	public static void copyArray(int startInToArray, byte[] toArray, byte[] fromArray) {
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
	 * 		transaction record
	 * @param body
	 * 		the originating transaction body
	 */
	public static void checkRecord(TransactionRecord record, TransactionBody body) {
		TransactionID txID = body.getTransactionID();
		System.out.println("$$$$ record=" + record + ", txID=" + txID);
		Assert.assertEquals(txID, record.getTransactionID());
		Assert.assertEquals(body.getMemo(), record.getMemo());
		Assert.assertEquals(true, record.getConsensusTimestamp().getSeconds() > 0);
		//in some cases contract create /contract call could charge more than the declared fee
		if (!body.hasContractCall() && !body.hasContractCreateInstance()) {
			Assert.assertEquals(true, body.getTransactionFee() >= record.getTransactionFee());
		}

		Assert.assertEquals(true, record.getTransactionHash().size() > 0);

		if (ResponseCodeEnum.INVALID_PAYER_SIGNATURE.equals(record.getReceipt().getStatus())) {
			Assert.assertEquals(false, record.hasTransferList());
		} else {
			Assert.assertEquals(true, record.hasTransferList());
		}

		if (body.hasCryptoTransfer() && ResponseCodeEnum.SUCCESS.equals(record.getReceipt().getStatus())) {
			TransferList transferList = body.getCryptoTransfer().getTransfers();
			Assert.assertEquals(true, record.getTransferList().toString().contains(transferList.toString()));
		}

		System.out.println(":) record check success!");
	}

	/**
	 * Generates number of bytes.
	 *
	 * @param numBytes
	 * @return bytes generated
	 */
	public static byte[] genRandomBytes(int numBytes) {
		byte[] fileContents = new byte[numBytes];
		(new Random()).nextBytes(fileContents);
		return fileContents;
	}

}
