package com.hedera.services.legacy.unit;

/*-
 * ‌
 * Hedera Services Node
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

import com.hedera.services.legacy.config.AsyncPropertiesObject;
import com.hedera.services.legacy.config.PropertiesLoader;
import com.hedera.services.legacy.logic.CustomProperties;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class PropertyLoaderTest {
	private final int RECIEPT_TTL = 180;
	private final int THRESHOLD_TTL = 90000;
	private final int TX_MAX_DURATION = 120;
	
	private final int RECIEPT_TTL_UPD = 280;
	private final int TX_MAX_DURATION_UPD = 220;
	
	private final String APP_CONFIG_FILE_PATH = "./configuration/dev/testConfig.properties";
	private final String API_CONFIG_FILE_PATH = "./configuration/dev/testApi.properties";

	public static void initialize(String applicationPropsFilePath, String apiPropertiesFilePath) {
			PropertiesLoader.applicationProps = propertiesFrom(applicationPropsFilePath);
			AsyncPropertiesObject.loadAsynchProperties(PropertiesLoader.applicationProps);
			PropertiesLoader.log.info("Application Properties Populated with these values :: "+ PropertiesLoader.applicationProps.getCustomProperties());
	}

	private static CustomProperties propertiesFrom(String loc) {
		var delegate = new Properties();
		try {
			delegate.load(Files.newInputStream(Paths.get(loc)));
		} catch (IOException impossible) {
		}
		return new CustomProperties(delegate);
	}

	public static void populatePropertiesWithConfigFilesPath(String applicationPropsFilePath, String apiPropertiesFilePath) {
		initialize(applicationPropsFilePath,apiPropertiesFilePath);
	}

	@Before
  public void setUp() throws Exception {
    loadPropertyFiles(String.valueOf(RECIEPT_TTL), String.valueOf(THRESHOLD_TTL), String.valueOf(TX_MAX_DURATION));
    populatePropertiesWithConfigFilesPath(APP_CONFIG_FILE_PATH,API_CONFIG_FILE_PATH);
  }

  private void loadPropertyFiles(String txReceiptTTL, String thresholdTxRecordTTL,
      String txMaximumDuration) {
    // create test application property file
    try (OutputStream output = new FileOutputStream(APP_CONFIG_FILE_PATH)) {

      Properties prop = new Properties();
      // set the properties value
      prop.setProperty("txReceiptTTL", txReceiptTTL);
      prop.setProperty("thresholdTxRecordTTL", thresholdTxRecordTTL);
      prop.setProperty("txMaximumDuration", txMaximumDuration);
      prop.store(output, null);
      output.flush();
      output.close();
    } catch (IOException io) {
    }

    // create test api property file
    try (OutputStream output = new FileOutputStream(API_CONFIG_FILE_PATH)) {
      Properties prop = new Properties();
      // set the properties value
      prop.setProperty("createAccount", "0-*");
      prop.setProperty("cryptoTransfer", "0-*");
      prop.store(output, null);
      output.flush();
      output.close();

    } catch (IOException io) {
    }
  }
  
  /**
   * Test for Loading from Configuration file and then update and reload from Proto Object
   */

  @Test
  public void testAPPChangeProperties() throws Exception {
    ServicesConfigurationList servicesConfigurationList = getAPPConfigPropProto(RECIEPT_TTL_UPD,TX_MAX_DURATION_UPD);
    // now reload properties 
    PropertiesLoader.populateApplicationPropertiesWithProto(servicesConfigurationList);

    // delete the files after test.
    File appProp = new File(APP_CONFIG_FILE_PATH);
    File appApiProp = new File(API_CONFIG_FILE_PATH);

    if (appProp.delete()) {
    }
    if (appApiProp.delete()) {
    }

  }
  
  private ServicesConfigurationList getAPPConfigPropProto(int recieptTime, int txMaxDuration) {
	  Setting recieptTimeSet = Setting.newBuilder().setName("txReceiptTTL").setValue(String.valueOf(recieptTime)).build();
	  Setting txMaxDurationSet = Setting.newBuilder().setName("txMaximumDuration").setValue(String.valueOf(txMaxDuration)).build();
	  ServicesConfigurationList serviceConfigList = ServicesConfigurationList.newBuilder()
			  																.addNameValue(recieptTimeSet)
			  																.addNameValue(txMaxDurationSet).build();
	  return serviceConfigList;
  }
}
