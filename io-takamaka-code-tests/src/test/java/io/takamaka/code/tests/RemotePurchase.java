/**
 * 
 */
package io.takamaka.code.tests;

import static io.hotmoka.beans.types.BasicTypes.INT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.SignatureException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.hotmoka.beans.CodeExecutionException;
import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.signatures.VoidMethodSignature;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.values.IntValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.nodes.Node.Subscription;
import io.takamaka.code.constants.Constants;

/**
 * A test for the remote purchase contract.
 */
class RemotePurchase extends TakamakaTest {
	private static final BigInteger _10_000 = BigInteger.valueOf(10000);
	private static final ClassType PURCHASE = new ClassType("io.takamaka.tests.remotepurchase.Purchase");
	private static final String PURCHASE_CONFIRMED_NAME = PURCHASE.name + "$PurchaseConfirmed";
	private static final VoidMethodSignature CONFIRM_RECEIVED = new VoidMethodSignature(PURCHASE, "confirmReceived");
	private static final VoidMethodSignature CONFIRM_PURCHASED = new VoidMethodSignature(PURCHASE, "confirmPurchase", INT);
	private static final ConstructorSignature CONSTRUCTOR_PURCHASE = new ConstructorSignature("io.takamaka.tests.remotepurchase.Purchase", INT);

	/**
	 * The seller contract.
	 */
	private StorageReference seller;

	/**
	 * The buyer contract.
	 */
	private StorageReference buyer;

	@BeforeEach
	void beforeEach() throws Exception {
		setNode("remotepurchase.jar", BigInteger.valueOf(100_000_000L), BigInteger.valueOf(100_000_000L));
		seller = account(0);
		buyer = account(1);
	}

	@Test @DisplayName("new Purchase(21)")
	void oddDeposit() throws TransactionException, CodeExecutionException {
		throwsTransactionExceptionWithCause(Constants.REQUIREMENT_VIOLATION_EXCEPTION_NAME, () ->
			addConstructorCallTransaction(privateKey(0), seller, _10_000, BigInteger.ONE, jar(), CONSTRUCTOR_PURCHASE, new IntValue(21))
		);
	}

	@Test @DisplayName("new Purchase(20)")
	void evenDeposit() throws TransactionException, CodeExecutionException, TransactionRejectedException, InvalidKeyException, SignatureException {
		addConstructorCallTransaction(privateKey(0), seller, _10_000, BigInteger.ONE, jar(), CONSTRUCTOR_PURCHASE, new IntValue(20));
	}

	@Test @DisplayName("seller runs purchase = new Purchase(20); buyer runs purchase.confirmPurchase(18)")
	void buyerCheats() throws TransactionException, CodeExecutionException, TransactionRejectedException, InvalidKeyException, SignatureException {
		StorageReference purchase = addConstructorCallTransaction(privateKey(0), seller, _10_000, BigInteger.ONE, jar(), CONSTRUCTOR_PURCHASE, new IntValue(20));

		throwsTransactionExceptionWithCause(Constants.REQUIREMENT_VIOLATION_EXCEPTION_NAME, () ->
			addInstanceMethodCallTransaction(privateKey(1), buyer, _10_000, BigInteger.ONE, jar(), CONFIRM_PURCHASED, purchase, new IntValue(18))
		);
	}

	@Test @DisplayName("seller runs purchase = new Purchase(20); buyer runs purchase.confirmPurchase(18); no event is generated")
	void buyerCheatsNoEvent() throws TransactionException, CodeExecutionException, TransactionRejectedException, InvalidKeyException, SignatureException {
		StorageReference purchase = addConstructorCallTransaction(privateKey(0), seller, _10_000, BigInteger.ONE, jar(), CONSTRUCTOR_PURCHASE, new IntValue(20));

		AtomicBoolean ok = new AtomicBoolean(true);

		// the code of the smart contract uses events having the same contract as key
		try (Subscription subscription = originalView.subscribeToEvents(purchase, (key, event) -> ok.set(false))) {
			throwsTransactionExceptionWithCause(Constants.REQUIREMENT_VIOLATION_EXCEPTION_NAME, () ->
				addInstanceMethodCallTransaction(privateKey(1), buyer, _10_000, BigInteger.ONE, jar(), CONFIRM_PURCHASED, purchase, new IntValue(18))
			);
		}

		assertTrue(ok.get());
	}

	@Test @DisplayName("seller runs purchase = new Purchase(20); buyer runs purchase.confirmPurchase(20)")
	void buyerHonest() throws TransactionException, CodeExecutionException, TransactionRejectedException, InvalidKeyException, SignatureException {
		StorageReference purchase = addConstructorCallTransaction(privateKey(0), seller, _10_000, BigInteger.ONE,jar(), CONSTRUCTOR_PURCHASE, new IntValue(20));
		addInstanceMethodCallTransaction(privateKey(1), buyer, _10_000, BigInteger.ONE, jar(), CONFIRM_PURCHASED, purchase, new IntValue(20));
	}

	@Test @DisplayName("seller runs purchase = new Purchase(20); buyer runs purchase.confirmPurchase(20); a purchase event is generated")
	void buyerHonestConfirmationEvent() throws TransactionException, CodeExecutionException, TransactionRejectedException, InvalidKeyException, SignatureException {
		StorageReference purchase = addConstructorCallTransaction(privateKey(0), seller, _10_000, BigInteger.ONE,jar(), CONSTRUCTOR_PURCHASE, new IntValue(20));

		AtomicReference<StorageReference> ref = new AtomicReference<>();

		// the code of the smart contract uses events having the same contract as key
		try (Subscription subscription = originalView.subscribeToEvents(purchase, (__, event) -> ref.set(event))) {
			addInstanceMethodCallTransaction(privateKey(1), buyer, _10_000, BigInteger.ONE, jar(), CONFIRM_PURCHASED, purchase, new IntValue(20));

			// the event might take some time to be notified
			try {
				Thread.sleep(2000);
			}
			catch (InterruptedException e) {}
		}

		assertTrue(ref.get() != null);
		assertEquals(PURCHASE_CONFIRMED_NAME, originalView.getClassTag(ref.get()).className);
	}

	@Test @DisplayName("seller runs purchase = new Purchase(20); buyer runs purchase.confirmPurchase(20); a purchase event is generated, subscription without key")
	void buyerHonestConfirmationEventNoKey() throws TransactionException, CodeExecutionException, TransactionRejectedException, InvalidKeyException, SignatureException {
		StorageReference purchase = addConstructorCallTransaction(privateKey(0), seller, _10_000, BigInteger.ONE,jar(), CONSTRUCTOR_PURCHASE, new IntValue(20));

		AtomicReference<StorageReference> ref = new AtomicReference<>();

		// the use null to subscribe to all events
		try (Subscription subscription = originalView.subscribeToEvents(null, (__, event) -> ref.set(event))) {
			addInstanceMethodCallTransaction(privateKey(1), buyer, _10_000, BigInteger.ONE, jar(), CONFIRM_PURCHASED, purchase, new IntValue(20));

			// the event might take some time to be notified
			try {
				Thread.sleep(2000);
			}
			catch (InterruptedException e) {}
		}

		assertTrue(ref.get() != null);
		assertEquals(PURCHASE_CONFIRMED_NAME, originalView.getClassTag(ref.get()).className);
	}

	@Test @DisplayName("seller runs purchase = new Purchase(20); buyer runs purchase.confirmPurchase(20); subscription is closed and no purchase event is handled")
	void buyerHonestConfirmationEventSubscriptionClosed() throws TransactionException, CodeExecutionException, TransactionRejectedException, InvalidKeyException, SignatureException {
		StorageReference purchase = addConstructorCallTransaction(privateKey(0), seller, _10_000, BigInteger.ONE,jar(), CONSTRUCTOR_PURCHASE, new IntValue(20));

		AtomicBoolean ok = new AtomicBoolean(true);

		// the use null to subscribe to all events
		try (Subscription subscription = originalView.subscribeToEvents(null, (key, event) -> ok.set(false))) {			
		}

		// the subscription is closed now, hence the event generated below will not set ok to false
		addInstanceMethodCallTransaction(privateKey(1), buyer, _10_000, BigInteger.ONE, jar(), CONFIRM_PURCHASED, purchase, new IntValue(20));

		assertTrue(ok.get());
	}

	@Test @DisplayName("seller runs purchase = new Purchase(20); buyer runs purchase.confirmReceived()")
	void confirmReceptionBeforePaying() throws TransactionException, CodeExecutionException, TransactionRejectedException, InvalidKeyException, SignatureException {
		StorageReference purchase = addConstructorCallTransaction(privateKey(0), seller, _10_000, BigInteger.ONE, jar(), CONSTRUCTOR_PURCHASE, new IntValue(20));

		throwsTransactionExceptionWithCause(Constants.REQUIREMENT_VIOLATION_EXCEPTION_NAME, () ->
			addInstanceMethodCallTransaction(privateKey(1), buyer, _10_000, BigInteger.ONE, jar(), CONFIRM_RECEIVED, purchase)
		);
	}

	@Test @DisplayName("seller runs purchase = new Purchase(20); buyer runs purchase.confirmPurchase(20) and then purchase.confirmReceived()")
	void buyerPaysAndConfirmReception() throws TransactionException, CodeExecutionException, TransactionRejectedException, InvalidKeyException, SignatureException {
		StorageReference purchase = addConstructorCallTransaction(privateKey(0), seller, _10_000, BigInteger.ONE, jar(), CONSTRUCTOR_PURCHASE, new IntValue(20));
		addInstanceMethodCallTransaction(privateKey(1), buyer, _10_000, BigInteger.ONE, jar(), CONFIRM_PURCHASED, purchase, new IntValue(20));
		addInstanceMethodCallTransaction(privateKey(1), buyer, _10_000, BigInteger.ONE, jar(), CONFIRM_RECEIVED, purchase);
	}
}