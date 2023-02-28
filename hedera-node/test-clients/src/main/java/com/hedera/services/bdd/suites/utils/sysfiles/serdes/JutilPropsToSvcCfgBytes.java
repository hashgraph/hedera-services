/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.utils.sysfiles.serdes;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import com.hederahashgraph.api.proto.java.Setting;
import java.io.IOException;
import java.io.StringReader;
import java.util.Comparator;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class JutilPropsToSvcCfgBytes implements SysFileSerde<String> {
    final String fileName;

    public JutilPropsToSvcCfgBytes(String fileName) {
        this.fileName = fileName;
    }

    @Override
    public String fromRawFile(byte[] bytes) {
        try {
            var proto = ServicesConfigurationList.parseFrom(bytes);
            return proto.getNameValueList().stream()
                    .map(setting -> String.format("%s=%s", setting.getName(), setting.getValue()))
                    .sorted(LEGACY_THROTTLES_FIRST_ORDER)
                    .collect(Collectors.joining("\n"));
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Content was not a valid ServicesConfigurationList", e);
        }
    }

    @Override
    public byte[] toRawFile(String styledFile) {
        var jutilConfig = new Properties();
        try {
            jutilConfig.load(new StringReader(styledFile));
            ServicesConfigurationList.Builder protoConfig = ServicesConfigurationList.newBuilder();
            jutilConfig.stringPropertyNames().stream()
                    .sorted(LEGACY_THROTTLES_FIRST_ORDER)
                    .forEach(prop -> protoConfig.addNameValue(
                            Setting.newBuilder().setName(prop).setValue(jutilConfig.getProperty(prop))));
            return protoConfig.build().toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Content was not a valid properties file", e);
        }
    }

    @Override
    public String preferredFileName() {
        return fileName;
    }

    public static final Comparator<String> LEGACY_THROTTLES_FIRST_ORDER = Comparator.<String>comparingInt(prop -> {
                if (isLegacyBouncerProp(prop)) {
                    return 0;
                } else if (prop.startsWith("throttling.hcs")) {
                    return 1;
                } else {
                    return 2;
                }
            })
            .thenComparing(Comparator.naturalOrder());

    private static Set<String> legacyBouncerProps =
            Set.of("throttlingTps", "simpletransferTps", "getReceiptTps", "queriesTps");

    private static boolean isLegacyBouncerProp(String prop) {
        return (!prop.contains("="))
                ? legacyBouncerProps.contains(prop)
                : legacyBouncerProps.contains(prop.substring(0, prop.indexOf("=")));
    }
}
