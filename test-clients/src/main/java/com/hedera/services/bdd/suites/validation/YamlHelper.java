package com.hedera.services.bdd.suites.validation;

import com.hedera.services.bdd.spec.persistence.Entity;
import com.hedera.services.bdd.spec.persistence.SkipNullRepresenter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

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
