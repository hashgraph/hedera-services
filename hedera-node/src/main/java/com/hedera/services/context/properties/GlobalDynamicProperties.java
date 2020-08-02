package com.hedera.services.context.properties;

import com.hederahashgraph.api.proto.java.ServicesConfigurationList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import static java.util.Collections.unmodifiableSet;
import static java.util.Map.entry;
import static java.util.Set.of;

public class GlobalDynamicProperties {
	String LEGACY_PROPS_LOC = "data/config/application.properties";

	static ThrowingStreamProvider fileStreamProvider = loc -> Files.newInputStream(Paths.get(loc));

	static Logger log = LogManager.getLogger(GlobalDynamicProperties.class);

	Map<String, Object> globalDynProps = Collections.emptyMap();

	ServicesConfigurationList fromLegacyFile() {
		throw new AssertionError("Not implemented");
	}

	private void initLegacyProps() {
		if (!new File(LEGACY_PROPS_LOC).exists()) {
			return;
		} else {
			var jutilProps = new Properties();

			try {
				var fin = fileStreamProvider.newInputStream(LEGACY_PROPS_LOC);
				jutilProps.load(fin);
				globalDynProps = new HashMap<>();
				LEGACY_PROP_NAMES
						.stream()
						.filter(jutilProps.stringPropertyNames()::contains)
						.forEach(legacyProp -> globalDynProps.put(
								legacyProp,
								LEGACY_PROP_TRANSFORMS.getOrDefault(legacyProp, s -> s)
										.apply(jutilProps.getProperty(legacyProp))));
			} catch (IOException e) {
				log.warn("Ignoring problem loading legacy props from '{}'.",  LEGACY_PROPS_LOC, e);
			}

		}
	}

	public static final Set<String> LEGACY_PROP_NAMES = unmodifiableSet(of(
	));

	private static final Map<String, Function<String, Object>> LEGACY_PROP_TRANSFORMS = Map.ofEntries(
	);
}
