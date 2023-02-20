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

package com.hedera.services.bdd.suites.validation;

import com.hedera.services.bdd.spec.persistence.Entity;
import com.hedera.services.bdd.spec.persistence.SkipNullRepresenter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

public class YamlHelper {
    private static final Logger log = LogManager.getLogger(TokenPuvSuite.class);

    public static void serializeEntity(Entity it, String manifestLoc) {
        var yamlOut = new Yaml(new SkipNullRepresenter());
        var itManifestLoc = it.getManifestAbsPath();
        it.setManifestAbsPath(null);
        var doc = yamlOut.dumpAs(it, Tag.MAP, null);
        try {
            var writer = Files.newBufferedWriter(Paths.get(manifestLoc));
            writer.write(doc);
            writer.close();
        } catch (IOException e) {
            log.warn("Could not serialize {}!", it.getName(), e);
        } finally {
            it.setManifestAbsPath(itManifestLoc);
        }
    }

    public static String yaml(String name) {
        return name + ".yaml";
    }
}
