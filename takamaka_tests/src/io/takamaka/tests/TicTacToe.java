/**
 * 
 */
package io.takamaka.tests;

import static io.hotmoka.beans.types.BasicTypes.INT;
import static io.hotmoka.beans.types.BasicTypes.LONG;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.references.Classpath;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.JarStoreTransactionRequest;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.signatures.NonVoidMethodSignature;
import io.hotmoka.beans.signatures.VoidMethodSignature;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.IntValue;
import io.hotmoka.beans.values.LongValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StringValue;
import io.hotmoka.memory.MemoryBlockchain;
import io.hotmoka.nodes.CodeExecutionException;
import io.takamaka.code.constants.Constants;

/**
 * A test for the remote purchase contract.
 */
class TicTacToe extends TakamakaTest {

	private static final ClassType TIC_TAC_TOE = new ClassType("io.takamaka.tests.tictactoe.TicTacToe");

	private static final ConstructorSignature CONSTRUCTOR_TIC_TAC_TOE = new ConstructorSignature(TIC_TAC_TOE);

	private static final BigInteger _200_000 = BigInteger.valueOf(200_000);

	private static final BigInteger _1_000_000_000 = BigInteger.valueOf(1_000_000_000);

	private static final IntValue _1 = new IntValue(1);
	private static final IntValue _2 = new IntValue(2);
	private static final IntValue _3 = new IntValue(3);

	/**
	 * The blockchain under test. This is recreated before each test.
	 */
	private MemoryBlockchain blockchain;

	/**
	 * The creator of the game.
	 */
	private StorageReference creator;

	/**
	 * The first player.
	 */
	private StorageReference player1;

	/**
	 * The second player.
	 */
	private StorageReference player2;

	/**
	 * The classpath of the classes being tested.
	 */
	private Classpath classpath;

	@BeforeEach
	void beforeEach() throws Exception {
		blockchain = MemoryBlockchain.of(Paths.get("../distribution/dist/io-takamaka-code-1.0.jar"),
			_1_000_000_000, BigInteger.valueOf(100_000_000L), BigInteger.valueOf(100_000_000L), BigInteger.valueOf(100_000_000L));

		TransactionReference tictactoe = blockchain.addJarStoreTransaction
			(new JarStoreTransactionRequest(blockchain.account(0), _200_000, BigInteger.ONE, blockchain.takamakaCode(),
			Files.readAllBytes(Paths.get("../takamaka_examples/dist/tictactoe.jar")), blockchain.takamakaCode()));

		classpath = new Classpath(tictactoe, true);
		creator = blockchain.account(1);
		player1 = blockchain.account(2);
		player2 = blockchain.account(3);
	}

	@Test @DisplayName("new TicTacToe()")
	void createTicTacToe() throws TransactionException, CodeExecutionException {
		blockchain.addConstructorCallTransaction
			(new ConstructorCallTransactionRequest(creator, _200_000, BigInteger.ONE, classpath, CONSTRUCTOR_TIC_TAC_TOE));
	}

	@Test @DisplayName("new TicTacToe() then first player plays")
	void crossPlays() throws TransactionException, CodeExecutionException {
		StorageReference ticTacToe = blockchain.addConstructorCallTransaction
			(new ConstructorCallTransactionRequest(creator, _200_000, BigInteger.ONE, classpath, CONSTRUCTOR_TIC_TAC_TOE));
		blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
			player1, 
			_200_000,
			BigInteger.ONE,
			classpath,
			new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
			ticTacToe,
			new LongValue(100L),
			_1, _1));
		StringValue toString = (StringValue) blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
			player1, 
			_200_000,
			BigInteger.ONE,
			classpath,
			new NonVoidMethodSignature(TIC_TAC_TOE, "toString", ClassType.STRING),
			ticTacToe));

		assertEquals("X| | \n-----\n | | \n-----\n | | ", toString.value);
	}

	@Test @DisplayName("new TicTacToe(), first player plays, second player plays same position")
	void bothPlaySamePosition() throws TransactionException, CodeExecutionException {
		StorageReference ticTacToe = blockchain.addConstructorCallTransaction
			(new ConstructorCallTransactionRequest(creator, _200_000, BigInteger.ONE, classpath, CONSTRUCTOR_TIC_TAC_TOE));
		blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
			player1, 
			_200_000,
			BigInteger.ONE,
			classpath,
			new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
			ticTacToe,
			new LongValue(100L),
			_1, _1));

		throwsTransactionExceptionWithCause(Constants.REQUIREMENT_VIOLATION_EXCEPTION_NAME, () ->
			blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
				player2,
				_200_000,
				BigInteger.ONE,
				classpath,
				new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
				ticTacToe,
				new LongValue(100L),
				_1, _1))
		);
	}

	@Test @DisplayName("new TicTacToe(), same player plays twice")
	void samePlayerPlaysTwice() throws TransactionException, CodeExecutionException {
		StorageReference ticTacToe = blockchain.addConstructorCallTransaction
			(new ConstructorCallTransactionRequest(creator, _200_000, BigInteger.ONE, classpath, CONSTRUCTOR_TIC_TAC_TOE));
		blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
			player1, 
			_200_000,
			BigInteger.ONE,
			classpath,
			new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
			ticTacToe,
			new LongValue(100L),
			_1, _1));

		throwsTransactionExceptionWithCause(Constants.REQUIREMENT_VIOLATION_EXCEPTION_NAME, () ->
			blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
				player1,
				_200_000,
				BigInteger.ONE,
				classpath,
				new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
				ticTacToe,
				new LongValue(100L),
				_1, _2))
		);
	}

	@Test @DisplayName("new TicTacToe(), second player bets too little")
	void circleBetsTooLittle() throws TransactionException, CodeExecutionException {
		StorageReference ticTacToe = blockchain.addConstructorCallTransaction
			(new ConstructorCallTransactionRequest(creator, _200_000, BigInteger.ONE, classpath, CONSTRUCTOR_TIC_TAC_TOE));
		blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
			player1,
			_200_000,
			BigInteger.ONE,
			classpath,
			new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
			ticTacToe,
			new LongValue(120L),
			_1, _1));

		throwsTransactionExceptionWithCause(Constants.REQUIREMENT_VIOLATION_EXCEPTION_NAME, () ->
			blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
				player2,
				_200_000,
				BigInteger.ONE,
				classpath,
				new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
				ticTacToe,
				new LongValue(119L),
				_1, _2))
		);
	}

	@Test @DisplayName("first player wins")
	void crossWins() throws TransactionException, CodeExecutionException {
		StorageReference ticTacToe = blockchain.addConstructorCallTransaction
			(new ConstructorCallTransactionRequest(creator, _200_000, BigInteger.ONE, classpath, CONSTRUCTOR_TIC_TAC_TOE));
		blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
			player1, 
			_200_000,
			BigInteger.ONE,
			classpath,
			new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
			ticTacToe,
			new LongValue(100L),
			_1, _1));
		blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
			player2,
			_200_000,
			BigInteger.ONE,
			classpath,
			new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
			ticTacToe,
			new LongValue(100L),
			_2, _1));
		blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
			player1, 
			_200_000,
			BigInteger.ONE,
			classpath,
			new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
			ticTacToe,
			new LongValue(0L),
			_1, _2));
		blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
			player2,
			_200_000,
			BigInteger.ONE,
			classpath,
			new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
			ticTacToe,
			new LongValue(0L),
			_2, _2));
		blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
			player1, 
			_200_000,
			BigInteger.ONE,
			classpath,
			new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
			ticTacToe,
			new LongValue(0L),
			_1, _3));

		StringValue toString = (StringValue) blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
			player1, 
			_200_000,
			BigInteger.ONE,
			classpath,
			new NonVoidMethodSignature(TIC_TAC_TOE, "toString", ClassType.STRING),
			ticTacToe));

		assertEquals("X|O| \n-----\nX|O| \n-----\nX| | ", toString.value);
	}


	@Test @DisplayName("first player wins but second continues to play")
	void crossWinsButCircleContinues() throws TransactionException, CodeExecutionException {
		StorageReference ticTacToe = blockchain.addConstructorCallTransaction
			(new ConstructorCallTransactionRequest(creator, _200_000, BigInteger.ONE, classpath, CONSTRUCTOR_TIC_TAC_TOE));
		blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
			player1, 
			_200_000,
			BigInteger.ONE,
			classpath,
			new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
			ticTacToe,
			new LongValue(100L),
			_1, _1));
		blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
			player2,
			_200_000,
			BigInteger.ONE,
			classpath,
			new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
			ticTacToe,
			new LongValue(100L),
			_2, _1));
		blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
			player1, 
			_200_000,
			BigInteger.ONE,
			classpath,
			new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
			ticTacToe,
			new LongValue(0L),
			_1, _2));
		blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
			player2,
			_200_000,
			BigInteger.ONE,
			classpath,
			new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
			ticTacToe,
			new LongValue(0L),
			_2, _2));
		blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
			player1, 
			_200_000,
			BigInteger.ONE,
			classpath,
			new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
			ticTacToe,
			new LongValue(0L),
			_1, _3));

		throwsTransactionExceptionWithCause(Constants.REQUIREMENT_VIOLATION_EXCEPTION_NAME, () ->
			blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest(
				player2, 
				_200_000,
				BigInteger.ONE,
				classpath,
				new VoidMethodSignature(TIC_TAC_TOE, "play", LONG, INT, INT),
				ticTacToe,
				new LongValue(0L),
				_2, _3))
		);
	}
}