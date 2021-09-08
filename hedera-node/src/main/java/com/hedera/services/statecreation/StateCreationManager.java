package com.hedera.services.statecreation;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.state.logic.NetworkCtxManager;
import com.hedera.services.statecreation.creationtxns.PostCreateTask;
import com.hedera.services.txns.submission.BasicSubmissionFlow;
import com.hedera.services.txns.submission.PlatformSubmissionManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
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

@Singleton
public class StateCreationManager {
	static Logger log = LogManager.getLogger(StateCreationManager.class);

	private Map<Integer, String> processOrders = new TreeMap<>();
	private Properties properties;

	ExecutorService executorService;
	private PlatformSubmissionManager submissionManager;
	private BasicSubmissionFlow submissionFlow;
	private NodeLocalProperties nodeLocalProperties;
	private NetworkCtxManager networkCtxManager;


	@Inject
	public StateCreationManager(final PlatformSubmissionManager submissionManager,
			final BasicSubmissionFlow submissionFlow,
			final NodeLocalProperties nodeLocalProperties,
			final NetworkCtxManager networkCtxManager) {
		this.submissionManager = submissionManager;
		this.nodeLocalProperties = nodeLocalProperties;
		this.networkCtxManager = networkCtxManager;
		this.submissionFlow = submissionFlow;
	}

	public void startIfNeeded() {
		if (nodeLocalProperties.isCreateStateFile()
				&& Files.exists(Path.of("data/config/entity-layout.properties"))) {

			loadAndProcessProperties();
			if(processOrders.size() > 0) {
				startCreation();
			}
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

		BuiltinClient client = new BuiltinClient(properties, processOrders,
				submissionManager, submissionFlow, networkCtxManager, allCreated);

		log.info("kicked off builtin client to send creation traffic");

		executorService.execute(client);

		PostCreateTask waiter = new PostCreateTask(allCreated, submissionFlow, properties);

		executorService.execute(waiter);
	}
}
