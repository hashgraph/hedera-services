package com.hedera.services.bdd.suites.utils.sysfiles.serdes;

import java.util.Collections;
import java.util.Map;

public class StandardSerdes {
	public static final Map<Long, SysFileSerde<String>> SYS_FILE_SERDES = Collections.unmodifiableMap(
			Map.of(
					101l, new AddrBkJsonToGrpcBytes(),
					102l, new NodesJsonToGrpcBytes(),
					111l, new FeesJsonToGrpcBytes(),
					112l, new XRatesJsonToGrpcBytes(),
					121l, new JutilPropsToSvcCfgBytes("application.properties"),
					122l, new JutilPropsToSvcCfgBytes("api-permission.properties")
			)
	);

}
