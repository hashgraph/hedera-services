import com.hedera.node.app.service.util.impl.UtilServiceImpl;

module com.hedera.node.app.service.util.impl {
	requires transitive com.hedera.node.app.service.util;

	provides com.hedera.node.app.service.util.UtilService with
			UtilServiceImpl;

	exports com.hedera.node.app.service.util.impl to
			com.hedera.node.app.service.util.impl.itest;
	exports com.hedera.node.app.service.util.impl.handlers;
}
