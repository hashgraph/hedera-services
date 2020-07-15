package com.hedera.services.legacy.logic;

/*-
 * ‌
 * Hedera Services Node
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Reads maintains custom properties from a file.
 *
 * @author hua
 */
public class CustomProperties {
  private static final Logger log = LogManager.getLogger(CustomProperties.class);

  private final Properties customProperties;

  public CustomProperties(Properties customProperties) {
	  this.customProperties = customProperties;
  }

  /**
   * Retrieve the string value of the property matching the given name.
   *
   * @param name the key of the property
   * @param defaultValue value to be retured if the named property does not exist
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
   * Retrieve the int value of the property matching the given name.
   *
   * @param name the key of the property
   * @return if the value is not a parsable integer, NumberFormatException will result.
   */
  public int getInt(String name, int defaultValue) {
    int rv = 0;
    try {
      rv = Integer.parseInt(customProperties.getProperty(name));
    } catch (Exception e) {
      rv = defaultValue;
    }
    return rv;
  }

  public long getLong(String name, long defaultValue) {
    long rv = 0;
    try {
      rv = Long.parseLong(customProperties.getProperty(name));
    } catch (Exception e) {
      rv = defaultValue;
    }
    return rv;
  }

  public double getDouble(String name, double defaultValue) {
    try {
      return Double.parseDouble(customProperties.getProperty(name));
    } catch (Exception ignore) {}
    return defaultValue;
  }

  public boolean getBoolean(String name, boolean defaultValue) {
    String property = customProperties.getProperty(name);
    return null == property ? defaultValue : Boolean.parseBoolean(property);
  }

  public Properties getCustomProperties() {
    return customProperties;
  }
}
