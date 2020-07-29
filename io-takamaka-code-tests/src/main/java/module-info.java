open module io.takamaka.code.tests {
	requires io.hotmoka.beans;
	requires io.hotmoka.nodes;
	requires io.hotmoka.memory;
	requires io.takamaka.code.constants;
	requires io.takamaka.code.verification;
	requires io.takamaka.code.whitelisting;
	requires org.junit.jupiter.api;
	requires io.takamaka.code.instrumentation;
	requires io.takamaka.code.engine;
	requires org.slf4j;
	requires maven.model;
	requires plexus.utils;
}