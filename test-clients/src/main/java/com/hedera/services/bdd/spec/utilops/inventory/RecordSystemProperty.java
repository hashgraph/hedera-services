package com.hedera.services.bdd.spec.utilops.inventory;

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Function;

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.suites.HapiApiSuite.APP_PROPERTIES;

public class RecordSystemProperty<T> extends UtilOp {
	private static final Logger log = LogManager.getLogger(RecordSystemProperty.class);

	private final String property;
	private final Function<String, T> converter;
	private final Consumer<T> historian;

	public RecordSystemProperty(String property, Function<String, T> converter, Consumer<T> historian) {
		this.property = property;
		this.converter = converter;
		this.historian = historian;
	}

	@Override
	protected boolean submitOp(HapiApiSpec spec) throws Throwable {
		Map<String, String> nodeProps = new HashMap<>();
		var op = QueryVerbs.getFileContents(APP_PROPERTIES).addingConfigListTo(nodeProps);
		allRunFor(spec, op);

		T value;
		if (!nodeProps.containsKey(property)) {
			var defaultProps = loadDefaults();
			if (!defaultProps.containsKey(property)) {
				throw new IllegalStateException(String.format(
						"Nothing can be recorded for putative property '%s'!", property));
			}
			var defaultValue = defaultProps.get(property);
			log.info("Recorded default '{}' = '{}'", property, defaultValue);
			value = converter.apply(defaultValue);
		} else {
			log.info("Recorded '{}' override = '{}'", property, nodeProps.get(property));
			value = converter.apply(nodeProps.get(property));
		}
		historian.accept(value);
		return false;
	}

	Map<String, String> loadDefaults() throws IOException {
		var defaultProps = new Properties();
		defaultProps.load(RecordSystemProperty.class.getClassLoader().getResourceAsStream("bootstrap.properties"));
		Map<String, String> defaults = new HashMap<>();
		defaultProps.stringPropertyNames().forEach(p -> defaults.put(p, defaultProps.getProperty(p)));
		return defaults;
	}
}
