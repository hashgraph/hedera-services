package com.hedera.services.bdd.suites.utils.keypairs;

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

import com.google.protobuf.Descriptors;
import com.google.protobuf.GeneratedMessageV3;
import com.hederahashgraph.api.proto.java.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;

public class Utils {

	private static final Logger logger = LogManager.getLogger(Utils.class);
	private static final TimeZone TIMEZONE_UTC = TimeZone.getTimeZone("UTC");
	private static final String DATE_TIME_FULL_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
	private static final String DATE_TIME_SHORT_MM_FIRST_PATTERN = "MM/dd/yy";

	/**
	 * In JSON format, the Timestamp type is encoded as a string in the RFC 3339 format:
	 * "{year}-{month}-{day}T{hour}:{min}:{sec}Z"
	 * where {year} is always expressed using four digits while {month}, {day},{hour}, {min}, and {sec} are zero-padded
	 * to two digits each.
	 * For example: "2019-01-15T01:30:15Z".
	 */

	public static Timestamp rfcTimeStringToTimestamp(String string) {
		if (string == null) {
			throw new IllegalArgumentException(
					String.format("Date time string must be in %s%s format.", DATE_TIME_FULL_PATTERN, "'Z'"));
		}
		return getTimestamp(string, getDateFormat(string));
	}


	public static Calendar excelDateTimeToCalendar(String excelDate) {

		final String applyingPattern = excelDate.contains(
				"T") ? DATE_TIME_FULL_PATTERN : DATE_TIME_SHORT_MM_FIRST_PATTERN;
		final SimpleDateFormat formatter = new SimpleDateFormat(applyingPattern);
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		Calendar calendar = Calendar.getInstance();

		try {
			calendar.setTime(formatter.parse(excelDate));
		} catch (ParseException ex) {
			logger.error("Parsing error.", ex);
			return null;
		}
		return calendar;
	}


	private static Timestamp getTimestamp(String excelDate, SimpleDateFormat formatter) {
		try {
			Date date = formatter.parse(excelDate);
			long seconds = date.getTime() / 1000;
			return Timestamp.newBuilder().setSeconds(seconds).build();
		} catch (ParseException ex) {
			logger.error("Parsing error.", ex);
			return Timestamp.newBuilder().setSeconds(0).build();
		}
	}


	public static Timestamp calendarToTimestamp(Calendar cal) {
		try {
			Date d = cal.getTime();
			long seconds = d.getTime() / 1000;
			return Timestamp.newBuilder().setSeconds(seconds).build();
		} catch (Exception ex) {
			logger.error("Protobuff building error.", ex);
			return Timestamp.newBuilder().build();
		}
	}


	public static String calendarToExcelDateString(Calendar cal) {
		SimpleDateFormat df = new SimpleDateFormat("MM/dd/yy");
		return df.format(cal.getTime());
	}


	/**
	 * Convert a Timestamp to an Instant
	 *
	 * @param timestamp
	 * @return
	 */
	public static Instant convertProtoTimeStamp(Timestamp timestamp) {
		return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
	}

	/**
	 * With a base date string as the starting UTC datetime for the beginning of the day, add
	 *
	 * @param excelUtcDate
	 * 		The beginning of the UTC datetime as a date string.
	 * @param hours
	 * 		elapsed hours since the beginning of the UTC date. The value range is in [0, 23] inclusively.
	 * @param minutes
	 * 		elapsed minutes in the moment hour since the beginning of the hour. The value range is in [0, 59]
	 * 		inclusively.
	 * @return a <code>com.hederahashgraph.api.proto.java.Timestamp</code> object if the input can be converted into a
	 * 		valid timestamp.
	 */
	@Nullable
	public static Timestamp excelUtcDateWithOffsetsToTimestamp(@Nonnull String excelUtcDate, final int hours,
			final int minutes) {
		if (excelUtcDate == null || excelUtcDate.isEmpty()) {
			throw new IllegalArgumentException("Input date string is empty.");
		}
		if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
			throw new IllegalArgumentException(
					String.format("Offset 'hours:minutes' is [%s:%s], not in '[0-23:0-59]' format.", hours, minutes));
		}
		final Date date;
		try {
			date = getDateFormat(excelUtcDate).parse(excelUtcDate);
			Calendar calendar = Calendar.getInstance(TIMEZONE_UTC);
			calendar.setTime(date);
			calendar.set(Calendar.HOUR_OF_DAY, hours);
			calendar.set(Calendar.MINUTE, minutes);
			return Timestamp.newBuilder().setSeconds(calendar.getTimeInMillis() / 1000).setNanos(0).build();
		} catch (ParseException cause) {
			logger.error(String.format("Unable to parse date string: %s", excelUtcDate), cause);
		}
		return null;
	}

	/**
	 * Build an AccountID
	 *
	 * @param realm
	 * @param shard
	 * @param account
	 * @return
	 */

	static AccountID createAccountID(final long realm, final long shard, final long account) {
		return AccountID.newBuilder().setRealmNum(realm).setShardNum(shard).setAccountNum(account).build();
	}


	/***
	 * Build an AccountID from a string
	 * @param accountString account id in string form. Can be of the format realm.shard.number or number (in which case
	 *                        realm and shard are assumed to be 0
	 * @return An account ID
	 */

	public static AccountID createAccountID(String accountString) {
		String[] accountList = accountString.split("[.]");
		long realm = 0;
		long shard = 0;
		long number = 0;
		if (accountList.length == 3) {
			try {
				realm = Long.parseLong(accountList[0]);
				shard = Long.parseLong(accountList[1]);
				number = Long.parseLong(accountList[2]);
			} catch (Exception e) {
				logger.warn("Account contains illegal characters");
			}
		} else if (accountList.length == 1) {
			try {
				number = Long.parseLong(accountList[0]);
			} catch (Exception ex) {
				logger.warn("Account contains illegal characters", ex.getMessage());
			}
		}

		return createAccountID(realm, shard, number);
	}

	/**
	 * @throws IllegalArgumentException
	 * 		when the value is negative
	 */
	public static void negativeValueCheck(final long value,
			final String fieldName) throws IllegalArgumentException {
		if (value < 0) {
			throw new IllegalArgumentException(fieldName + " should not be negative");
		}
	}

	/**
	 * @throws IllegalArgumentException
	 * 		when the value is negative or 0
	 */
	public static void greaterThanZeroValueCheck(final long value,
			final String fieldName) throws IllegalArgumentException {
		if (value <= 0) {
			throw new IllegalArgumentException(fieldName + " must be greater than zero");
		}
	}

	/**
	 * @throws IllegalArgumentException
	 * 		when the value is negative
	 */
	public static void notNegativeValueCheck(final long value,
			final String fieldName) throws IllegalArgumentException {
		if (value < 0) {
			throw new IllegalArgumentException(fieldName + " must be greater than zero");
		}
	}

	/***
	 * Checks if a string can be parsed to a long
	 * @param strNum
	 * @return
	 */
	static boolean isLong(String strNum) {
		try {
			Long.parseLong(strNum);
		} catch (NumberFormatException | NullPointerException nfe) {
			return false;
		}
		return true;
	}

	/**
	 * return a string which represents an AccountID
	 *
	 * @param accountID
	 * @return
	 */
	public static String accountIDToString(final AccountID accountID) {
		return String.format("%d.%d.%d", accountID.getShardNum(),
				accountID.getRealmNum(), accountID.getAccountNum());
	}

	/**
	 * return a string which represents a Timestamp
	 *
	 * @param timestamp
	 * @return
	 */
	public static String timestampToString(final Timestamp timestamp) {
		String s = String.valueOf(timestamp.getSeconds());
		if (timestamp.getNanos() != 0) {
			s += "." + timestamp.getNanos();
		}
		return s;
	}

	/**
	 * return a string which represents a TransactionID: accountID_transactionValidStart
	 *
	 * @param transID
	 * @return
	 */
	public static String getTransactionIDString(final TransactionID transID) {
		return accountIDToString(transID.getAccountID()) + "_" + timestampToString(transID.getTransactionValidStart());
	}

	/***
	 *
	 * get the depth of message, return 0 if it doesn't have any nesting message
	 *
	 * @param message
	 * @return
	 */
	public static int getDepth(final GeneratedMessageV3 message) {
		Map<Descriptors.FieldDescriptor, Object> fields = message.getAllFields();
		int depth = 0;
		for (Descriptors.FieldDescriptor descriptor : fields.keySet()) {
			Object field = fields.get(descriptor);
			if (field instanceof GeneratedMessageV3) {
				GeneratedMessageV3 fieldMessage = (GeneratedMessageV3) field;
				depth = Math.max(depth, getDepth(fieldMessage) + 1);
			} else if (field instanceof List) {
				for (Object ele : (List) field) {
					if (ele instanceof GeneratedMessageV3) {
						depth = Math.max(depth, getDepth((GeneratedMessageV3) ele) + 1);
					}
				}
			}
		}
		return depth;
	}


	private static SimpleDateFormat getDateFormat(String dateString) {
		final String applyingPattern = dateString.contains(
				"T") ? DATE_TIME_FULL_PATTERN : DATE_TIME_SHORT_MM_FIRST_PATTERN;
		final SimpleDateFormat formatter = new SimpleDateFormat(applyingPattern);
		//End with 'Z' or simple MM/dd/yyyy indicating in a UTC timezone.
		if (!dateString.contains("T") || dateString.endsWith("Z")) {
			formatter.setTimeZone(TIMEZONE_UTC);
		}
		return formatter;
	}

	static Properties getApplicationProperties() {
		Properties prop = new Properties();
		InputStream input;
		try {
			input = new FileInputStream("./config/application.properties");
			prop.load(input);
		} catch (IOException cause) {
			logger.warn("Unable to read application properties. Message: " + cause.getMessage());
		}
		return prop;
	}

	/**
	 * START Migration from
	 */

	/**
	 * Read input from Console or System.in
	 *
	 * @param format
	 * @param args
	 * @return
	 * @throws IOException
	 */
	public static String readLine(String format, Object... args) throws IOException {
		if (System.console() != null) {
			return System.console().readLine(format, args);
		}
		logger.info(String.format(format, args));
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				System.in));
		return reader.readLine();
	}

	public static String timestampToExcelDateTimeString(Timestamp timestamp) {
		SimpleDateFormat df = new SimpleDateFormat(DATE_TIME_FULL_PATTERN);
		return df.format((new Date(1000L * timestamp.getSeconds())).getTime());
	}

	/**
	 * Return the ResponseCodeEnum in Response
	 *
	 * @param response
	 * @return
	 */
	public static ResponseCodeEnum responseCode(final Response response) {
		if (response.hasTransactionGetReceipt() && response.getTransactionGetReceipt() != null) {
			TransactionGetReceiptResponse receiptResponse = response.getTransactionGetReceipt();

			if (receiptResponse.hasReceipt() && receiptResponse.getReceipt() != null) {
				return receiptResponse.getReceipt().getStatus();
			}

			if (receiptResponse.hasHeader() && receiptResponse.getHeader() != null) {
				return receiptResponse.getHeader().getNodeTransactionPrecheckCode();
			}
		}

		return ResponseCodeEnum.RECEIPT_NOT_FOUND;
	}

	/**
	 * Return the Receipt in Response
	 *
	 * @param response
	 * @return
	 */
	public static TransactionReceipt receipt(final Response response) {
		if (response != null && response.hasTransactionGetReceipt() && response.getTransactionGetReceipt() != null) {
			TransactionGetReceiptResponse receiptResponse = response.getTransactionGetReceipt();

			if (receiptResponse.hasReceipt() && receiptResponse.getReceipt() != null) {
				return receiptResponse.getReceipt();
			}
		}
		return null;
	}

	/**
	 * Migrate on 01/23/2020, Line 430 - 436
	 */
}
