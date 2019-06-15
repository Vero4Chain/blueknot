/**
 * 
 */
package takamaka.tests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static takamaka.blockchain.types.BasicTypes.BOOLEAN;
import static takamaka.blockchain.types.BasicTypes.BYTE;
import static takamaka.blockchain.types.BasicTypes.INT;

import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import takamaka.blockchain.Classpath;
import takamaka.blockchain.CodeExecutionException;
import takamaka.blockchain.ConstructorSignature;
import takamaka.blockchain.MethodSignature;
import takamaka.blockchain.TransactionException;
import takamaka.blockchain.TransactionReference;
import takamaka.blockchain.request.ConstructorCallTransactionRequest;
import takamaka.blockchain.request.InstanceMethodCallTransactionRequest;
import takamaka.blockchain.request.JarStoreTransactionRequest;
import takamaka.blockchain.types.ClassType;
import takamaka.blockchain.values.BigIntegerValue;
import takamaka.blockchain.values.BooleanValue;
import takamaka.blockchain.values.ByteValue;
import takamaka.blockchain.values.IntValue;
import takamaka.blockchain.values.StorageReference;
import takamaka.lang.RequirementViolationException;
import takamaka.memory.InitializedMemoryBlockchain;

/**
 * A test for the blind auction contract.
 */
class BlindAuction {

	/**
	 * The number of bids placed by the players.
	 */
	private static final int NUM_BIDS = 100;

	/**
	 * The bidding time of the experiments (in milliseconds).
	 */
	private static final int BIDDING_TIME = 40_000;

	/**
	 * The reveal time of the experiments (in millisecond).
	 */
	private static final int REVEAL_TIME = 60_000;

	private static final BigInteger _1_000 = BigInteger.valueOf(1_000);

	private static final ClassType BLIND_AUCTION = new ClassType("takamaka.tests.auction.BlindAuction");

	private static final ConstructorSignature CONSTRUCTOR_BLIND_AUCTION = new ConstructorSignature(BLIND_AUCTION, INT, INT);

	private static final ConstructorSignature CONSTRUCTOR_BYTES32 = new ConstructorSignature
		(new ClassType("takamaka.util.Bytes32"),
			BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE,
			BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE,
			BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE,
			BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE, BYTE);

	private static final ConstructorSignature CONSTRUCTOR_STORAGE_LIST = new ConstructorSignature(ClassType.STORAGE_LIST);

	private static final ConstructorSignature CONSTRUCTOR_REVEALED_BID = new ConstructorSignature(new ClassType("takamaka.tests.auction.BlindAuction$RevealedBid"),
			ClassType.BIG_INTEGER, BOOLEAN, ClassType.BYTES32);

	private static final MethodSignature BID = new MethodSignature(BLIND_AUCTION, "bid", ClassType.BIG_INTEGER, ClassType.BYTES32);

	private static final MethodSignature REVEAL = new MethodSignature(BLIND_AUCTION, "reveal", ClassType.STORAGE_LIST);

	private static final MethodSignature AUCTION_END = new MethodSignature(BLIND_AUCTION, "auctionEnd");

	private static final MethodSignature GET_BALANCE = new MethodSignature(new ClassType("takamaka.lang.TestExternallyOwnedAccount"), "getBalance");
	
	private static final MethodSignature ADD = new MethodSignature(ClassType.STORAGE_LIST, "add", ClassType.OBJECT);

	private static final BigInteger _200_000 = BigInteger.valueOf(200_000);

	/**
	 * The blockchain under test. This is recreated before each test.
	 */
	private InitializedMemoryBlockchain blockchain;

	/**
	 * The classpath of the classes being tested.
	 */
	private Classpath classpath;

	/**
	 * The hashing algorithm used to hide the bids.
	 */
	private MessageDigest digest;

	@BeforeEach
	void beforeEach() throws Exception {
		digest = MessageDigest.getInstance("SHA-256");
		blockchain = new InitializedMemoryBlockchain(Paths.get("../takamaka_runtime/dist/takamaka_base.jar"), _200_000, _200_000, _200_000, _200_000);

		TransactionReference auctions = blockchain.addJarStoreTransaction
			(new JarStoreTransactionRequest(blockchain.account(0), _200_000, blockchain.takamakaBase,
			Files.readAllBytes(Paths.get("../takamaka_examples/dist/auctions.jar")), blockchain.takamakaBase));

		classpath = new Classpath(auctions, true);
	}

	@Test @DisplayName("three players put bids before end of bidding time")
	void bids() throws TransactionException, CodeExecutionException {
		StorageReference auction = blockchain.addConstructorCallTransaction
			(new ConstructorCallTransactionRequest(blockchain.account(0), _1_000, classpath, CONSTRUCTOR_BLIND_AUCTION, new IntValue(BIDDING_TIME), new IntValue(REVEAL_TIME)));

		Random random = new Random();
		for (int i = 1; i <= NUM_BIDS; i++) {
			int player = 1 + random.nextInt(3);
			BigInteger deposit = BigInteger.valueOf(random.nextInt(1000));
			BigInteger value = BigInteger.valueOf(random.nextInt(1000));
			boolean fake = random.nextBoolean();
			byte[] salt = new byte[32];
			random.nextBytes(salt);
			StorageReference bytes32 = codeAsBytes32(player, value, fake, salt);
        	blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(blockchain.account(player), _1_000, classpath, BID, auction, new BigIntegerValue(deposit), bytes32));
		}
	}

	@Test @DisplayName("three players put bids but bidding time expires")
	void biddingTimeExpires() throws TransactionException, CodeExecutionException {
		StorageReference auction = blockchain.addConstructorCallTransaction
				(new ConstructorCallTransactionRequest(blockchain.account(0), _1_000, classpath, CONSTRUCTOR_BLIND_AUCTION, new IntValue(4000), new IntValue(REVEAL_TIME)));

		try {
			Random random = new Random();
			for (int i = 1; i <= NUM_BIDS; i++) {
				int player = 1 + random.nextInt(3);
				BigInteger deposit = BigInteger.valueOf(random.nextInt(1000));
				BigInteger value = BigInteger.valueOf(random.nextInt(1000));
				boolean fake = random.nextBoolean();
				byte[] salt = new byte[32];
				random.nextBytes(salt);
				StorageReference bytes32 = codeAsBytes32(player, value, fake, salt);
				blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
						(blockchain.account(player), _1_000, classpath, BID, auction, new BigIntegerValue(deposit), bytes32));
				sleep(1000);
			}
		}
		catch (TransactionException e) {
			if (e.getCause() instanceof RequirementViolationException)
				return;

			fail("wrong exception");
		}

		fail("no exception");
	}

	/**
	 * Class used to keep in memory the bids placed by each player,
	 * that will be revealed at the end.
	 */
	private class BidToReveal {
		private final int player;
		private final BigInteger value;
		private final boolean fake;
		private final byte[] salt;

		private BidToReveal(int player, BigInteger value, boolean fake, byte[] salt) {
			this.player = player;
			this.value = value;
			this.fake = fake;
			this.salt = salt;
		}

		private StorageReference intoBlockchain() throws TransactionException, CodeExecutionException {
			return blockchain.addConstructorCallTransaction(new ConstructorCallTransactionRequest
        		(blockchain.account(player), _1_000, classpath, CONSTRUCTOR_REVEALED_BID, new BigIntegerValue(value), new BooleanValue(fake), createBytes32(player, salt)));
		}
	}

	@Test @DisplayName("three players put bids before end of bidding time then reveal")
	void bidsThenReveal() throws TransactionException, CodeExecutionException {
		StorageReference auction = blockchain.addConstructorCallTransaction
			(new ConstructorCallTransactionRequest(blockchain.account(0), _1_000, classpath, CONSTRUCTOR_BLIND_AUCTION, new IntValue(BIDDING_TIME), new IntValue(REVEAL_TIME)));

		long start = System.currentTimeMillis();
		List<BidToReveal> bids = new ArrayList<>();

		BigInteger maxBid = BigInteger.ZERO;
		StorageReference expectedWinner = null;
		Random random = new Random();
		for (int i = 1; i <= NUM_BIDS; i++) {
			int player = 1 + random.nextInt(3);
			BigInteger deposit = BigInteger.valueOf(random.nextInt(1000));
			BigInteger value = BigInteger.valueOf(random.nextInt(1000));
			boolean fake = random.nextBoolean();
			byte[] salt = new byte[32];
			random.nextBytes(salt);
			StorageReference bytes32 = codeAsBytes32(player, value, fake, salt);

			// we store the explicit bid in memory, not yet in blockchain, since it would be visible there
			bids.add(new BidToReveal(player, value, fake, salt));
        	blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(blockchain.account(player), _1_000, classpath, BID, auction, new BigIntegerValue(deposit), bytes32));

        	if (!fake && value.compareTo(maxBid) > 0) {
        		maxBid = value;
        		expectedWinner = blockchain.account(player);
        	}
		}

		waitUntil(BIDDING_TIME + 2000, start);

		// we create a storage list for each of the players
		StorageReference[] lists = {
			blockchain.addConstructorCallTransaction(new ConstructorCallTransactionRequest(blockchain.account(1), _1_000, classpath, CONSTRUCTOR_STORAGE_LIST)),
			blockchain.addConstructorCallTransaction(new ConstructorCallTransactionRequest(blockchain.account(2), _1_000, classpath, CONSTRUCTOR_STORAGE_LIST)),
			blockchain.addConstructorCallTransaction(new ConstructorCallTransactionRequest(blockchain.account(3), _1_000, classpath, CONSTRUCTOR_STORAGE_LIST))
		};

		// we create the revealed bids in blockchain; this is safe now, since the bidding time is over
		for (BidToReveal bid: bids)
			blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(blockchain.account(bid.player), _1_000, classpath, ADD, lists[bid.player - 1], bid.intoBlockchain()));

		for (int player = 1; player <= 3; player++)
			blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(blockchain.account(player), _200_000, classpath, REVEAL, auction, lists[player - 1]));

		waitUntil(BIDDING_TIME + REVEAL_TIME + 2000, start);

		StorageReference winner = (StorageReference) blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
			(blockchain.account(0), _1_000, classpath, AUCTION_END, auction));

		assertEquals(expectedWinner, winner);
	}

	private void waitUntil(long duration, long start) throws TransactionException, CodeExecutionException {
		while (System.currentTimeMillis() - start < duration) {
			sleep(100);
			// we need to perform dummy transactions, otherwise the blockchain time does not progress
			blockchain.addInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(blockchain.account(0), _1_000, classpath, GET_BALANCE, blockchain.account(0)));
		}
	}

	private StorageReference codeAsBytes32(int player, BigInteger value, boolean fake, byte[] salt) throws TransactionException, CodeExecutionException {
		digest.reset();
		digest.update(value.toByteArray());
		digest.update(fake ? (byte) 0 : (byte) 1);
		digest.update(salt);
		byte[] hash = digest.digest();
		return createBytes32(player, hash);
	}

	private StorageReference createBytes32(int player, byte[] hash) throws TransactionException, CodeExecutionException {
		return blockchain.addConstructorCallTransaction
			(new ConstructorCallTransactionRequest(blockchain.account(player), _1_000, classpath, CONSTRUCTOR_BYTES32,
				new ByteValue(hash[0]), new ByteValue(hash[1]), new ByteValue(hash[2]), new ByteValue(hash[3]),
				new ByteValue(hash[4]), new ByteValue(hash[5]), new ByteValue(hash[6]), new ByteValue(hash[7]),
				new ByteValue(hash[8]), new ByteValue(hash[9]), new ByteValue(hash[10]), new ByteValue(hash[11]),
				new ByteValue(hash[12]), new ByteValue(hash[13]), new ByteValue(hash[14]), new ByteValue(hash[15]),
				new ByteValue(hash[16]), new ByteValue(hash[17]), new ByteValue(hash[18]), new ByteValue(hash[19]),
				new ByteValue(hash[20]), new ByteValue(hash[21]), new ByteValue(hash[22]), new ByteValue(hash[23]),
				new ByteValue(hash[24]), new ByteValue(hash[25]), new ByteValue(hash[26]), new ByteValue(hash[27]),
				new ByteValue(hash[28]), new ByteValue(hash[29]), new ByteValue(hash[30]), new ByteValue(hash[31])));
	}

	private static void sleep(long milliseconds) {
		try {
			Thread.sleep(milliseconds);
		}
		catch (InterruptedException e) {}
	}
}