package com.hedera.services.statecreation;

import com.hedera.services.context.ServicesContext;
import com.hedera.services.statecreation.creationtxns.PostCreateTask;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


public class StateCreationManager {
	static Logger log = LogManager.getLogger(StateCreationManager.class);

	private Map<Integer, String> processOrders = new TreeMap<>();
	private Properties properties;

	ExecutorService executorService;
	private ServicesContext ctx;


	public StateCreationManager(ServicesContext ctx) {
		this.ctx = ctx;
	}

	public void create() {
		loadAndProcessProperties();
		if(processOrders.size() > 0) {
			startCreation();
		}
	}

	private void loadAndProcessProperties() {
		properties = new Properties();
		final String propertyFile = "data/config/entity-layout.properties";
		try (InputStream propStream = Files.newInputStream(Path.of(propertyFile))) {
			properties.load(propStream);
		} catch (IOException e) {
			log.warn("Can't load property file {} for creating saved state.", propertyFile);
		}

		processProperties(properties);
	}

	private void processProperties(Properties props) {
		for (Map.Entry<Object, Object> entry : props.entrySet()) {
			String key = (String)entry.getKey();
			String type = (String)entry.getValue();
			if(key.matches("position.[0-9]+")) {
				String posi = key.substring("position.".length());
				int val = Integer.parseInt(properties.getProperty(type + ".total"));
				if(val > 0) {
					processOrders.put(Integer.parseInt(posi), type);
				}
			}
		}
	}

	private void startCreation() {
		AtomicBoolean allCreated = new AtomicBoolean(false);
		executorService = Executors.newFixedThreadPool(2);

		BuiltinClient client = new BuiltinClient(properties, processOrders, ctx, allCreated);

		log.info("kicked off builtin client to send creation traffic");

		executorService.execute(client);

		PostCreateTask waiter = new PostCreateTask(allCreated, ctx, properties);

		executorService.execute(waiter);
	}
}
