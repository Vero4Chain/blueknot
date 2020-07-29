/**
 * 
 */
package io.takamaka.code.tests.errors;

import java.io.IOException;
import java.math.BigInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.beans.CodeExecutionException;
import io.hotmoka.beans.TransactionException;
import io.takamaka.code.tests.TakamakaTest;

class IllegalSynchronized2 extends TakamakaTest {
	private static final BigInteger _20_000 = BigInteger.valueOf(20_000);
	private static final BigInteger _1_000_000_000 = BigInteger.valueOf(1_000_000_000);

	@BeforeEach
	void beforeEach() throws Exception {
		setNode(_1_000_000_000);
	}

	@Test @DisplayName("install jar")
	void installJar() throws TransactionException, CodeExecutionException, IOException {
		throwsVerificationException(() ->
			addJarStoreTransaction(privateKey(0), account(0), _20_000, BigInteger.ONE, takamakaCode(), bytesOf("illegalsynchronized2.jar"), takamakaCode())
		);
	}
}