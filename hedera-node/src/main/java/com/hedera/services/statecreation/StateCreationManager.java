package com.hedera.services.statecreation;

import com.hedera.services.context.ServicesContext;
import com.hedera.services.statecreation.creationtxns.FreezeTxnFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;


public class StateCreationManager {
	static Logger log = LogManager.getLogger(BuiltinClient.class);

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
			executorService = Executors.newSingleThreadExecutor();
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

		processOrders.forEach(this::createEntitiesFor);

		executorService.shutdown();


		System.out.println("Done create the state file and let's shutdown the server.");

		FreezeTxnFactory.newFreezeTxn().freezeStartAt(Instant.now().plusSeconds(10));

		// wait a little bit or check swirlds.log to find the "MAINTENANCE" flag,
		// then gzip and upload the generated saved files
	}

	private void createEntitiesFor(Integer posi, String entityType) {
		final String valStr = properties.getProperty(processOrders.get(posi) + ".total");
		final AtomicInteger totalToCreate = new AtomicInteger(Integer.parseInt(valStr));

		BuiltinClient client = new BuiltinClient(totalToCreate, entityType, ctx);
		if(totalToCreate.get() > 0) {
			System.out.println("Start to build " + valStr + " " + entityType);
			executorService.execute(client);
		}
	}
}
