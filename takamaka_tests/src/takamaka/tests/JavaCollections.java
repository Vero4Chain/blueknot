/**
 * 
 */
package takamaka.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import takamaka.blockchain.Classpath;
import takamaka.blockchain.CodeExecutionException;
import takamaka.blockchain.NonVoidMethodSignature;
import takamaka.blockchain.TransactionException;
import takamaka.blockchain.TransactionReference;
import takamaka.blockchain.request.JarStoreTransactionRequest;
import takamaka.blockchain.request.StaticMethodCallTransactionRequest;
import takamaka.blockchain.types.ClassType;
import takamaka.blockchain.values.StringValue;
import takamaka.lang.NonWhiteListedCallException;
import takamaka.memory.InitializedMemoryBlockchain;

/**
 * A test for the Java HashMap class.
 */
class JavaCollections {

	private static final ClassType HASH_MAP_TESTS = new ClassType("takamaka.tests.javacollections.HashMapTests");
	private static final ClassType HASH_SET_TESTS = new ClassType("takamaka.tests.javacollections.HashSetTests");

	private static final BigInteger _200_000 = BigInteger.valueOf(200_000);

	private static final BigInteger ALL_FUNDS = BigInteger.valueOf(1_000_000);

	/**
	 * The blockchain under test. This is recreated before each test.
	 */
	private InitializedMemoryBlockchain blockchain;

	/**
	 * The classpath of the classes being tested.
	 */
	private Classpath classpath;

	@BeforeEach
	void beforeEach() throws Exception {
		blockchain = new InitializedMemoryBlockchain(Paths.get("../takamaka_runtime/dist/takamaka_base.jar"), ALL_FUNDS);
		TransactionReference collections = blockchain.addJarStoreTransaction
			(new JarStoreTransactionRequest(blockchain.account(0), _200_000, blockchain.takamakaBase,
			Files.readAllBytes(Paths.get("../takamaka_examples/dist/javacollections.jar")), blockchain.takamakaBase));

		classpath = new Classpath(collections, true);
	}

	@Test @DisplayName("HashMapTests.testToString1() == [how, are, hello, you, ?]")
	void toString1OnHashMap() throws TransactionException, CodeExecutionException {
		StringValue toString = (StringValue) blockchain.addStaticMethodCallTransaction(new StaticMethodCallTransactionRequest
			(blockchain.account(0), _200_000, classpath, new NonVoidMethodSignature(HASH_MAP_TESTS, "testToString1", ClassType.STRING)));
		assertEquals("[how, are, hello, you, ?]", toString.value);
	}

	@Test @DisplayName("HashMapTests.testToString2() == [how, are, hello, you, ?]")
	void toString2OnHashMap() throws TransactionException, CodeExecutionException {
		StringValue toString = (StringValue) blockchain.addStaticMethodCallTransaction(new StaticMethodCallTransactionRequest
			(blockchain.account(0), _200_000, classpath, new NonVoidMethodSignature(HASH_MAP_TESTS, "testToString2", ClassType.STRING)));
		assertEquals("[how, are, hello, you, ?]", toString.value);
	}

	@Test @DisplayName("HashMapTests.testToString3() fails with a run-time white-listing violation")
	void toString3OnHashMap() throws TransactionException, CodeExecutionException {
		try {
			blockchain.addStaticMethodCallTransaction(new StaticMethodCallTransactionRequest
					(blockchain.account(0), _200_000, classpath, new NonVoidMethodSignature(HASH_MAP_TESTS, "testToString3", ClassType.STRING)));
		}
		catch (TransactionException e) {
			if (e.getCause() instanceof NonWhiteListedCallException)
				return;

			e.printStackTrace();
			fail("wrong exception");
		}
	}

	@Test @DisplayName("HashMapTests.testToString4() == [how, are, hello, you, ?]")
	void toString4OnHashMap() throws TransactionException, CodeExecutionException {
		StringValue toString = (StringValue) blockchain.addStaticMethodCallTransaction(new StaticMethodCallTransactionRequest
			(blockchain.account(0), _200_000, classpath, new NonVoidMethodSignature(HASH_MAP_TESTS, "testToString4", ClassType.STRING)));
		assertEquals("[are, takamaka.tests.javacollections.C@2a, hello, you, ?]", toString.value);
	}

	@Test @DisplayName("HashSetTests.testToString1() == [how, are, hello, you, ?]")
	void toString1OnHashSet() throws TransactionException, CodeExecutionException {
		StringValue toString = (StringValue) blockchain.addStaticMethodCallTransaction(new StaticMethodCallTransactionRequest
			(blockchain.account(0), _200_000, classpath, new NonVoidMethodSignature(HASH_SET_TESTS, "testToString1", ClassType.STRING)));
		assertEquals("[how, are, hello, you, ?]", toString.value);
	}

	@Test @DisplayName("HashSetTests.testToString2() == [how, are, hello, you, ?]")
	void toString2OnHashSet() throws TransactionException, CodeExecutionException {
		try {
			blockchain.addStaticMethodCallTransaction(new StaticMethodCallTransactionRequest
				(blockchain.account(0), _200_000, classpath, new NonVoidMethodSignature(HASH_SET_TESTS, "testToString2", ClassType.STRING)));
		}
		catch (TransactionException e) {
			if (e.getCause() instanceof NonWhiteListedCallException)
				return;

			e.printStackTrace();
			fail("wrong exception");
		}
	}

	@Test @DisplayName("HashSetTests.testToString3() == [how, are, hello, you, ?]")
	void toString3OnHashSet() throws TransactionException, CodeExecutionException {
		StringValue toString = (StringValue) blockchain.addStaticMethodCallTransaction(new StaticMethodCallTransactionRequest
			(blockchain.account(0), _200_000, classpath, new NonVoidMethodSignature(HASH_SET_TESTS, "testToString3", ClassType.STRING)));
		assertEquals("[how, are, takamaka.tests.javacollections.C@2a, hello, you, ?]", toString.value);
	}
}