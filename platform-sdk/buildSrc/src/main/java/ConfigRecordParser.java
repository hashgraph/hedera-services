/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.file.SourceDirectorySet;

public class ConfigRecordParser {

    private static Pattern FIELD_PATTERN = Pattern.compile(
            "@ConfigProperty\\s*\\(([^)]+)\\)\s+([a-zA-Z0-9_]+)\\s+([a-zA-Z0-9_]+)");
    private static Pattern CONFIG_DATA_PATTERN = Pattern.compile(
            "/\\*\\*(.*?)\\*/\\s+@ConfigData\\s*\\(([^)]+)\\)\\s+public\\s+record\\s+([a-zA-Z0-9_]+)", Pattern.DOTALL);
    private static Pattern JAVA_DOC_PARAM_PATTERN = Pattern.compile("@param\\s+(\\S+)(.*?)(?=@|$)", Pattern.DOTALL);
    private static Pattern PACKAGE_PATTERN = Pattern.compile("package\\s+([^\\s;]+);");
    private static Pattern CONFIG_OPTION_PATTERN = Pattern.compile("([a-zA-Z0-9_]+)\\s*=\\s*([^,]+)");

    public static List<ConfigProperty> parse(final SourceDirectorySet rootDirectories) {
        final List<ConfigProperty> result = new ArrayList<>();
        for (final File rootDirectory : rootDirectories.getSrcDirs()) {
            if (rootDirectory.isDirectory()) {
                try {
                    Files.walk(rootDirectory.toPath())
                            .filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".java"))
                            .map(path -> {
                                try {
                                    return processJavaFile(path);
                                } catch (IOException e) {
                                    throw new RuntimeException("Error in processing " + path, e);
                                }
                            })
                            .forEach(properties -> result.addAll(properties));
                } catch (final Exception e) {
                    throw new RuntimeException("Error in processing " + rootDirectory, e);
                }
            }
        }
        return result;
    }

    public static List<ConfigProperty> processJavaFile(final Path path) throws IOException {
        final List<ConfigProperty> result = new ArrayList<>();

        final String javaCode = Files.readString(path);
        final Matcher configDataMatcher = CONFIG_DATA_PATTERN.matcher(javaCode);
        if (configDataMatcher.find()) {
            String packageName = "";
            final Matcher packagerMatcher = PACKAGE_PATTERN.matcher(javaCode);
            if (packagerMatcher.find()) {
                packageName = packagerMatcher.group(1) + ".";
            }
            final String javaDoc = configDataMatcher.group(1);
            final String configPrefix = configDataMatcher.group(2).replaceAll("\"", "");
            String recordName = packageName + configDataMatcher.group(3);
            // process java doc
            final Map<String, String> descriptions = new HashMap<>();
            final Matcher paramMatcher = JAVA_DOC_PARAM_PATTERN.matcher(javaDoc);
            while (paramMatcher.find()) {
                final String fieldName = paramMatcher.group(1);
                String doc = paramMatcher.group(2)
                        .replaceAll("(\\s+\\*)+\\s+", " ")
                        .trim();
                // capitalize first character
                doc = doc.substring(0, 1).toUpperCase() + doc.substring(1, doc.length() - 1);
                descriptions.put(fieldName, doc);
            }
            // process fields
            final Matcher fieldMatcher = FIELD_PATTERN.matcher(javaCode);
            while (fieldMatcher.find()) {
                final String configOptions = fieldMatcher.group(1);
                final String type = fieldMatcher.group(2);
                final String name = fieldMatcher.group(3);
                String value = "";
                String defaultValue = "";
                final Matcher configOptionMatcher = CONFIG_OPTION_PATTERN.matcher(configOptions);
                while (configOptionMatcher.find()) {
                    final String optionName = configOptionMatcher.group(1);
                    final String optionValue = configOptionMatcher.group(2).replaceAll("\"", "");
                    if (optionName.equals("defaultValue")) {
                        defaultValue = optionValue;
                    } else if (optionName.equals("value")) {
                        value = optionValue;
                    }
                }
                result.add(new ConfigProperty(
                        configPrefix + "." + name,
                        type,
                        defaultValue,
                        descriptions.get(name),
                        recordName,
                        value));
            }
        }
        return result;
    }


}
