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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Properties;

import static java.lang.Boolean.parseBoolean;

/**
 * Reads and maintains custom properties from a file.
 */
public class CustomProperties {
	private static final Logger log = LogManager.getLogger(CustomProperties.class);
	private Properties customProperties = new Properties();

	/**
	 * Reads custom properties from a file path.
	 *
	 * @param file
	 * 		the properties file to be read, if null, customProperties will be empty.
	 * @param isResource
	 * 		whether the properties file can be loaded as a resource, i.e. on classpath
	 */
	public CustomProperties(String file, boolean isResource) {
		if (file == null) {
			return;
		}

		InputStream input = null;
		try {
			if (isResource) {
				input = CustomProperties.class.getClassLoader().getResourceAsStream(file);
			} else {
				input = new FileInputStream(file);
			}
			customProperties.load(input);
			if (!parseBoolean(Optional.ofNullable(System.getenv("IN_CIRCLE_CI")).orElse("false"))) {
				log.info("loaded properties from " + file + "\n" + customProperties);
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Retrieves the string value of the property matching the given name.
	 *
	 * @param name
	 * 		the key of the property to be retrieved
	 * @param defaultValue
	 * 		default value to be returned if the named property does not exist
	 * @return string value of the property
	 */
	public String getString(String name, String defaultValue) {
		String rv = null;
		rv = customProperties.getProperty(name);
		if (rv == null) {
			rv = defaultValue;
		}
		return rv;
	}

	/**
	 * Retrieves the int value of the property matching the given name.
	 *
	 * @param name
	 * 		the key of the property to be retrieved
	 * @param defaultValue
	 * 		default value to be returned if the named property does not exist or
	 * 		the value retrieved is not a parsable integer which results in {@link NumberFormatException}
	 * @return int value of the property
	 */
	public int getInt(String name, int defaultValue) {
		int rv = 0;
		try {
			rv = Integer.parseInt(customProperties.getProperty(name));
		} catch (NumberFormatException e) {
			rv = defaultValue;
		}
		return rv;
	}


	/**
	 * Retrieves the long value of the property matching the given name.
	 *
	 * @param name
	 * 		the key of the property to be retrieved
	 * @param defaultValue
	 * 		default value to be returned if the named property does not exist or
	 * 		the value retrieved is not a parsable long which results in {@link NumberFormatException}
	 * @return long value of the property
	 */
	public long getLong(String name, long defaultValue) {
		long rv = 0;
		try {
			rv = Long.parseLong(customProperties.getProperty(name));
		} catch (NumberFormatException e) {
			rv = defaultValue;
		}
		return rv;
	}
}
