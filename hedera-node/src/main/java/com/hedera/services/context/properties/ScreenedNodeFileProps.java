package com.hedera.services.context.properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hedera.services.context.properties.BootstrapProperties.GLOBAL_DYNAMIC_PROPS;
import static com.hedera.services.context.properties.BootstrapProperties.NODE_PROPS;
import static com.hedera.services.context.properties.PropUtils.loadOverride;
import static java.util.Map.entry;

public class ScreenedNodeFileProps implements PropertySource {
	static Logger log = LogManager.getLogger(ScreenedNodeFileProps.class);

	static Map<String, String> STANDARDIZED_NAMES = Map.ofEntries(
			entry("port", "grpc.port"),
			entry("tlsPort", "grpc.tlsPort")
	);

	static String NODE_PROPS_LOC = "data/config/node.properties";
	static String LEGACY_NODE_PROPS_LOC = "data/config/application.properties";
	static String MISPLACED_PROP_TPL = "Property '%s' is not node-local, please find it a proper home!";
	static String DEPRECATED_PROP_TPL = "Property name '%s' is deprecated, please use '%s' in '%s' instead!";
	static String UNPARSEABLE_PROP_TPL = "Value '%s' is unparseable for '%s' (%s), being ignored!";

	static ThrowingStreamProvider fileStreamProvider = loc -> Files.newInputStream(Paths.get(loc));

	Map<String, Object> fromFile = new HashMap<>();

	public ScreenedNodeFileProps() {
		loadFrom(LEGACY_NODE_PROPS_LOC, false);
		loadFrom(NODE_PROPS_LOC, true);
		var msg = "Node-local properties overridden on disk are:\n " + NODE_PROPS.stream()
				.filter(fromFile::containsKey)
				.sorted()
				.map(name -> String.format("%s=%s", name, fromFile.get(name)))
				.collect(Collectors.joining("\n  "));
		log.info(msg);
	}

	private void loadFrom(String loc, boolean warnOnMisplacedProp) {
		var overrideProps = new Properties();
		loadOverride(loc, overrideProps, fileStreamProvider, log);
		for (String prop : overrideProps.stringPropertyNames()) {
			tryOverriding(prop, overrideProps.getProperty(prop), warnOnMisplacedProp);
		}
	}

	private void tryOverriding(String name, String value, boolean warnOnMisplacedProp) {
		if (STANDARDIZED_NAMES.containsKey(name)) {
			var standardName = STANDARDIZED_NAMES.get(name);
			log.warn(String.format(DEPRECATED_PROP_TPL, name, standardName, NODE_PROPS_LOC));
			name = standardName;
		}
		if (!NODE_PROPS.contains(name)) {
			if (warnOnMisplacedProp) {
				log.warn(String.format(MISPLACED_PROP_TPL, name));
			}
			return;
		}
		try {
			fromFile.put(name, BootstrapProperties.PROP_TRANSFORMS.get(name).apply(value));
		} catch (Exception reason) {
			log.warn(String.format(UNPARSEABLE_PROP_TPL, value, name, reason.getClass().getSimpleName()));
		}
	}

	@Override
	public boolean containsProperty(String name) {
		return fromFile.containsKey(name);
	}

	@Override
	public Object getProperty(String name) {
		return fromFile.get(name);
	}

	@Override
	public Set<String> allPropertyNames() {
		return fromFile.keySet();
	}
}
