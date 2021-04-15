package io.hotmoka.local;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedList;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.hotmoka.beans.GasCostModel;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.NonInitialTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest;
import io.hotmoka.beans.responses.NonInitialTransactionResponse;
import io.hotmoka.beans.signatures.FieldSignature;
import io.hotmoka.beans.updates.ClassTag;
import io.hotmoka.beans.updates.Update;
import io.hotmoka.beans.updates.UpdateOfField;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.crypto.SignatureAlgorithm;
import io.hotmoka.local.internal.NodeInternal;
import io.hotmoka.local.internal.transactions.AbstractResponseBuilder;
import io.hotmoka.nodes.OutOfGasError;

/**
 * The creator of the response for a non-initial transaction. Non-initial transactions consume gas,
 * have a payer a nonce and a chain identifier and are signed. The constructor of this class checks
 * the validity of all these elements.
 */
public abstract class NonInitialResponseBuilder<Request extends NonInitialTransactionRequest<Response>, Response extends NonInitialTransactionResponse> extends AbstractResponseBuilder<Request, Response> {
	protected final static Logger logger = LoggerFactory.getLogger(NonInitialResponseBuilder.class);

	/**
	 * The cost model of the node for which the transaction is being built.
	 */
	protected final GasCostModel gasCostModel;

	private final static BigInteger _1_000_000 = BigInteger.valueOf(1_000_000L);

	/**
	 * Creates a the builder of the response.
	 * 
	 * @param reference the reference to the transaction that is building the response
	 * @param request the request of the transaction
	 * @param node the node that is creating the response
	 * @throws TransactionRejectedException if the builder cannot be built
	 */
	protected NonInitialResponseBuilder(TransactionReference reference, Request request, NodeInternal node) throws TransactionRejectedException {
		super(reference, request, node);

		try {
			this.gasCostModel = node.getGasCostModel();
			callerMustBeExternallyOwnedAccount();
			payerMustBeContract();
			gasLimitIsInsideBounds();
			requestPromisesEnoughGas();
			gasPriceIsLargeEnough();
			requestMustHaveCorrectChainId();
			signatureMustBeValid();
			callerAndRequestMustAgreeOnNonce();
			payerCanPayForAllPromisedGas();
		}
		catch (Throwable t) {
			throw wrapAsTransactionRejectedException(t);
		}
	}

	/**
	 * Creates a the builder of the response.
	 * 
	 * @param reference the reference to the transaction that is building the response
	 * @param request the request of the transaction
	 * @param node the node that is creating the response
	 * @throws TransactionRejectedException if the builder cannot be built
	 */
	protected NonInitialResponseBuilder(TransactionReference reference, Request request, AbstractLocalNode<?,?> node) throws TransactionRejectedException {
		this(reference, request, node.internal);
	}

	/**
	 * Determines if the transaction is signed.
	 * 
	 * @return true if and only if the request is signed and the transaction is not a view transaction
	 */
	protected boolean transactionIsSigned() {
		return !transactionIsView() && request instanceof SignedTransactionRequest;
	}

	@Override
	protected EngineClassLoader mkClassLoader() {
		return node.getCaches().getClassLoader(request.classpath);
	}

	/**
	 * Computes a minimal threshold of gas that is required for the transaction.
	 * Below this threshold, the response builder cannot be created.
	 * 
	 * @return the minimal threshold
	 */
	protected BigInteger minimalGasRequiredForTransaction() {
		BigInteger result = gasCostModel.cpuBaseTransactionCost();
		result = result.add(request.size(gasCostModel));
		result = result.add(gasForStoringFailedResponse());
		result = result.add(classLoader.getLengthsOfJars().mapToObj(gasCostModel::cpuCostForLoadingJar).reduce(ZERO, BigInteger::add));
		result = result.add(classLoader.getLengthsOfJars().mapToObj(gasCostModel::ramCostForLoadingJar).reduce(ZERO, BigInteger::add));
	
		return result;
	}

	/**
	 * Extracts the payer from the request. Normally, this is its caller,
	 * but subclasses might redefine.
	 * 
	 * @return the payer
	 */
	protected StorageReference getPayerFromRequest() {
		return request.caller;
	}

	/**
	 * Yields the cost for storage a failed response for the transaction that is being built.
	 * 
	 * @return the cost
	 */
	protected abstract BigInteger gasForStoringFailedResponse();

	/**
	 * Determines if the transaction is a view transaction.
	 * 
	 * @return true if and only if the transaction is a view transaction
	 */
	private boolean transactionIsView() {
		return this instanceof ViewResponseBuilder;
	}

	/**
	 * Determine the signature algorithm that must have been used for signing the request.
	 * This depends on the run-time class of the caller of the request.
	 * 
	 * @return the signature algorithm
	 * @throws NoSuchAlgorithmException if the needed signature algorithm is not available
	 * @throws ClassNotFoundException if the class of the caller cannot be found
	 */
	private SignatureAlgorithm<SignedTransactionRequest> determineSignatureAlgorithm() throws NoSuchAlgorithmException, ClassNotFoundException {
		ClassTag classTag = node.getClassTag(request.caller);
		Class<?> clazz = classLoader.loadClass(classTag.clazz.name);

		if (classLoader.getAccountED25519().isAssignableFrom(clazz))
			return SignatureAlgorithm.ed25519(SignedTransactionRequest::toByteArrayWithoutSignature);
		else if (classLoader.getAccountSHA256DSA().isAssignableFrom(clazz))
			return SignatureAlgorithm.sha256dsa(SignedTransactionRequest::toByteArrayWithoutSignature);
		else if (classLoader.getAccountQTESLA1().isAssignableFrom(clazz))
			return SignatureAlgorithm.qtesla1(SignedTransactionRequest::toByteArrayWithoutSignature);
		else if (classLoader.getAccountQTESLA3().isAssignableFrom(clazz))
			return SignatureAlgorithm.qtesla3(SignedTransactionRequest::toByteArrayWithoutSignature);
		else
			return consensus.getSignature();
	}

	/**
	 * Checks if the caller is an externally owned account or subclass.
	 *
	 * @throws TransactionRejectedException if the caller is not an externally owned account
	 * @throws ClassNotFoundException if the class of the caller cannot be determined
	 */
	private void callerMustBeExternallyOwnedAccount() throws TransactionRejectedException, ClassNotFoundException {
		ClassTag classTag = node.getClassTag(request.caller);
		Class<?> clazz = classLoader.loadClass(classTag.clazz.name);
		if (!classLoader.getExternallyOwnedAccount().isAssignableFrom(clazz))
			throw new TransactionRejectedException("the caller of a request must be an externally owned account");
	}

	/**
	 * Checks if the payer is a contract or subclass.
	 *
	 * @throws TransactionRejectedException if the payer is not a contract
	 * @throws ClassNotFoundException if the class of the payer cannot be determined
	 */
	private void payerMustBeContract() throws TransactionRejectedException, ClassNotFoundException {
		StorageReference payer = getPayerFromRequest();
	
		if (payer.equals(request.caller))
			// if the payer coincides with the caller, as it is normally the case,
			// then there is nothing to check, since we know that the caller
			// is an externally owned account, hence a contract
			return;
	
		// otherwise we check
		ClassTag classTag = node.getClassTag(payer);
		Class<?> clazz = classLoader.loadClass(classTag.clazz.name);
		if (!classLoader.getContract().isAssignableFrom(clazz))
			throw new TransactionRejectedException("the payer of a request must be a contract");
	}

	/**
	 * Checks that the request is signed with the private key of its caller.
	 * 
	 * @throws Exception if the signature of the request could not be checked
	 */
	private void signatureMustBeValid() throws Exception {
		if (transactionIsSigned() && !node.getCaches().signatureIsValid((SignedTransactionRequest) request, determineSignatureAlgorithm()))
			throw new TransactionRejectedException("invalid request signature");
	}

	/**
	 * Checks if the node has the same chain identifier as the request.
	 * 
	 * @throws TransactionRejectedException if the node and the request have different chain identifiers
	 */
	private void requestMustHaveCorrectChainId() throws TransactionRejectedException {
		// unsigned transactions do not check the chain identifier;
		// if the node is not initialized yet, the chain id is irrelevant
		if (transactionIsSigned() && node.getStoreUtilities().nodeIsInitializedUncommitted()) {
			String chainIdOfNode = consensus.chainId;
			String chainId = ((SignedTransactionRequest) request).getChainId();
			if (!chainIdOfNode.equals(chainId))
				throw new TransactionRejectedException("incorrect chain id: the request reports " + chainId + " but the node requires " + chainIdOfNode);
		}
	}

	/**
	 * Checks if the caller has the same nonce as the request.
	 * 
	 * @throws TransactionRejectedException if the nonce of the caller is not equal to that in {@code request}
	 */
	private void callerAndRequestMustAgreeOnNonce() throws TransactionRejectedException {
		// calls to @View methods do not check the nonce
		if (!transactionIsView()) {
			BigInteger expected = node.getStoreUtilities().getNonceUncommitted(request.caller);

			if (!expected.equals(request.nonce))
				throw new TransactionRejectedException("incorrect nonce: the request reports " + request.nonce
					+ " but the account " + request.caller + " contains " + expected);
		}
	}

	/**
	 * Checks that the request provides a minimal threshold of gas for starting the transaction.
	 * 
	 * @throws TransactionRejectedException if the request provides too little gas
	 */
	private void requestPromisesEnoughGas() throws TransactionRejectedException {
		BigInteger minimum = minimalGasRequiredForTransaction();
		if (request.gasLimit.compareTo(minimum) < 0)
			throw new TransactionRejectedException("not enough gas to start the transaction, expected at least " + minimum + " units of gas");
	}

	/**
	 * Checks that the gas of the request is between zero and the maximum in the configuration of the node.
	 * 
	 * @throws TransactionRejectedException if the gas is outside these bounds
	 */
	private void gasLimitIsInsideBounds() throws TransactionRejectedException {
		if (request.gasLimit.compareTo(ZERO) < 0)
			throw new TransactionRejectedException("the gas limit cannot be negative");

		BigInteger maxGas;

		// view requests have a fixed maximum gas, overriding what is specified in the consensus parameters
		if (transactionIsView())
			maxGas = node.getConfig().maxGasPerViewTransaction;
		else
			maxGas = consensus.maxGasPerTransaction;

		if (request.gasLimit.compareTo(maxGas) > 0)
			throw new TransactionRejectedException("the gas limit of the request is larger than the maximum allowed (" + request.gasLimit + " > " + maxGas + ")");
	}

	/**
	 * Checks that the gas price of the request is at least as large as the current gas price of the node.
	 * 
	 * @throws TransactionRejectedException if the gas price is smaller than the current gas price of the node
	 */
	private void gasPriceIsLargeEnough() throws TransactionRejectedException {
		// before initialization, the gas price is not yet available
		if (transactionIsSigned() && node.getStoreUtilities().nodeIsInitializedUncommitted() && !consensus.ignoresGasPrice) {
			BigInteger currentGasPrice = node.getCaches().getGasPrice().get();
			if (request.gasPrice.compareTo(currentGasPrice) < 0)
				throw new TransactionRejectedException("the gas price of the request is smaller than the current gas price (" + request.gasPrice + " < " + currentGasPrice + ")");
		}
	}

	/**
	 * Checks if the payer of the request has enough funds for paying for all gas promised
	 * (green and red coins together).
	 * 
	 * @throws TransactionRejectedException if the payer is not rich enough for that
	 */
	private void payerCanPayForAllPromisedGas() throws TransactionRejectedException {
		BigInteger cost = costOf(request.gasLimit);
		BigInteger totalBalance = node.getStoreUtilities().getTotalBalanceUncommitted(getPayerFromRequest());

		if (totalBalance.subtract(cost).signum() < 0)
			throw new TransactionRejectedException("the payer has not enough funds to buy " + request.gasLimit + " units of gas");
	}

	/**
	 * Computes the cost of the given units of gas.
	 * 
	 * @param gas the units of gas
	 * @return the cost, as {@code gas} times {@code gasPrice}
	 */
	private BigInteger costOf(BigInteger gas) {
		return gas.multiply(request.gasPrice);
	}

	protected abstract class ResponseCreator extends AbstractResponseBuilder<Request, Response>.ResponseCreator {

		/**
		 * The deserialized caller.
		 */
		private Object deserializedCaller;

		/**
		 * The deserialized payer.
		 */
		private Object deserializedPayer;

		/**
		 * The deserialized validators contract, if the node is already initialized.
		 */
		private Optional<Object> deserializedValidators;

		/**
		 * A stack of available gas. When a sub-computation is started
		 * with a subset of the available gas, the latter is taken away from
		 * the current available gas and pushed on top of this stack.
		 */
		private final LinkedList<BigInteger> oldGas = new LinkedList<>();

		/**
		 * The remaining amount of gas for the current transaction, not yet consumed.
		 */
		private BigInteger gas;

		/**
		 * The amount of gas consumed for CPU execution.
		 */
		private BigInteger gasConsumedForCPU = ZERO;

		/**
		 * The amount of gas consumed for RAM allocation.
		 */
		private BigInteger gasConsumedForRAM = ZERO;

		/**
		 * The amount of gas consumed for storage consumption.
		 */
		private BigInteger gasConsumedForStorage = ZERO;

		/**
		 * The amount of green coins that have been deduced at the beginning
		 * for paying the gas in full.
		 */
		private BigInteger greenInitiallyPaidForGas;

		/**
		 * The green balance of the payer with all promised gas paid.
		 * This will be the green balance if the transaction fails.
		 */
		private BigInteger greenBalanceOfPayerInCaseOfTransactionException;

		/**
		 * The red balance of the payer with all promised gas paid.
		 * This will be the red balance if the transaction fails.
		 */
		private BigInteger redBalanceOfPayerInCaseOfTransactionException;

		/**
		 * The initial green balance of the validators before the transaction.
		 */
		private BigInteger initialGreenBalanceOfValidators;

		/**
		 * The initial red balance of the validators before the transaction.
		 */
		private BigInteger initialRedBalanceOfValidators;

		protected ResponseCreator() throws TransactionRejectedException {
			try {
				this.gas = request.gasLimit;
			}
			catch (Throwable t) {
				logger.error("response creation rejected", t);
				throw wrapAsTransactionRejectedException(t);
			}
		}

		protected final void init() {
			this.deserializedCaller = deserializer.deserialize(request.caller);
			this.deserializedPayer = deserializedPayer();
			this.deserializedValidators = node.getCaches().getValidators().map(deserializer::deserialize);

			increaseNonceOfCaller();
			chargeGasForCPU(gasCostModel.cpuBaseTransactionCost());
			chargeGasForStorage(node.getRequestStorageCost(request));
			chargeGasForClassLoader();	
			this.greenInitiallyPaidForGas = chargePayerForAllGasPromised();
			this.greenBalanceOfPayerInCaseOfTransactionException = classLoader.getBalanceOf(deserializedPayer);
			this.redBalanceOfPayerInCaseOfTransactionException = classLoader.getRedBalanceOf(deserializedPayer);
			if (deserializedValidators.isPresent()) {
				this.initialGreenBalanceOfValidators = classLoader.getBalanceOf(deserializedValidators.get());
				this.initialRedBalanceOfValidators = classLoader.getRedBalanceOf(deserializedValidators.get());
			}
		}

		/**
		 * Yields the contract that pays for the transaction.
		 * This normally coincides with {@link #getDeserializedCaller()}
		 * but subclasses may redefine.
		 * 
		 * @return the payer for the transaction
		 */
		protected Object deserializedPayer() {
			return deserializedCaller;
		}

		/**
		 * Yields the deserialized caller of the transaction.
		 * 
		 * @return the deserialized caller
		 */
		protected final Object getDeserializedCaller() {
			return deserializedCaller;
		}

		/**
		 * Yields the contract that collects the validators of the node.
		 * After each transaction that consumes gas, the price of the gas is sent to this
		 * contract, that can later redistribute the reward to all validators.
		 * 
		 * @return the contract, inside the store of the node, if the node is already initialized
		 */
		protected final Optional<Object> getDeserializedValidators() {
			return deserializedValidators;
		}

		/**
		 * Yields the amount of gas consumed for CPU execution.
		 * 
		 * @return the amount of gas
		 */
		protected final BigInteger gasConsumedForCPU() {
			return gasConsumedForCPU;
		}

		/**
		 * Yields the amount of gas consumed for RAM allocation.
		 * 
		 * @return the amount of gas
		 */
		protected final BigInteger gasConsumedForRAM() {
			return gasConsumedForRAM;
		}

		/**
		 * Yields the amount of gas consumed for storage consumption.
		 * 
		 * @return the amount of gas
		 */
		protected final BigInteger gasConsumedForStorage() {
			return gasConsumedForStorage;
		}

		/**
		 * Yields the gas that would be paid if the transaction fails.
		 * 
		 * @return the gas for penalty, computed as the total initial gas minus
		 *         the gas already consumed for PCU, for RAM and for storage
		 */
		protected final BigInteger gasConsumedForPenalty() {
			return request.gasLimit.subtract(gasConsumedForCPU).subtract(gasConsumedForRAM).subtract(gasConsumedForStorage);
		}

		/**
		 * Reduces the remaining amount of gas. It performs a task at the end.
		 * 
		 * @param amount the amount of gas to consume
		 * @param forWhat the task performed at the end, for the amount of gas to consume
		 */
		private void charge(BigInteger amount, Consumer<BigInteger> forWhat) {
			if (amount.signum() < 0)
				throw new IllegalArgumentException("gas cannot increase");

			// gas can be negative only if it was initialized so; this special case is
			// used for the creation of the gamete, when gas should not be counted
			if (gas.signum() < 0)
				return;

			if (gas.compareTo(amount) < 0)
				// we report how much gas is missing
				throw new OutOfGasError();
		
			gas = gas.subtract(amount);
			forWhat.accept(amount);
		}

		/**
		 * Decreases the available gas by the given amount, for storage allocation.
		 * 
		 * @param amount the amount of gas to consume
		 */
		private void chargeGasForStorage(BigInteger amount) {
			charge(amount, x -> gasConsumedForStorage = gasConsumedForStorage.add(x));
		}

		/**
		 * Decreases the available gas for the given response, for storage allocation.
		 * 
		 * @param response the response
		 */
		protected final void chargeGasForStorageOf(Response response) {
			chargeGasForStorage(response.size(gasCostModel));
		}

		@Override
		public final void chargeGasForCPU(BigInteger amount) {
			charge(amount, x -> gasConsumedForCPU = gasConsumedForCPU.add(x));
		}

		@Override
		public final void chargeGasForRAM(BigInteger amount) {
			charge(amount, x -> gasConsumedForRAM = gasConsumedForRAM.add(x));
		}

		/**
		 * Charges gas proportional to the complexity of the class loader that has been created.
		 */
		protected final void chargeGasForClassLoader() {
			classLoader.getLengthsOfJars().mapToObj(gasCostModel::cpuCostForLoadingJar).forEach(this::chargeGasForCPU);
			classLoader.getLengthsOfJars().mapToObj(gasCostModel::ramCostForLoadingJar).forEach(this::chargeGasForRAM);
		}

		/**
		 * Collects all updates to the balance or nonce of the caller of the transaction
		 * or of the balance of the validators contract.
		 * 
		 * @return the updates
		 */
		protected final Stream<Update> updatesToBalanceOrNonceOfCallerOrValidators() {
			Stream<Object> objects;
			if (deserializedValidators.isPresent())
				objects = Stream.of(deserializedCaller, deserializedValidators.get());
			else
				objects = Stream.of(deserializedCaller);

			return updatesExtractor.extractUpdatesFrom(objects)
				.filter(this::isUpdateToBalanceOrNonceOfCallerOrToBalanceOfValidators);
		}

		/**
		 * Determines if the given update affects the balance or the nonce of the caller
		 * of the transaction or the balance of the validators contract of the node.
		 * Those are the only updates that are allowed during the execution of a view method.
		 * 
		 * @param update the update
		 * @return true if and only if that condition holds
		 */
		protected final boolean isUpdateToBalanceOrNonceOfCallerOrToBalanceOfValidators(Update update) {
			if (update instanceof UpdateOfField) {
				FieldSignature field = ((UpdateOfField) update).getField();
				if (update.object.equals(request.caller))
					return FieldSignature.BALANCE_FIELD.equals(field) || FieldSignature.RED_BALANCE_FIELD.equals(field)
						|| FieldSignature.EOA_NONCE_FIELD.equals(field);
				else {
					Optional<StorageReference> validators = node.getCaches().getValidators();
					if (validators.isPresent() && update.object.equals(validators.get()))
						return FieldSignature.BALANCE_FIELD.equals(field);
				}
			}

			return false;
		}

		/**
		 * Charge to the payer of the transaction all gas promised for the transaction.
		 * 
		 * @return the amount that has been subtracted from the green balance
		 */
		private BigInteger chargePayerForAllGasPromised() {
			BigInteger cost = costOf(request.gasLimit);

			BigInteger greenBalance = classLoader.getBalanceOf(deserializedPayer);
			BigInteger redBalance = classLoader.getRedBalanceOf(deserializedPayer);

			// we check first if the payer can pay with red coins only
			BigInteger newRedBalance = redBalance.subtract(cost);
			if (newRedBalance.signum() >= 0) {
				classLoader.setRedBalanceOf(deserializedPayer, newRedBalance);
				return ZERO;
			}
			else {
				// otherwise, its red coins are set to 0 and the remainder is paid with green coins
				classLoader.setRedBalanceOf(deserializedPayer, ZERO);
				classLoader.setBalanceOf(deserializedPayer, greenBalance.add(newRedBalance));
				return newRedBalance.negate();
			}
		}

		/**
		 * Pays back the remaining gas to the payer of the transaction.
		 */
		protected final void refundPayerForAllRemainingGas() {
			BigInteger refund = costOf(gas);
			BigInteger greenBalance = classLoader.getBalanceOf(deserializedPayer);

			// we pay back the green before
			if (refund.subtract(greenInitiallyPaidForGas).signum() <= 0)
				classLoader.setBalanceOf(deserializedPayer, greenBalance.add(refund));
			else {
				BigInteger redBalance = classLoader.getRedBalanceOf(deserializedPayer);
				classLoader.setBalanceOf(deserializedPayer, greenBalance.add(greenInitiallyPaidForGas));
				classLoader.setRedBalanceOf(deserializedPayer, redBalance.add(refund.subtract(greenInitiallyPaidForGas)));
			}
		}

		/**
		 * Sends to the validators contract the price of all gas consumed for the transaction.
		 * Later, this can be redistributed to the validators.
		 */
		protected final void sendAllConsumedGasToValidators() {
			deserializedValidators.ifPresent(_validators -> {
				BigInteger gas = gasConsumedForCPU().add(gasConsumedForRAM()).add(gasConsumedForStorage());
				gas = addInflation(gas);
				classLoader.setBalanceOf(_validators, classLoader.getBalanceOf(_validators).add(costOf(gas)));
			});
		}

		protected final void resetBalanceOfPayerToInitialValueMinusAllPromisedGas() {
			classLoader.setBalanceOf(deserializedPayer, greenBalanceOfPayerInCaseOfTransactionException);
			classLoader.setRedBalanceOf(deserializedPayer, redBalanceOfPayerInCaseOfTransactionException);
		}

		protected final void resetBalanceOfValidatorsToInitialValue() {
			deserializedValidators.ifPresent(_validators -> {
				classLoader.setBalanceOf(_validators, initialGreenBalanceOfValidators);
				classLoader.setRedBalanceOf(_validators, initialRedBalanceOfValidators);
			});
		}

		/**
		 * Sends to the validators contract the price of all gas consumed for the transaction,
		 * including that for penalty. Later, this can be redistributed to the validators.
		 */
		protected final void sendAllConsumedGasToValidatorsIncludingPenalty() {
			deserializedValidators.ifPresent(_validators -> {
				BigInteger gas = gasConsumedForCPU().add(gasConsumedForRAM()).add(gasConsumedForStorage()).add(gasConsumedForPenalty());
				gas = addInflation(gas);
				classLoader.setBalanceOf(_validators, classLoader.getBalanceOf(_validators).add(costOf(gas)));
			});
		}

		private BigInteger addInflation(BigInteger gas) {
			// consensus can be null only during the run transactions to reconstruct the same consensus
			// when a node is restarted; in that case, the actual final gas is irrelevant
			if (consensus != null)
				gas = gas.multiply(_1_000_000.add(BigInteger.valueOf(consensus.inflation)))
				         .divide(_1_000_000);

			return gas;
		}

		@Override
		public final <T> T withGas(BigInteger amount, Callable<T> what) throws Exception {
			chargeGasForCPU(amount);
			oldGas.addFirst(gas);
			gas = amount;
		
			try {
				return what.call();
			}
			finally {
				gas = gas.add(oldGas.removeFirst());
			}
		}

		/**
		 * Sets the nonce to the value successive to that in the request.
		 */
		private void increaseNonceOfCaller() {
			if (!transactionIsView())
				classLoader.setNonceOf(deserializedCaller, request.nonce.add(ONE));
		}
	}
}