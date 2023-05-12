/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
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
 */

package com.swirlds.benchmark;

import com.swirlds.common.settings.ParsingUtils;
import com.swirlds.fchashmap.FCHashMapSettingsFactory;
import com.swirlds.jasperdb.settings.JasperDbSettingsFactory;
import com.swirlds.merkledb.settings.MerkleDbSettingsFactory;
import com.swirlds.platform.FCHashMapSettingsImpl;
import com.swirlds.platform.JasperDbSettingsImpl;
import com.swirlds.platform.MerkleDbSettingsImpl;
import com.swirlds.platform.VirtualMapSettingsImpl;
import com.swirlds.virtualmap.VirtualMapSettingsFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Scanner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A trimmed copy of com.swirlds.platform.Settings
 */
@Deprecated
public final class BenchmarkSettings {

    private static final Logger logger = LogManager.getLogger(BenchmarkSettings.class);

    static final File settingsPath = new File(".", "settings.txt");

    /**
     * Settings controlling FCHashMap.
     */
    static FCHashMapSettingsImpl fcHashMap = new FCHashMapSettingsImpl();

    /**
     * Settings controlling VirtualMap.
     */
    static VirtualMapSettingsImpl virtualMap = new VirtualMapSettingsImpl();

    /**
     * Settings controlling JasperDB.
     */
    static JasperDbSettingsImpl jasperDb = new JasperDbSettingsImpl();

    /**
     * Settings controlling MerkleDb.
     */
    static MerkleDbSettingsImpl merkleDb = new MerkleDbSettingsImpl();

    /**
     * Placeholder for BenchmarkConfig properties
     */
    static class BenchmarkConfigStub {
        String benchmarkData;
        boolean saveDataDirectory;
        boolean verifyResult;
        boolean printHistogram;
        String csvOutputFolder;
        String csvFileName;
        int csvWriteFrequency;
        boolean csvAppend;
        String deviceName;
    }

    static BenchmarkConfigStub benchmark = new BenchmarkConfigStub();

    public static void init() {
        loadSettings();
        FCHashMapSettingsFactory.configure(fcHashMap);
        VirtualMapSettingsFactory.configure(virtualMap);
        JasperDbSettingsFactory.configure(jasperDb);
        MerkleDbSettingsFactory.configure(merkleDb);
    }

    private BenchmarkSettings() {}

    private static void loadSettings() {
        final Scanner scanner;
        if (!settingsPath.exists()) {
            return;
        }

        try {
            scanner = new Scanner(settingsPath, StandardCharsets.UTF_8.name());
        } catch (final FileNotFoundException e) { // this should never happen
            logger.error("Can't read {}: {} ", settingsPath, e);
            return;
        }

        logger.info("Reading settings from file:        {}", settingsPath);

        int count = 0;
        while (scanner.hasNextLine()) {
            final String originalLine = scanner.nextLine();
            String line = originalLine;
            final int pos = line.indexOf("#");
            if (pos > -1) {
                line = line.substring(0, pos);
            }
            line = line.trim();
            count++;
            if (!line.isEmpty()) {
                final String[] pars = line.split(",");
                if (pars.length > 0) { // ignore empty lines
                    try {
                        if (!handleSetting(pars)) {
                            logger.error("bad name in {}, line {}: {}", settingsPath, count, originalLine);
                        }
                    } catch (final Exception e) {
                        logger.error("syntax error in {}, line {}: {}", settingsPath, count, originalLine);
                        scanner.close();
                        return;
                    }
                }
            }
        }
        scanner.close();
    }

    private static boolean handleSetting(final String[] pars) {
        String name = pars[0].trim();
        String subName = null;
        if (name.contains(".")) {
            // if the name contains a dot (.), then we need to set a variable that is inside an object
            final String[] split = name.split("\\.");
            name = split[0];
            subName = split[1];
        }
        final String val = pars.length > 1 ? pars[1].trim() : ""; // the first parameter passed in, or "" if none
        boolean good = false; // is name a valid name of a non-final static field in Settings?
        final Field field = getFieldByName(BenchmarkSettings.class.getDeclaredFields(), name);
        if (field != null && !Modifier.isFinal(field.getModifiers())) {
            try {
                if (subName == null) {
                    good = setValue(field, null, val);
                } else {
                    final Field subField = getFieldByName(field.getType().getDeclaredFields(), subName);
                    if (subField != null) {
                        good = setValue(subField, field.get(BenchmarkSettings.class), val);
                    }
                }
            } catch (final IllegalArgumentException | IllegalAccessException e) {
                logger.error("illegal line in {}: {}, {}  {}", settingsPath, pars[0], pars[1], e);
            }
        }

        if (!good) {
            logger.warn("{} is not a valid setting name", pars[0]);
            return false;
        }
        return true;
    }

    private static Field getFieldByName(final Field[] fields, final String name) {
        for (final Field f : fields) {
            if (f.getName().equalsIgnoreCase(name)) {
                return f;
            }
        }
        return null;
    }

    private static boolean setValue(final Field field, final Object object, final String value)
            throws IllegalAccessException {
        final Class<?> t = field.getType();
        if (t == String.class) {
            field.set(object, value);
            return true;
        } else if (t == char.class) {
            field.set(object, value.charAt(0));
            return true;
        } else if (t == byte.class) {
            field.set(object, Byte.parseByte(value));
            return true;
        } else if (t == short.class) {
            field.set(object, Short.parseShort(value));
            return true;
        } else if (t == int.class) {
            field.set(object, Integer.parseInt(value));
            return true;
        } else if (t == long.class) {
            field.set(object, Long.parseLong(value));
            return true;
        } else if (t == boolean.class) {
            field.set(object, parseBoolean(value));
            return true;
        } else if (t == float.class) {
            field.set(object, Float.parseFloat(value));
            return true;
        } else if (t == double.class) {
            field.set(object, Double.parseDouble(value));
            return true;
        } else if (t == Duration.class) {
            field.set(object, ParsingUtils.parseDuration(value));
            return true;
        }
        return false;
    }

    private static boolean parseBoolean(String par) {
        if (par == null) {
            return false;
        }
        String p = par.trim().toLowerCase();
        if (p.equals("")) {
            return false;
        }
        String f = p.substring(0, 1);
        return !(p.equals("0") || f.equals("f") || f.equals("n") || p.equals("off"));
    }
}
