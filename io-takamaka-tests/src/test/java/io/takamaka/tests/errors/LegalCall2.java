package io.takamaka.tests.errors;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.math.BigInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.beans.CodeExecutionException;
import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.references.Classpath;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.signatures.NonVoidMethodSignature;
import io.hotmoka.beans.signatures.VoidMethodSignature;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StringValue;
import io.takamaka.tests.TakamakaTest;

class LegalCall2 extends TakamakaTest {
	private static final BigInteger _20_000 = BigInteger.valueOf(20_000);
	private static final BigInteger _1_000_000_000 = BigInteger.valueOf(1_000_000_000);
	private static final ClassType C = new ClassType("io.takamaka.tests.errors.legalcall2.C");

	@BeforeEach
	void beforeEach() throws Exception {
		mkBlockchain(_1_000_000_000);
	}

	@Test @DisplayName("install jar")
	void installJar() throws TransactionException, CodeExecutionException, IOException {
		addJarStoreTransaction(account(0), _20_000, BigInteger.ONE, takamakaCode(), bytesOf("legalcall2.jar"), takamakaCode());
	}

	@Test @DisplayName("new C().test(); toString() == \"53331\"")
	void newTestToString() throws TransactionException, CodeExecutionException, IOException {
		TransactionReference jar = addJarStoreTransaction(account(0), _20_000, BigInteger.ONE, takamakaCode(), bytesOf("legalcall2.jar"), takamakaCode());
		Classpath classpath = new Classpath(jar, true);

		StorageReference c = addConstructorCallTransaction(account(0), _20_000, BigInteger.ONE, classpath, new ConstructorSignature(C));
		postInstanceMethodCallTransaction(account(0), _20_000, BigInteger.ONE, classpath, new VoidMethodSignature(C, "test"), c);
		StringValue result = (StringValue) addInstanceMethodCallTransaction(account(0), _20_000, BigInteger.ONE, classpath, new NonVoidMethodSignature(C, "toString", ClassType.STRING), c);

		assertEquals("53331", result.value);
	}
}