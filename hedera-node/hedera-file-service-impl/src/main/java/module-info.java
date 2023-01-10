import com.hedera.node.app.service.file.impl.FileServiceImpl;

module com.hedera.node.app.service.file.impl {
	requires transitive com.hedera.node.app.service.file;

	provides com.hedera.node.app.service.file.FileService with
			FileServiceImpl;

	exports com.hedera.node.app.service.file.impl to
			com.hedera.node.app.service.file.impl.itest;
	exports com.hedera.node.app.service.file.impl.handlers;
}
