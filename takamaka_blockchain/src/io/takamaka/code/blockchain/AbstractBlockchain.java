package io.takamaka.code.blockchain;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hotmoka.beans.references.Classpath;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.AbstractJarStoreTransactionRequest;
import io.hotmoka.beans.requests.ConstructorCallTransactionRequest;
import io.hotmoka.beans.requests.GameteCreationTransactionRequest;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.JarStoreInitialTransactionRequest;
import io.hotmoka.beans.requests.JarStoreTransactionRequest;
import io.hotmoka.beans.requests.NonInitialTransactionRequest;
import io.hotmoka.beans.requests.RedGreenGameteCreationTransactionRequest;
import io.hotmoka.beans.requests.StaticMethodCallTransactionRequest;
import io.hotmoka.beans.requests.TransactionRequest;
import io.hotmoka.beans.signatures.CodeSignature;
import io.hotmoka.beans.signatures.ConstructorSignature;
import io.hotmoka.beans.signatures.FieldSignature;
import io.hotmoka.beans.signatures.MethodSignature;
import io.hotmoka.beans.signatures.NonVoidMethodSignature;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.types.StorageType;
import io.hotmoka.beans.updates.ClassTag;
import io.hotmoka.beans.updates.Update;
import io.hotmoka.beans.updates.UpdateOfBalance;
import io.hotmoka.beans.updates.UpdateOfField;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StorageValue;
import io.takamaka.code.blockchain.internal.Deserializer;
import io.takamaka.code.blockchain.internal.Serializer;
import io.takamaka.code.blockchain.internal.SizeCalculator;
import io.takamaka.code.blockchain.internal.StorageTypeToClass;
import io.takamaka.code.blockchain.internal.TempJarFile;
import io.takamaka.code.blockchain.responses.ConstructorCallTransactionExceptionResponse;
import io.takamaka.code.blockchain.responses.ConstructorCallTransactionFailedResponse;
import io.takamaka.code.blockchain.responses.ConstructorCallTransactionResponse;
import io.takamaka.code.blockchain.responses.ConstructorCallTransactionSuccessfulResponse;
import io.takamaka.code.blockchain.responses.GameteCreationTransactionResponse;
import io.takamaka.code.blockchain.responses.JarStoreInitialTransactionResponse;
import io.takamaka.code.blockchain.responses.JarStoreTransactionFailedResponse;
import io.takamaka.code.blockchain.responses.JarStoreTransactionResponse;
import io.takamaka.code.blockchain.responses.JarStoreTransactionSuccessfulResponse;
import io.takamaka.code.blockchain.responses.MethodCallTransactionExceptionResponse;
import io.takamaka.code.blockchain.responses.MethodCallTransactionFailedResponse;
import io.takamaka.code.blockchain.responses.MethodCallTransactionResponse;
import io.takamaka.code.blockchain.responses.MethodCallTransactionSuccessfulResponse;
import io.takamaka.code.blockchain.responses.TransactionResponse;
import io.takamaka.code.blockchain.responses.TransactionResponseWithInstrumentedJar;
import io.takamaka.code.blockchain.responses.TransactionResponseWithUpdates;
import io.takamaka.code.blockchain.responses.VoidMethodCallTransactionSuccessfulResponse;
import io.takamaka.code.blockchain.runtime.Runtime;
import io.takamaka.code.instrumentation.InstrumentationConstants;
import io.takamaka.code.instrumentation.InstrumentedJar;
import io.takamaka.code.verification.Dummy;
import io.takamaka.code.verification.TakamakaClassLoader;
import io.takamaka.code.verification.VerifiedJar;
import io.takamaka.code.whitelisting.WhiteListingProofObligation;
import io.takamaka.code.whitelisting.WhiteListingWizard;

/**
 * A generic implementation of a blockchain. Specific implementations can subclass this class
 * and just implement the abstract template methods. The rest of code should work instead
 * as a generic layer for all blockchain implementations.
 */
public abstract class AbstractBlockchain implements Blockchain {

	/**
	 * The events accumulated during the current transaction. This is reset at each transaction.
	 */
	private final List<Object> events = new ArrayList<>();

	/**
	 * The gas cost model of this blockchain.
	 */
	private final GasCostModel gasCostModel = getGasCostModel();

	/**
	 * The object that knows about the size of data once stored in blockchain.
	 */
	private final SizeCalculator sizeCalculator = new SizeCalculator(gasCostModel);

	/**
	 * The object that serializes RAM values into storage objects.
	 */
	private final Serializer serializer = new Serializer(this);

	/**
	 * The object that deserializes storage objects into RAM values.
	 */
	private final Deserializer deserializer = new Deserializer(this, this::getLastEagerUpdatesFor);

	/**
	 * The object that translates storage types into their run-time class tag.
	 */
	private final StorageTypeToClass storageTypeToClass = new StorageTypeToClass(this);

	/**
	 * The remaining amount of gas for the current transaction, not yet consumed.
	 */
	private BigInteger gas;

	/**
	 * The amount of gas consumed for CPU execution.
	 */
	private BigInteger gasConsumedForCPU;

	/**
	 * The amount of gas consumed for RAM allocation.
	 */
	private BigInteger gasConsumedForRAM;

	/**
	 * The amount of gas consumed for storage consumption.
	 */
	private BigInteger gasConsumedForStorage;

	/**
	 * A stack of available gas. When a sub-computation is started
	 * with a subset of the available gas, the latter is taken away from
	 * the current available gas and pushed on top of this stack.
	 */
	private final LinkedList<BigInteger> oldGas = new LinkedList<>();

	/**
	 * The class loader for the transaction currently being executed.
	 */
	private BlockchainClassLoader classLoader;

	/**
	 * The reference to the transaction where this must be executed.
	 */
	private TransactionReference current;

	// ABSTRACT TEMPLATE METHODS
	// Any implementation of a blockchain must implement the following and leave the rest unchanged
	
	/**
	 * Yields a transaction reference whose {@code toString()} is the given string.
	 * 
	 * @param toString the result of {@code toString()} on the desired transaction reference
	 * @return the transaction reference
	 */
	protected abstract TransactionReference getTransactionReferenceFor(String toString);

	/**
	 * Yields the request that generated the given transaction.
	 * 
	 * @param transaction the reference to the transaction
	 * @return the request
	 * @throws Exception if the request could not be found
	 */
	protected abstract TransactionRequest getRequestAt(TransactionReference transaction) throws Exception;

	/**
	 * Yields the response that was generated by the given transaction.
	 * 
	 * @param transaction the reference to the transaction
	 * @return the response
	 * @throws Exception if the response could not be found
	 */
	protected abstract TransactionResponse getResponseAt(TransactionReference transaction) throws Exception;

	/**
	 * Yields the most recent eager updates for the given storage reference.
	 * 
	 * @param reference the storage reference
	 * @return the updates. These must include the class tag update for the reference
	 * @throws Exception if the updates cannot be found
	 */
	protected abstract Stream<Update> getLastEagerUpdatesFor(StorageReference reference) throws Exception;

	/**
	 * Yields the most recent update for the given non-{@code final} field,
	 * of lazy type, of the object at given storage reference.
	 * Conceptually, this amounts to scanning backwards the blockchain, from its tip,
	 * looking for the latest update.
	 * 
	 * @param object the storage reference
	 * @param field the field whose update is being looked for
	 * @return the update, if any
	 * @throws Exception if the update could not be found
	 */
	protected abstract UpdateOfField getLastLazyUpdateToNonFinalFieldOf(StorageReference object, FieldSignature field) throws Exception;

	/**
	 * Yields the most recent update for the given {@code final} field,
	 * of lazy type, of the object at given storage reference.
	 * Conceptually, this amounts to accessing the storage reference when the object was
	 * created and reading the value of the field there. Its implementation can be identical to
	 * that of {@link #getLastLazyUpdateToNonFinalFieldOf(StorageReference, FieldSignature)},
	 * or exploit the fact that the field is {@code final}, for an optimized look-up.
	 * 
	 * @param object the storage reference
	 * @param field the field whose update is being looked for
	 * @return the update, if any
	 * @throws Exception if the update could not be found
	 */
	protected abstract UpdateOfField getLastLazyUpdateToFinalFieldOf(StorageReference object, FieldSignature field) throws Exception;

	/**
	 * Yields the UTC time when the currently executing transaction is being run.
	 * This might be for instance the time of creation of the block where the transaction
	 * occurs, but the detail is left to the implementation. In any case, this
	 * time must be the same for a given transaction, if it gets executed more times.
	 * 
	 * @return the UTC time, as returned by {@link java.lang.System#currentTimeMillis()}
	 */
	public abstract long getNow();

	/**
	 * Yields the gas cost model of this blockchain.
	 * 
	 * @return the standard gas cost model. Subclasses may redefine
	 */
	protected GasCostModel getGasCostModel() {
		return GasCostModel.standard();
	}

	/**
	 * Initializes the state at the beginning of the execution of a new transaction
	 * 
	 * @param gas the amount of gas available for the transaction
	 * @param previous the transaction reference after which the transaction is being executed.
	 *                 If this is the first transaction, then {@code previous} will be {@code null}
	 * @param current the reference to the transaction where this must be executed
	 * @throws Exception if the transaction could not be initialized
	 */
	protected void initTransaction(BigInteger gas, TransactionReference current) throws Exception {
		Runtime.init(AbstractBlockchain.this); // this blockchain will be used during the execution of the code
		events.clear();
		deserializer.init();
		ClassType.clearCache();
		FieldSignature.clearCache();
		this.gas = gas;
		this.gasConsumedForCPU = BigInteger.ZERO;
		this.gasConsumedForRAM = BigInteger.ZERO;
		this.gasConsumedForStorage = BigInteger.ZERO;
		oldGas.clear();
		this.current = current;
	}

	/**
	 * Yields the reference to the transaction being executed.
	 * 
	 * @return the reference
	 */
	public final TransactionReference getCurrentTransaction() {
		return current;
	}

	// BLOCKCHAIN-AGNOSTIC IMPLEMENTATION

	/**
	 * Yields the request that generated the given transaction.
	 * 
	 * @param transaction the reference to the transaction
	 * @return the request
	 * @throws Exception if the request could not be found
	 */
	private TransactionRequest getRequestAtAndCharge(TransactionReference transaction) throws Exception {
		chargeForCPU(gasCostModel.cpuCostForGettingRequestAt(transaction));
		return getRequestAt(transaction);
	}

	/**
	 * Yields the class object that represents the given storage type in the Java language,
	 * for the current transaction.
	 * 
	 * @param type the storage type
	 * @return the class object, if any
	 * @throws ClassNotFoundException if some class type cannot be found
	 */
	public final Class<?> toClass(StorageType type) throws ClassNotFoundException {
		return storageTypeToClass.toClass(type);
	}

	/**
	 * Yields the response that generated the given transaction.
	 * 
	 * @param transaction the reference to the transaction
	 * @return the response
	 * @throws Exception if the response could not be found
	 */
	protected final TransactionResponse getResponseAtAndCharge(TransactionReference transaction) throws Exception {
		chargeForCPU(gasCostModel.cpuCostForGettingResponseAt(transaction));
		return getResponseAt(transaction);
	}

	/**
	 * Determines if a field of a storage class, having the given field, is eagerly loaded.
	 * 
	 * @param type the type
	 * @return true if and only if that condition holds
	 */
	public boolean isEagerlyLoaded(Class<?> type) {
		return classLoader.isEagerlyLoaded(type);
	}

	/**
	 * Determines if a field of a storage class, having the given field, is lazily loaded.
	 * 
	 * @param type the type
	 * @return true if and only if that condition holds
	 */
	public boolean isLazilyLoaded(Class<?> type) {
		return classLoader.isLazilyLoaded(type);
	}

	/**
	 * Yields the transaction reference that installed the jar
	 * where the given class is defined.
	 * 
	 * @param clazz the class
	 * @return the transaction reference
	 * @throws IllegalStateException if the transaction reference cannot be determined
	 */
	public final TransactionReference transactionThatInstalledJarFor(Class<?> clazz) {
		String className = clazz.getName();
		CodeSource src = clazz.getProtectionDomain().getCodeSource();
		if (src == null)
			throw new IllegalStateException("Cannot determine the jar of class "+ className);
		String classpath = src.getLocation().getPath();
		if (!classpath.endsWith(".jar"))
			throw new IllegalStateException("Unexpected class path " + classpath + " for class " + className);
		int start = classpath.lastIndexOf('@');
		if (start < 0)
			throw new IllegalStateException("Class path " + classpath + " misses @ separator");
		return getTransactionReferenceFor(classpath.substring(start + 1, classpath.length() - 4));
	}

	/**
	 * Decreases the available gas by the given amount, for CPU execution.
	 * 
	 * @param amount the amount of gas to consume
	 */
	public final void chargeForCPU(BigInteger amount) {
		if (amount.signum() < 0)
			throw new IllegalArgumentException("Gas cannot inrease");

		// gas can be negative only if it was initialized so; this special case is
		// used for the creation of the gamete, when gas should not be counted
		if (gas.signum() < 0)
			return;

		if (gas.compareTo(amount) < 0)
			// we report how much gas is missing
			throw new OutOfGasError();
	
		gas = gas.subtract(amount);
		gasConsumedForCPU = gasConsumedForCPU.add(amount);
	}

	/**
	 * Decreases the available gas by the given amount, for CPU execution.
	 * 
	 * @param amount the amount of gas to consume
	 */
	private void chargeForCPU(int amount) {
		chargeForCPU(BigInteger.valueOf(amount));
	}

	/**
	 * Decreases the available gas by the given amount, for RAM execution.
	 * 
	 * @param amount the amount of gas to consume
	 */
	public final void chargeForRAM(BigInteger amount) {
		if (amount.signum() < 0)
			throw new IllegalArgumentException("Gas cannot increase");

		// gas can be negative only if it was initialized so; this special case is
		// used for the creation of the gamete, when gas should not be counted
		if (gas.signum() < 0)
			return;

		if (gas.compareTo(amount) < 0)
			// we report how much gas is missing
			throw new OutOfGasError();
	
		gas = gas.subtract(amount);
		gasConsumedForRAM = gasConsumedForRAM.add(amount);
	}

	/**
	 * Decreases the available gas by the given amount, for storage allocation.
	 * 
	 * @param amount the amount of gas to consume
	 */
	public final void chargeForStorage(BigInteger amount) {
		if (amount.signum() < 0)
			throw new IllegalArgumentException("Gas cannot increase");

		// gas can be negative only if it was initialized so; this special case is
		// used for the creation of the gamete, when gas should not be counted
		if (gas.signum() < 0)
			return;

		if (gas.compareTo(amount) < 0)
			// we report how much gas is missing
			throw new OutOfGasError();
	
		gas = gas.subtract(amount);
		gasConsumedForStorage = gasConsumedForStorage.add(amount);
	}

	/**
	 * Runs a given piece of code with a subset of the available gas.
	 * It first charges the given amount of gas. Then runs the code
	 * with the charged gas only. At its end, the remaining gas is added
	 * to the available gas to continue the computation.
	 * 
	 * @param amount the amount of gas provided to the code
	 * @param what the code to run
	 * @return the result of the execution of the code
	 * @throws OutOfGasError if there is not enough gas
	 * @throws Exception if the code runs into this exception
	 */
	public final <T> T withGas(BigInteger amount, Callable<T> what) throws Exception {
		chargeForCPU(amount);
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
	 * Adds an event to those occurred during the execution of the current transaction.
	 * 
	 * @param event the event
	 * @throws IllegalArgumentException if the event is {@code null}
	 */
	public final void event(Object event) {
		if (event == null)
			throw new IllegalArgumentException("Events cannot be null");

		events.add(event);
	}

	@Override
	public final JarStoreInitialTransactionResponse runJarStoreInitialTransaction(JarStoreInitialTransactionRequest request, TransactionReference current) throws TransactionException {
		return wrapInCaseOfException(() -> {
			// we do not count gas for this transaction
			initTransaction(BigInteger.valueOf(-1L), current);

			// we transform the array of bytes into a real jar file
			try (TempJarFile original = new TempJarFile(request.getJar());
				 BlockchainClassLoader jarClassLoader = new BlockchainClassLoader(original.toPath(), request.getDependencies(), this)) {
				VerifiedJar verifiedJar = VerifiedJar.of(original.toPath(), jarClassLoader, true);
				InstrumentedJar instrumentedJar = InstrumentedJar.of(verifiedJar, gasModelAsForInstrumentation());
				return new JarStoreInitialTransactionResponse(instrumentedJar.toBytes());
			}
		});
	}

	@Override
	public final GameteCreationTransactionResponse runGameteCreationTransaction(GameteCreationTransactionRequest request, TransactionReference current) throws TransactionException {
		return wrapInCaseOfException(() -> {
			// we do not count gas for this creation
			initTransaction(BigInteger.valueOf(-1L), current);

			if (request.initialAmount.signum() < 0)
				throw new IllegalTransactionRequestException("The gamete must be initialized with a non-negative amount of coins");

			try (TakamakaClassLoader classLoader = this.classLoader = new BlockchainClassLoader(request.classpath, this)) {
				// we create an initial gamete ExternallyOwnedContract and we fund it with the initial amount
				Object gamete = classLoader.getExternallyOwnedAccount().getDeclaredConstructor().newInstance();
				// we set the balance field of the gamete
				Field balanceField = classLoader.getContract().getDeclaredField("balance");
				balanceField.setAccessible(true); // since the field is private
				balanceField.set(gamete, request.initialAmount);

				return new GameteCreationTransactionResponse(collectUpdates(null, null, null, gamete).stream(), getStorageReferenceOf(gamete));
			}
		});
	}

	@Override
	public final GameteCreationTransactionResponse runRedGreenGameteCreationTransaction(RedGreenGameteCreationTransactionRequest request, TransactionReference current) throws TransactionException {
		return wrapInCaseOfException(() -> {
			// we do not count gas for this creation
			initTransaction(BigInteger.valueOf(-1L), current);

			if (request.initialAmount.signum() < 0 || request.redInitialAmount.signum() < 0)
				throw new IllegalTransactionRequestException("The gamete must be initialized with a non-negative amount of coins");

			try (TakamakaClassLoader classLoader = this.classLoader = new BlockchainClassLoader(request.classpath, this)) {
				// we create an initial gamete RedGreenExternallyOwnedContract and we fund it with the initial amount
				Object gamete = classLoader.getRedGreenExternallyOwnedAccount().getDeclaredConstructor().newInstance();
				// we set the balance field of the gamete
				Field balanceField = classLoader.getContract().getDeclaredField("balance");
				balanceField.setAccessible(true); // since the field is private
				balanceField.set(gamete, request.initialAmount);

				// we set the red balance field of the gamete
				Field redBalanceField = classLoader.getRedGreenContract().getDeclaredField("balanceRed");
				redBalanceField.setAccessible(true); // since the field is private
				redBalanceField.set(gamete, request.redInitialAmount);
	
				return new GameteCreationTransactionResponse(collectUpdates(null, null, null, gamete).stream(), getStorageReferenceOf(gamete));
			}
		});
	}

	@Override
	public final JarStoreTransactionResponse runJarStoreTransaction(JarStoreTransactionRequest request, TransactionReference current) throws TransactionException {
		return wrapInCaseOfException(() -> {
			initTransaction(request.gas, current);

			try (BlockchainClassLoader classLoader = new BlockchainClassLoader(request.classpath, this)) {
				this.classLoader = classLoader;
				Object deserializedCaller = deserializer.deserialize(request.caller);
				checkIsExternallyOwned(deserializedCaller);

				// we sell all gas first: what remains will be paid back at the end;
				// if the caller has not enough to pay for the whole gas, the transaction won't be executed
				UpdateOfBalance balanceUpdateInCaseOfFailure = checkMinimalGas(request, deserializedCaller);

				// before this line, an exception will abort the transaction and leave the blockchain unchanged;
				// after this line, the transaction will be added to the blockchain, possibly as a failed one

				try {
					chargeForCPU(gasCostModel.cpuBaseTransactionCost());
					chargeForStorage(sizeCalculator.sizeOf(request));

					byte[] jar = request.getJar();
					chargeForCPU(gasCostModel.cpuCostForInstallingJar(jar.length));
					chargeForRAM(gasCostModel.ramCostForInstalling(jar.length));

					byte[] instrumentedBytes;
					// we transform the array of bytes into a real jar file
					try (TempJarFile original = new TempJarFile(jar);
						 BlockchainClassLoader jarClassLoader = new BlockchainClassLoader(original.toPath(), request.getDependencies(), this)) {
						VerifiedJar verifiedJar = VerifiedJar.of(original.toPath(), jarClassLoader, false);
						InstrumentedJar instrumentedJar = InstrumentedJar.of(verifiedJar, gasModelAsForInstrumentation());
						instrumentedBytes = instrumentedJar.toBytes();
					}

					BigInteger balanceOfCaller = increaseBalance(deserializedCaller, BigInteger.ZERO);
					StorageReference storageReferenceOfDeserializedCaller = getStorageReferenceOf(deserializedCaller);
					UpdateOfBalance balanceUpdate = new UpdateOfBalance(storageReferenceOfDeserializedCaller, balanceOfCaller);
					JarStoreTransactionResponse response = new JarStoreTransactionSuccessfulResponse(instrumentedBytes, balanceUpdate, gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
					chargeForStorage(sizeCalculator.sizeOf(response));
					balanceOfCaller = increaseBalance(deserializedCaller, remainingGas());
					balanceUpdate = new UpdateOfBalance(storageReferenceOfDeserializedCaller, balanceOfCaller);
					return new JarStoreTransactionSuccessfulResponse(instrumentedBytes, balanceUpdate, gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
				}
				catch (Throwable t) {
					// we do not pay back the gas
					BigInteger gasConsumedForPenalty = request.gas.subtract(gasConsumedForCPU).subtract(gasConsumedForStorage);
					return new JarStoreTransactionFailedResponse(wrapAsTransactionException(t, "Failed transaction"), balanceUpdateInCaseOfFailure, gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage, gasConsumedForPenalty);
				}
			}
		});
	}

	/**
	 * Yields an adapter of the gas cost model for instrumentation.
	 * 
	 * @return the adapted gas model
	 */
	private io.takamaka.code.instrumentation.GasCostModel gasModelAsForInstrumentation() {
		return new io.takamaka.code.instrumentation.GasCostModel() {

			@Override
			public int cpuCostOfArithmeticInstruction() {
				return gasCostModel.cpuCostOfArithmeticInstruction();
			}

			@Override
			public int cpuCostOfArrayAccessInstruction() {
				return gasCostModel.cpuCostOfArrayAccessInstruction();
			}

			@Override
			public int cpuCostOfFieldAccessInstruction() {
				return gasCostModel.cpuCostOfFieldAccessInstruction();
			}

			@Override
			public int cpuCostOfInstruction() {
				return gasCostModel.cpuCostOfInstruction();
			}

			@Override
			public int cpuCostOfInvokeInstruction() {
				return gasCostModel.cpuCostOfInvokeInstruction();
			}

			@Override
			public int cpuCostOfMemoryAllocationInstruction() {
				return gasCostModel.cpuCostOfMemoryAllocationInstruction();
			}

			@Override
			public int cpuCostOfSelectInstruction() {
				return gasCostModel.cpuCostOfSelectInstruction();
			}

			@Override
			public int ramCostOfActivationRecord() {
				return gasCostModel.ramCostOfActivationRecord();
			}

			@Override
			public int ramCostOfActivationSlot() {
				return gasCostModel.ramCostOfActivationSlot();
			}

			@Override
			public int ramCostOfArray() {
				return gasCostModel.ramCostOfArray();
			}

			@Override
			public int ramCostOfArraySlot() {
				return gasCostModel.ramCostOfArraySlot();
			}

			@Override
			public int ramCostOfField() {
				return gasCostModel.ramCostOfField();
			}

			@Override
			public int ramCostOfObject() {
				return gasCostModel.ramCostOfObject();
			}
		};
	}

	@Override
	public final ConstructorCallTransactionResponse runConstructorCallTransaction(ConstructorCallTransactionRequest request, TransactionReference current) throws TransactionException {
		return wrapInCaseOfException(() -> {
			initTransaction(request.gas, current);

			try (BlockchainClassLoader classLoader = new BlockchainClassLoader(request.classpath, this)) {
				this.classLoader = classLoader;
				Object deserializedCaller = deserializer.deserialize(request.caller);
				checkIsExternallyOwned(deserializedCaller);
				
				// we sell all gas first: what remains will be paid back at the end;
				// if the caller has not enough to pay for the whole gas, the transaction won't be executed
				UpdateOfBalance balanceUpdateInCaseOfFailure = checkMinimalGas(request, deserializedCaller);

				// before this line, an exception will abort the transaction and leave the blockchain unchanged;
				// after this line, the transaction can be added to the blockchain, possibly as a failed one

				try {
					chargeForCPU(gasCostModel.cpuBaseTransactionCost());
					chargeForStorage(sizeCalculator.sizeOf(request));

					CodeExecutor executor = new ConstructorExecutor(request.constructor, deserializedCaller, request.actuals());
					executor.start();
					executor.join();

					if (executor.exception instanceof InvocationTargetException) {
						ConstructorCallTransactionResponse response = new ConstructorCallTransactionExceptionResponse((Exception) executor.exception.getCause(), executor.updates(), events.stream().map(event -> getStorageReferenceOf(event)), gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
						chargeForStorage(sizeCalculator.sizeOf(response));
						increaseBalance(deserializedCaller, remainingGas());
						return new ConstructorCallTransactionExceptionResponse((Exception) executor.exception.getCause(), executor.updates(), events.stream().map(this::getStorageReferenceOf), gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
					}

					if (executor.exception != null)
						throw executor.exception;

					ConstructorCallTransactionResponse response = new ConstructorCallTransactionSuccessfulResponse
						((StorageReference) serializer.serialize(executor.result), executor.updates(), events.stream().map(this::getStorageReferenceOf), gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
					chargeForStorage(sizeCalculator.sizeOf(response));
					increaseBalance(deserializedCaller, remainingGas());
					return new ConstructorCallTransactionSuccessfulResponse
						((StorageReference) serializer.serialize(executor.result), executor.updates(), events.stream().map(this::getStorageReferenceOf), gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
				}
				catch (Throwable t) {
					// we do not pay back the gas: the only update resulting from the transaction is one that withdraws all gas from the balance of the caller
					BigInteger gasConsumedForPenalty = request.gas.subtract(gasConsumedForCPU).subtract(gasConsumedForRAM).subtract(gasConsumedForStorage);
					return new ConstructorCallTransactionFailedResponse(wrapAsTransactionException(t, "Failed transaction"), balanceUpdateInCaseOfFailure, gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage, gasConsumedForPenalty);
				}
			}
		});
	}

	@Override
	public final MethodCallTransactionResponse runInstanceMethodCallTransaction(InstanceMethodCallTransactionRequest request, TransactionReference current) throws TransactionException {
		return wrapInCaseOfException(() -> {
			initTransaction(request.gas, current);

			try (BlockchainClassLoader classLoader = new BlockchainClassLoader(request.classpath, this)) {
				this.classLoader = classLoader;
				Object deserializedCaller = deserializer.deserialize(request.caller);
				checkIsExternallyOwned(deserializedCaller);
				
				// we sell all gas first: what remains will be paid back at the end;
				// if the caller has not enough to pay for the whole gas, the transaction won't be executed
				UpdateOfBalance balanceUpdateInCaseOfFailure = checkMinimalGas(request, deserializedCaller);

				// before this line, an exception will abort the transaction and leave the blockchain unchanged;
				// after this line, the transaction can be added to the blockchain, possibly as a failed one

				try {
					chargeForCPU(gasCostModel.cpuBaseTransactionCost());
					chargeForStorage(sizeCalculator.sizeOf(request));

					InstanceMethodExecutor executor = new InstanceMethodExecutor(request.method, deserializedCaller, request.receiver, request.getActuals());
					executor.start();
					executor.join();

					if (executor.exception instanceof InvocationTargetException) {
						MethodCallTransactionResponse response = new MethodCallTransactionExceptionResponse((Exception) executor.exception.getCause(), executor.updates(), events.stream().map(event -> getStorageReferenceOf(event)), gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
						chargeForStorage(sizeCalculator.sizeOf(response));
						increaseBalance(deserializedCaller, remainingGas());
						return new MethodCallTransactionExceptionResponse((Exception) executor.exception.getCause(), executor.updates(), events.stream().map(event -> getStorageReferenceOf(event)), gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
					}

					if (executor.exception != null)
						throw executor.exception;

					if (executor.isViewMethod && !executor.onlyAffectedBalanceOf(deserializedCaller))
						throw new SideEffectsInViewMethodException((MethodSignature) executor.methodOrConstructor);

					if (executor.isVoidMethod) {
						MethodCallTransactionResponse response = new VoidMethodCallTransactionSuccessfulResponse(executor.updates(), events.stream().map(this::getStorageReferenceOf), gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
						chargeForStorage(sizeCalculator.sizeOf(response));
						increaseBalance(deserializedCaller, remainingGas());
						return new VoidMethodCallTransactionSuccessfulResponse(executor.updates(), events.stream().map(this::getStorageReferenceOf), gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
					}
					else {
						MethodCallTransactionResponse response = new MethodCallTransactionSuccessfulResponse
							(serializer.serialize(executor.result), executor.updates(), events.stream().map(this::getStorageReferenceOf), gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
						chargeForStorage(sizeCalculator.sizeOf(response));
						increaseBalance(deserializedCaller, remainingGas());
						return new MethodCallTransactionSuccessfulResponse
							(serializer.serialize(executor.result), executor.updates(), events.stream().map(this::getStorageReferenceOf), gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
					}
				}
				catch (Throwable t) {
					// we do not pay back the gas: the only update resulting from the transaction is one that withdraws all gas from the balance of the caller
					BigInteger gasConsumedForPenalty = request.gas.subtract(gasConsumedForCPU).subtract(gasConsumedForRAM).subtract(gasConsumedForStorage);
					return new MethodCallTransactionFailedResponse(wrapAsTransactionException(t, "Failed transaction"), balanceUpdateInCaseOfFailure, gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage, gasConsumedForPenalty);
				}
			}
		});
	}

	@Override
	public final MethodCallTransactionResponse runStaticMethodCallTransaction(StaticMethodCallTransactionRequest request, TransactionReference current) throws TransactionException {
		return wrapInCaseOfException(() -> {
			initTransaction(request.gas, current);

			try (BlockchainClassLoader classLoader = new BlockchainClassLoader(request.classpath, this)) {
				this.classLoader = classLoader;
				Object deserializedCaller = deserializer.deserialize(request.caller);
				checkIsExternallyOwned(deserializedCaller);
				
				// we sell all gas first: what remains will be paid back at the end;
				// if the caller has not enough to pay for the whole gas, the transaction won't be executed
				UpdateOfBalance balanceUpdateInCaseOfFailure = checkMinimalGas(request, deserializedCaller);

				// before this line, an exception will abort the transaction and leave the blockchain unchanged;
				// after this line, the transaction can be added to the blockchain, possibly as a failed one

				try {
					chargeForCPU(gasCostModel.cpuBaseTransactionCost());
					chargeForStorage(sizeCalculator.sizeOf(request));

					StaticMethodExecutor executor = new StaticMethodExecutor(request.method, deserializedCaller, request.getActuals());
					executor.start();
					executor.join();

					if (executor.exception instanceof InvocationTargetException) {
						MethodCallTransactionResponse response = new MethodCallTransactionExceptionResponse((Exception) executor.exception.getCause(), executor.updates(), events.stream().map(event -> getStorageReferenceOf(event)), gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
						chargeForStorage(sizeCalculator.sizeOf(response));
						increaseBalance(deserializedCaller, remainingGas());
						return new MethodCallTransactionExceptionResponse((Exception) executor.exception.getCause(), executor.updates(), events.stream().map(event -> getStorageReferenceOf(event)), gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
					}

					if (executor.exception != null)
						throw executor.exception;

					if (executor.isViewMethod && !executor.onlyAffectedBalanceOf(deserializedCaller))
						throw new SideEffectsInViewMethodException((MethodSignature) executor.methodOrConstructor);

					if (executor.isVoidMethod) {
						MethodCallTransactionResponse response = new VoidMethodCallTransactionSuccessfulResponse(executor.updates(), events.stream().map(event -> getStorageReferenceOf(event)), gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
						chargeForStorage(sizeCalculator.sizeOf(response));
						increaseBalance(deserializedCaller, remainingGas());
						return new VoidMethodCallTransactionSuccessfulResponse(executor.updates(), events.stream().map(this::getStorageReferenceOf), gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
					}
					else {
						MethodCallTransactionResponse response = new MethodCallTransactionSuccessfulResponse
							(serializer.serialize(executor.result), executor.updates(), events.stream().map(this::getStorageReferenceOf), gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
						chargeForStorage(sizeCalculator.sizeOf(response));
						increaseBalance(deserializedCaller, remainingGas());
						return new MethodCallTransactionSuccessfulResponse
							(serializer.serialize(executor.result), executor.updates(), events.stream().map(this::getStorageReferenceOf), gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);
					}
				}
				catch (Throwable t) {
					// we do not pay back the gas: the only update resulting from the transaction is one that withdraws all gas from the balance of the caller
					BigInteger gasConsumedForPenalty = request.gas.subtract(gasConsumedForCPU).subtract(gasConsumedForRAM).subtract(gasConsumedForStorage);
					return new MethodCallTransactionFailedResponse(wrapAsTransactionException(t, "Failed transaction"), balanceUpdateInCaseOfFailure, gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage, gasConsumedForPenalty);
				}
			}
		});
	}

	/**
	 * Yields the run-time class of the given object.
	 * 
	 * @param object the object
	 * @return the name of the class
	 * @throws DeserializationError if the class of the object cannot be found
	 */
	public final String getClassNameOf(StorageReference object) {
		try {
			TransactionResponse response = getResponseAt(object.transaction);
			if (response instanceof TransactionResponseWithUpdates) {
				Optional<ClassTag> classTag = ((TransactionResponseWithUpdates) response).getUpdates()
					.filter(update -> update instanceof ClassTag)
					.map(update -> (ClassTag) update)
					.findFirst();

				if (classTag.isPresent())
					return classTag.get().className;
			}
		}
		catch (DeserializationError e) {
			throw e;
		}
		catch (Exception e) {
			throw new DeserializationError(e);
		}

		throw new DeserializationError("Did not find the class tag for " + object);
	}

	// BLOCKCHAIN-AGNOSTIC IMPLEMENTATION
	
	/**
	 * Yields the latest value for the given field, of lazy type, of the given storage reference.
	 * The field is not {@code final}.
	 * Conceptually, this method goes backwards from the tip of the blockchain, looking for the latest
	 * update of the given field.
	 * 
	 * @param reference the storage reference
	 * @param field the field, of lazy type
	 * @return the value of the field
	 * @throws Exception if the look up fails
	 */
	public final Object deserializeLastLazyUpdateFor(StorageReference reference, FieldSignature field) throws Exception {
		return deserializer.deserialize(getLastLazyUpdateToNonFinalFieldOf(reference, field).getValue());
	}

	/**
	 * Yields the latest value for the given field, of lazy type, of the given storage reference.
	 * The field is {@code final}. Conceptually, this method looks for the value of the field
	 * in the transaction where the reference was created.
	 * 
	 * @param reference the storage reference
	 * @param field the field, of lazy type
	 * @return the value of the field
	 * @throws Exception if the look up fails
	 */
	public final Object deserializeLastLazyUpdateForFinal(StorageReference reference, FieldSignature field) throws Exception {
		return deserializer.deserialize(getLastLazyUpdateToFinalFieldOf(reference, field).getValue());
	}

	/**
	 * Yields the class with the given name for the current transaction.
	 * 
	 * @param name the name of the class
	 * @return the class, if any
	 * @throws ClassNotFoundException if the class cannot be found for the current transaction
	 */
	public final Class<?> loadClass(String name) throws ClassNotFoundException {
		return classLoader.loadClass(name);
	}

	/**
	 * Yields method {@link io.takamaka.code.lang.Contract#entry(io.takamaka.code.lang.Contract)}.
	 * 
	 * @return the method
	 */
	public final Method getEntry() {
		return classLoader.entry;
	}

	/**
	 * Yields method {@link io.takamaka.code.lang.Contract#payableEntry(io.takamaka.code.lang.Contract, int)}.
	 * 
	 * @return the method
	 */
	public final Method getPayableEntryInt() {
		return classLoader.payableEntryInt;
	}

	/**
	 * Yields method {@link io.takamaka.code.lang.Contract#payableEntry(io.takamaka.code.lang.Contract, long)}.
	 * 
	 * @return the method
	 */
	public final Method getPayableEntryLong() {
		return classLoader.payableEntryLong;
	}

	/**
	 * Yields method {@link io.takamaka.code.lang.Contract#payableEntry(io.takamaka.code.lang.Contract, java.math.BigInteger)}.
	 * 
	 * @return the method
	 */
	public final Method getPayableEntryBigInteger() {
		return classLoader.payableEntryBigInteger;
	}

	/**
	 * Yields method {@link io.takamaka.code.lang.RedGreenContract#redPayable(io.takamaka.code.lang.RedGreenContract, int)}.
	 * 
	 * @return the method
	 */
	public final Method getRedPayableInt() {
		return classLoader.redPayableInt;
	}

	/**
	 * Yields method {@link io.takamaka.code.lang.RedGreenContract#redPayable(io.takamaka.code.lang.RedGreenContract, long)}.
	 * 
	 * @return the method
	 */
	public final Method getRedPayableLong() {
		return classLoader.redPayableLong;
	}

	/**
	 * Yields method {@link io.takamaka.code.lang.RedGreenContract#redPayable(io.takamaka.code.lang.RedGreenContract, java.math.BigInteger)}.
	 * 
	 * @return the method
	 */
	public final Method getRedPayableBigInteger() {
		return classLoader.redPayableBigInteger;
	}

	/**
	 * Yields field {@link io.takamaka.code.lang.Storage#storageReference)}.
	 * 
	 * @return the field
	 */
	public final Field getStorageReferenceField() {
		return classLoader.storageReference;
	}

	/**
	 * Yields field {@link io.takamaka.code.lang.Storage#inStorage)}.
	 * 
	 * @return the field
	 */
	public final Field getInStorageField() {
		return classLoader.inStorage;
	}

	public boolean isStorage(Object object) {
		return object != null && classLoader.getStorage().isAssignableFrom(object.getClass());
	}

	public StorageReference getStorageReferenceOf(Object object) {
		try {
			return (StorageReference) classLoader.storageReference.get(object);
		}
		catch (IllegalArgumentException | IllegalAccessException e) {
			throw new IllegalStateException("cannot read the storage reference of a storage object of class " + object.getClass().getName());
		}
	}

	public boolean getInStorageOf(Object object) {
		try {
			return (boolean) classLoader.inStorage.get(object);
		}
		catch (IllegalArgumentException | IllegalAccessException e) {
			throw new IllegalStateException("cannot read the inStorage tag of a storage object of class " + object.getClass().getName());
		}
	}

	/**
	 * Checks if the caller of a transaction has enough money at least for paying the promised gas and the addition of a
	 * failed transaction response to blockchain.
	 * 
	 * @param request the request
	 * @param deserializedCaller the caller
	 * @return the update to the balance that would follow if the failed transaction request is added to the blockchain
	 * @throws IllegalTransactionRequestException if the caller has not enough money to buy the promised gas and the addition
	 *                                            of a failed transaction response to blockchain
	 * @throws ClassNotFoundException if the balance of the account cannot be correctly modified
	 * @throws NoSuchFieldException if the balance of the account cannot be correctly modified
	 * @throws SecurityException if the balance of the account cannot be correctly modified
	 * @throws IllegalArgumentException if the balance of the account cannot be correctly modified
	 * @throws IllegalAccessException if the balance of the account cannot be correctly modified
	 */
	private UpdateOfBalance checkMinimalGas(NonInitialTransactionRequest request, Object deserializedCaller) throws IllegalTransactionRequestException, ClassNotFoundException, NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
		BigInteger decreasedBalanceOfCaller = decreaseBalance(deserializedCaller, request.gas);
		UpdateOfBalance balanceUpdateInCaseOfFailure = new UpdateOfBalance(getStorageReferenceOf(deserializedCaller), decreasedBalanceOfCaller);

		if (gas.compareTo(minimalGasForRunning(request, balanceUpdateInCaseOfFailure)) < 0)
			throw new IllegalTransactionRequestException("Not enough gas to start the transaction");

		return balanceUpdateInCaseOfFailure;
	}

	private BigInteger minimalGasForRunning(NonInitialTransactionRequest request, UpdateOfBalance balanceUpdateInCaseOfFailure) throws IllegalTransactionRequestException {
		// we create a response whose size over-approximates that of a response in case of failure of this request
		if (request instanceof ConstructorCallTransactionRequest)
			return BigInteger.valueOf(gasCostModel.cpuBaseTransactionCost())
				.add(sizeCalculator.sizeOf(request))
				.add(sizeCalculator.sizeOf(new ConstructorCallTransactionFailedResponse(null, balanceUpdateInCaseOfFailure, gas, gas, gas, gas)));
		else if (request instanceof InstanceMethodCallTransactionRequest)
			return BigInteger.valueOf(gasCostModel.cpuBaseTransactionCost())
				.add(sizeCalculator.sizeOf(request))
				.add(sizeCalculator.sizeOf(new MethodCallTransactionFailedResponse(null, balanceUpdateInCaseOfFailure, gas, gas, gas, gas)));
		else if (request instanceof StaticMethodCallTransactionRequest)
			return BigInteger.valueOf(gasCostModel.cpuBaseTransactionCost())
				.add(sizeCalculator.sizeOf(request))
				.add(sizeCalculator.sizeOf(new MethodCallTransactionFailedResponse(null, balanceUpdateInCaseOfFailure, gas, gas, gas, gas)));
		else if (request instanceof JarStoreTransactionRequest)
			return BigInteger.valueOf(gasCostModel.cpuBaseTransactionCost())
				.add(sizeCalculator.sizeOf(request))
				.add(sizeCalculator.sizeOf(new JarStoreTransactionFailedResponse(null, balanceUpdateInCaseOfFailure, gas, gas, gas, gas)));
		else
			throw new IllegalTransactionRequestException("unexpected transaction request");
	}

	/**
	 * Calls the given callable. If if throws an exception, it wraps into into a {@link io.takamaka.code.blockchain.TransactionException}.
	 * 
	 * @param what the callable
	 * @return the result of the callable
	 * @throws TransactionException the wrapped exception
	 */
	protected static <T> T wrapInCaseOfException(Callable<T> what) throws TransactionException {
		try {
			return what.call();
		}
		catch (Throwable t) {
			throw wrapAsTransactionException(t, "Cannot complete the transaction");
		}
	}

	/**
	 * A class loader used to access the definition of the classes
	 * of Takamaka methods or constructors executed during a transaction.
	 */
	static class BlockchainClassLoader implements TakamakaClassLoader {

		/**
		 * The parent of this class loader;
		 */
		private final TakamakaClassLoader parent;

		/**
		 * The temporary files that hold the class path for a transaction.
		 */
		private final List<Path> classpathElements = new ArrayList<>();

		/**
		 * Method {@link io.takamaka.code.lang.Contract#entry(io.takamaka.code.lang.Contract)}.
		 */
		private final Method entry;

		/**
		 * Method {@link io.takamaka.code.lang.Contract#payableEntry(io.takamaka.code.lang.Contract, int)}.
		 */
		private final Method payableEntryInt;

		/**
		 * Method {@link io.takamaka.code.lang.Contract#payableEntry(io.takamaka.code.lang.Contract, long)}.
		 */
		private final Method payableEntryLong;

		/**
		 * Method {@link io.takamaka.code.lang.Contract#payableEntry(io.takamaka.code.lang.Contract, BigInteger)}.
		 */
		private final Method payableEntryBigInteger;

		/**
		 * Method {@link io.takamaka.code.lang.RedGreenContract#redPayable(io.takamaka.code.lang.RedGreenContract, int)}.
		 */
		private final Method redPayableInt;

		/**
		 * Method {@link io.takamaka.code.lang.RedGreenContract#redPayable(io.takamaka.code.lang.RedGreenContract, long)}.
		 */
		private final Method redPayableLong;

		/**
		 * Method {@link io.takamaka.code.lang.RedGreenContract#redPayable(io.takamaka.code.lang.RedGreenContract, BigInteger)}.
		 */
		private final Method redPayableBigInteger;

		/**
		 * The field {@link io.takamaka.code.lang.Storage#storageReference}.
		 */
		private final Field storageReference;

		/**
		 * The field {@link io.takamaka.code.lang.Storage#inStorage}.
		 */
		private final Field inStorage;

		/**
		 * Builds the class loader for the given class path and its dependencies.
		 * 
		 * @param classpath the class path
		 * @throws Exception if an error occurs
		 */
		private BlockchainClassLoader(Classpath classpath, AbstractBlockchain blockchain) throws Exception {
			this.parent = TakamakaClassLoader.of(collectURLs(Stream.of(classpath), blockchain, null));

			getOrigins().forEach(url -> {
				try {
					classpathElements.add(Paths.get(url.toURI()));
				}
				catch (URISyntaxException e) {
					throw new IllegalStateException("Unexpected illegal URL", e);
				}
			});

			this.entry = getContract().getDeclaredMethod("entry", getContract());
			this.entry.setAccessible(true); // it was private
			this.payableEntryInt = getContract().getDeclaredMethod("payableEntry", getContract(), int.class);
			this.payableEntryInt.setAccessible(true); // it was private
			this.payableEntryLong = getContract().getDeclaredMethod("payableEntry", getContract(), long.class);
			this.payableEntryLong.setAccessible(true); // it was private
			this.payableEntryBigInteger = getContract().getDeclaredMethod("payableEntry", getContract(), BigInteger.class);
			this.payableEntryBigInteger.setAccessible(true); // it was private
			this.redPayableInt = getRedGreenContract().getDeclaredMethod("redPayable", getRedGreenContract(), int.class);
			this.redPayableInt.setAccessible(true); // it was private
			this.redPayableLong = getRedGreenContract().getDeclaredMethod("redPayable", getRedGreenContract(), long.class);
			this.redPayableLong.setAccessible(true); // it was private
			this.redPayableBigInteger = getRedGreenContract().getDeclaredMethod("redPayable", getRedGreenContract(), BigInteger.class);
			this.redPayableBigInteger.setAccessible(true); // it was private
			this.storageReference = loadClass(io.takamaka.code.constants.Constants.STORAGE_NAME).getDeclaredField(InstrumentationConstants.STORAGE_REFERENCE_FIELD_NAME);
			this.storageReference.setAccessible(true); // it was private
			this.inStorage = loadClass(io.takamaka.code.constants.Constants.STORAGE_NAME).getDeclaredField(InstrumentationConstants.IN_STORAGE);
			this.inStorage.setAccessible(true); // it was private
		}

		/**
		 * Builds the class loader for the given jar and its dependencies.
		 * 
		 * @param jar the jar
		 * @param dependencies the dependencies
		 * @throws Exception if an error occurs
		 */
		private BlockchainClassLoader(Path jar, Stream<Classpath> dependencies, AbstractBlockchain blockchain) throws Exception {
			this.parent = TakamakaClassLoader.of(collectURLs(dependencies, blockchain, jar.toUri().toURL()));

			getOrigins().forEach(url -> {
				try {
					classpathElements.add(Paths.get(url.toURI()));
				}
				catch (URISyntaxException e) {
					throw new IllegalStateException("Unexpected illegal URL", e);
				}
			});

			this.entry = getContract().getDeclaredMethod("entry", getContract());
			this.entry.setAccessible(true); // it was private
			this.payableEntryInt = getContract().getDeclaredMethod("payableEntry", getContract(), int.class);
			this.payableEntryInt.setAccessible(true); // it was private
			this.payableEntryLong = getContract().getDeclaredMethod("payableEntry", getContract(), long.class);
			this.payableEntryLong.setAccessible(true); // it was private
			this.payableEntryBigInteger = getContract().getDeclaredMethod("payableEntry", getContract(), BigInteger.class);
			this.payableEntryBigInteger.setAccessible(true); // it was private
			this.redPayableInt = getRedGreenContract().getDeclaredMethod("redPayable", getRedGreenContract(), int.class);
			this.redPayableInt.setAccessible(true); // it was private
			this.redPayableLong = getRedGreenContract().getDeclaredMethod("redPayable", getRedGreenContract(), long.class);
			this.redPayableLong.setAccessible(true); // it was private
			this.redPayableBigInteger = getRedGreenContract().getDeclaredMethod("redPayable", getRedGreenContract(), BigInteger.class);
			this.redPayableBigInteger.setAccessible(true); // it was private
			this.storageReference = loadClass(io.takamaka.code.constants.Constants.STORAGE_NAME).getDeclaredField(InstrumentationConstants.STORAGE_REFERENCE_FIELD_NAME);
			this.storageReference.setAccessible(true); // it was private
			this.inStorage = loadClass(io.takamaka.code.constants.Constants.STORAGE_NAME).getDeclaredField(InstrumentationConstants.IN_STORAGE);
			this.inStorage.setAccessible(true); // it was private
		}

		private static URL[] collectURLs(Stream<Classpath> classpaths, AbstractBlockchain blockchain, URL start) throws Exception {
			List<URL> urls = new ArrayList<>();
			if (start != null)
				urls.add(start);

			for (Classpath classpath: classpaths.toArray(Classpath[]::new))
				urls = addURLs(classpath, blockchain, urls);

			return urls.toArray(new URL[urls.size()]);
		}

		private static List<URL> addURLs(Classpath classpath, AbstractBlockchain blockchain, List<URL> bag) throws Exception {
			// if the class path is recursive, we consider its dependencies as well, recursively
			if (classpath.recursive) {
				TransactionRequest request = blockchain.getRequestAtAndCharge(classpath.transaction);
				if (!(request instanceof AbstractJarStoreTransactionRequest))
					throw new IllegalTransactionRequestException("classpath does not refer to a jar store transaction");

				Stream<Classpath> dependencies = ((AbstractJarStoreTransactionRequest) request).getDependencies();
				for (Classpath dependency: dependencies.toArray(Classpath[]::new))
					addURLs(dependency, blockchain, bag);
			}

			TransactionResponse response = blockchain.getResponseAtAndCharge(classpath.transaction);
			if (!(response instanceof TransactionResponseWithInstrumentedJar))
				throw new IllegalTransactionRequestException("classpath does not refer to a successful jar store transaction");

			byte[] instrumentedJarBytes = ((TransactionResponseWithInstrumentedJar) response).getInstrumentedJar();
			blockchain.chargeForCPU(blockchain.gasCostModel.cpuCostForLoadingJar(instrumentedJarBytes.length));
			blockchain.chargeForRAM(blockchain.gasCostModel.ramCostForLoading(instrumentedJarBytes.length));

			try (InputStream is = new BufferedInputStream(new ByteArrayInputStream(instrumentedJarBytes))) {
				Path classpathElement = Files.createTempFile("takamaka_", "@" + classpath.transaction + ".jar");
				Files.copy(is, classpathElement, StandardCopyOption.REPLACE_EXISTING);

				// we add, for class loading, the jar containing the instrumented code
				bag.add(classpathElement.toFile().toURI().toURL());
			}

			return bag;
		}

		@Override
		public void close() throws IOException {
			// we delete all paths elements that were used to build this class loader
			for (Path classpathElement: classpathElements)
				Files.deleteIfExists(classpathElement);

			parent.close();
		}

		@Override
		public final Class<?> loadClass(String className) throws ClassNotFoundException {
			return parent.loadClass(className);
		}

		@Override
		public Stream<URL> getOrigins() {
			return parent.getOrigins();
		}

		@Override
		public WhiteListingWizard getWhiteListingWizard() {
			return parent.getWhiteListingWizard();
		}

		@Override
		public Optional<Field> resolveField(String className, String name, Class<?> type) throws ClassNotFoundException {
			return parent.resolveField(className, name, type);
		}

		@Override
		public Optional<Field> resolveField(Class<?> clazz, String name, Class<?> type) {
			return parent.resolveField(clazz, name, type);
		}

		@Override
		public Optional<Constructor<?>> resolveConstructor(String className, Class<?>[] args) throws ClassNotFoundException {
			return parent.resolveConstructor(className, args);
		}

		@Override
		public Optional<Constructor<?>> resolveConstructor(Class<?> clazz, Class<?>[] args) {
			return parent.resolveConstructor(clazz, args);
		}

		@Override
		public Optional<Method> resolveMethod(String className, String methodName, Class<?>[] args, Class<?> returnType) throws ClassNotFoundException {
			return parent.resolveMethod(className, methodName, args, returnType);
		}

		@Override
		public Optional<Method> resolveMethod(Class<?> clazz, String methodName, Class<?>[] args, Class<?> returnType) {
			return parent.resolveMethod(clazz, methodName, args, returnType);
		}

		@Override
		public Optional<Method> resolveInterfaceMethod(String className, String methodName, Class<?>[] args, Class<?> returnType) throws ClassNotFoundException {
			return parent.resolveInterfaceMethod(className, methodName, args, returnType);
		}

		@Override
		public Optional<Method> resolveInterfaceMethod(Class<?> clazz, String methodName, Class<?>[] args, Class<?> returnType) {
			return parent.resolveInterfaceMethod(clazz, methodName, args, returnType);
		}

		@Override
		public boolean isStorage(String className) {
			return parent.isStorage(className);
		}

		@Override
		public boolean isContract(String className) {
			return parent.isContract(className);
		}

		@Override
		public boolean isRedGreenContract(String className) {
			return parent.isRedGreenContract(className);
		}

		@Override
		public boolean isInterface(String className) {
			return parent.isInterface(className);
		}

		@Override
		public boolean isLazilyLoaded(Class<?> type) {
			return parent.isLazilyLoaded(type);
		}

		@Override
		public boolean isEagerlyLoaded(Class<?> type) {
			return parent.isEagerlyLoaded(type);
		}

		@Override
		public Class<?> getContract() {
			return parent.getContract();
		}

		@Override
		public Class<?> getRedGreenContract() {
			return parent.getRedGreenContract();
		}

		@Override
		public Class<?> getStorage() {
			return parent.getStorage();
		}

		@Override
		public Class<?> getExternallyOwnedAccount() {
			return parent.getExternallyOwnedAccount();
		}

		@Override
		public Class<?> getRedGreenExternallyOwnedAccount() {
			return parent.getRedGreenExternallyOwnedAccount();
		}

		@Override
		public ClassLoader getJavaClassLoader() {
			return parent.getJavaClassLoader();
		}
	}

	/**
	 * Sells the given amount of gas to the given externally owned account.
	 * 
	 * @param eoa the reference to the externally owned account
	 * @param gas the gas to sell
	 * @return the balance of the contract after paying the given amount of gas
	 * @throws IllegalTransactionRequestException if the externally owned account does not have funds
	 *                                            for buying the given amount of gas
	 * @throws ClassNotFoundException if the balance of the account cannot be correctly modified
	 * @throws NoSuchFieldException if the balance of the account cannot be correctly modified
	 * @throws SecurityException if the balance of the account cannot be correctly modified
	 * @throws IllegalArgumentException if the balance of the account cannot be correctly modified
	 * @throws IllegalAccessException if the balance of the account cannot be correctly modified
	 */
	private BigInteger decreaseBalance(Object eoa, BigInteger gas)
			throws IllegalTransactionRequestException, ClassNotFoundException, NoSuchFieldException,
			SecurityException, IllegalArgumentException, IllegalAccessException {
	
		BigInteger delta = gasCostModel.toCoin(gas);
		Field balanceField = classLoader.getContract().getDeclaredField("balance");
		balanceField.setAccessible(true); // since the field is private
		BigInteger previousBalance = (BigInteger) balanceField.get(eoa);
		if (previousBalance.compareTo(delta) < 0)
			throw new IllegalTransactionRequestException("Caller has not enough funds to buy " + gas + " units of gas");

		BigInteger result = previousBalance.subtract(delta);
		balanceField.set(eoa, result);
		return result;
	}

	/**
	 * Buys back the given amount of gas from the given externally owned account.
	 * 
	 * @param eoa the reference to the externally owned account
	 * @param gas the gas to buy back
	 * @return the balance of the contract after buying back the given amount of gas
	 * @throws ClassNotFoundException if the balance of the account cannot be correctly modified
	 * @throws NoSuchFieldException if the balance of the account cannot be correctly modified
	 * @throws SecurityException if the balance of the account cannot be correctly modified
	 * @throws IllegalArgumentException if the balance of the account cannot be correctly modified
	 * @throws IllegalAccessException if the balance of the account cannot be correctly modified
	 */
	private BigInteger increaseBalance(Object eoa, BigInteger gas)
			throws ClassNotFoundException, NoSuchFieldException,
			SecurityException, IllegalArgumentException, IllegalAccessException {
	
		BigInteger delta = gasCostModel.toCoin(gas);
		Field balanceField = classLoader.getContract().getDeclaredField("balance");
		balanceField.setAccessible(true); // since the field is private
		BigInteger previousBalance = (BigInteger) balanceField.get(eoa);
		BigInteger result = previousBalance.add(delta);
		balanceField.set(eoa, result);
		return result;
	}

	/**
	 * Checks if the given object is an externally owned account or subclass.
	 * 
	 * @param object the object to check
	 * @throws IllegalTransactionRequestException if the object is not an externally owned account
	 */
	private void checkIsExternallyOwned(Object object) throws ClassNotFoundException, IllegalTransactionRequestException {
		Class<? extends Object> clazz = object.getClass();
		if (!classLoader.getExternallyOwnedAccount().isAssignableFrom(clazz)
				&& !classLoader.getRedGreenExternallyOwnedAccount().isAssignableFrom(clazz))
			throw new IllegalTransactionRequestException("Only an externally owned contract can start a transaction");
	}

	/**
	 * Checks if the given object is a red/green externally owned account or subclass.
	 * 
	 * @param object the object to check
	 * @throws IllegalTransactionRequestException if the object is not a red/green externally owned account
	 */
	private void checkIsRedGreenExternallyOwned(Object object) throws ClassNotFoundException, IllegalTransactionRequestException {
		Class<? extends Object> clazz = object.getClass();
		if (!classLoader.getRedGreenExternallyOwnedAccount().isAssignableFrom(clazz))
			throw new IllegalTransactionRequestException("Only a red/green externally owned contract can start a transaction for a @RedPayable method or constructor");
	}

	/**
	 * Collects all updates reachable from the actuals or from the caller, receiver or result of a method call.
	 * 
	 * @param actuals the actuals; only {@code Storage} are relevant; this might be {@code null}
	 * @param caller the caller of an {@code @@Entry} method; this might be {@code null}
	 * @param receiver the receiver of the call; this might be {@code null}
	 * @param result the result; relevant only if {@code Storage}
	 * @return the ordered updates
	 */
	private SortedSet<Update> collectUpdates(Object[] actuals, Object caller, Object receiver, Object result) {
		List<Object> potentiallyAffectedObjects = new ArrayList<>();
		if (caller != null)
			potentiallyAffectedObjects.add(caller);
		if (receiver != null)
			potentiallyAffectedObjects.add(receiver);
		if (result != null && classLoader.getStorage().isAssignableFrom(result.getClass()))
			potentiallyAffectedObjects.add(result);

		if (actuals != null)
			for (Object actual: actuals)
				if (actual != null && classLoader.getStorage().isAssignableFrom(actual.getClass()))
					potentiallyAffectedObjects.add(actual);

		// events are accessible from outside, hence they count as side-effects
		events.forEach(potentiallyAffectedObjects::add);

		return new ExtractedUpdates(this, potentiallyAffectedObjects.stream()).getUpdates();
	}

	/**
	 * The thread that executes a constructor or method of a storage object. It creates the class loader
	 * from the class path and deserializes receiver and actuals. Then calls the code and serializes
	 * the resulting value back.
	 */
	private abstract class CodeExecutor extends Thread {

		/**
		 * The exception resulting from the execution of the method or constructor, if any.
		 * This is {@code null} if the execution completed without exception.
		 */
		protected Throwable exception;

		/**
		 * The resulting value for methods or the created object for constructors.
		 * This is {@code null} if the execution completed with an exception or
		 * if the method actually returned {@code null}.
		 */
		protected Object result;

		/**
		 * The deserialized caller.
		 */
		protected final Object deserializedCaller;

		/**
		 * The method or constructor that is being called.
		 */
		protected final CodeSignature methodOrConstructor;
		
		/**
		 * True if the method has been called correctly and it is declared as {@code void},
		 */
		protected boolean isVoidMethod;

		/**
		 * True if the method has been called correctly and it is annotated as {@link io.takamaka.code.lang.View}.
		 */
		protected boolean isViewMethod;

		/**
		 * The deserialized receiver of a method call. This is {@code null} for static methods and constructors.
		 */
		protected final Object deserializedReceiver; // it might be null

		/**
		 * The deserialized actual arguments of the call.
		 */
		protected final Object[] deserializedActuals;

		/**
		 * Builds the executor of a method or constructor.
		 * 
		 * @param classLoader the class loader that must be used to find the classes during the execution of the method or constructor
		 * @param deseralizedCaller the deserialized caller
		 * @param methodOrConstructor the method or constructor to call
		 * @param receiver the receiver of the call, if any. This is {@code null} for constructors and static methods
		 * @param actuals the actuals provided to the method or constructor
		 */
		private CodeExecutor(Object deseralizedCaller, CodeSignature methodOrConstructor, StorageReference receiver, Stream<StorageValue> actuals) {
			this.deserializedCaller = deseralizedCaller;
			this.methodOrConstructor = methodOrConstructor;
			this.deserializedReceiver = receiver != null ? deserializer.deserialize(receiver) : null;
			this.deserializedActuals = actuals.map(deserializer::deserialize).toArray(Object[]::new);
		}

		/**
		 * A cache for {@link io.takamaka.code.blockchain.AbstractBlockchain.CodeExecutor#updates()}.
		 */
		private SortedSet<Update> updates;

		/**
		 * Yields the updates resulting from the execution of the method or constructor.
		 * 
		 * @return the updates
		 */
		protected final Stream<Update> updates() {
			if (updates != null)
				return updates.stream();

			return (updates = collectUpdates(deserializedActuals, deserializedCaller, deserializedReceiver, result)).stream();
		}

		/**
		 * Resolves the method that must be called.
		 * 
		 * @return the method
		 * @throws NoSuchMethodException if the method could not be found
		 * @throws SecurityException if the method could not be accessed
		 * @throws ClassNotFoundException if the class of the method or of some parameter or return type cannot be found
		 */
		protected final Method getMethod() throws ClassNotFoundException, NoSuchMethodException {
			MethodSignature method = (MethodSignature) methodOrConstructor;
			Class<?> returnType = method instanceof NonVoidMethodSignature ? storageTypeToClass.toClass(((NonVoidMethodSignature) method).returnType) : void.class;
			Class<?>[] argTypes = formalsAsClass();

			return classLoader.resolveMethod(method.definingClass.name, method.methodName, argTypes, returnType)
				.orElseThrow(() -> new NoSuchMethodException(method.toString()));
		}

		/**
		 * Resolves the method that must be called, assuming that it is an entry.
		 * 
		 * @return the method
		 * @throws NoSuchMethodException if the method could not be found
		 * @throws SecurityException if the method could not be accessed
		 * @throws ClassNotFoundException if the class of the method or of some parameter or return type cannot be found
		 */
		protected final Method getEntryMethod() throws NoSuchMethodException, SecurityException, ClassNotFoundException {
			MethodSignature method = (MethodSignature) methodOrConstructor;
			Class<?> returnType = method instanceof NonVoidMethodSignature ? storageTypeToClass.toClass(((NonVoidMethodSignature) method).returnType) : void.class;
			Class<?>[] argTypes = formalsAsClassForEntry();

			return classLoader.resolveMethod(method.definingClass.name, method.methodName, argTypes, returnType)
				.orElseThrow(() -> new NoSuchMethodException(method.toString()));
		}

		/**
		 * Determines if the execution only affected the balance of the caller contract.
		 *
		 * @param deserializedCaller the caller contract
		 * @return true  if and only if that condition holds
		 */
		protected final boolean onlyAffectedBalanceOf(Object deserializedCaller) {
			return updates().allMatch
				(update -> update.object.equals(getStorageReferenceOf(deserializedCaller))
				&& update instanceof UpdateOfField
				&& ((UpdateOfField) update).getField().equals(FieldSignature.BALANCE_FIELD));
		}

		/**
		 * Yields the classes of the formal arguments of the method or constructor.
		 * 
		 * @return the array of classes, in the same order as the formals
		 * @throws ClassNotFoundException if some class cannot be found
		 */
		protected final Class<?>[] formalsAsClass() throws ClassNotFoundException {
			List<Class<?>> classes = new ArrayList<>();
			for (StorageType type: methodOrConstructor.formals().collect(Collectors.toList()))
				classes.add(storageTypeToClass.toClass(type));

			return classes.toArray(new Class<?>[classes.size()]);
		}

		/**
		 * Yields the classes of the formal arguments of the method or constructor, assuming that it is
		 * and {@link io.takamaka.code.lang.Entry}. Entries are instrumented with the addition of
		 * trailing contract formal (the caller) and of a dummy type.
		 * 
		 * @return the array of classes, in the same order as the formals
		 * @throws ClassNotFoundException if some class cannot be found
		 */
		protected final Class<?>[] formalsAsClassForEntry() throws ClassNotFoundException {
			List<Class<?>> classes = new ArrayList<>();
			for (StorageType type: methodOrConstructor.formals().collect(Collectors.toList()))
				classes.add(storageTypeToClass.toClass(type));

			classes.add(classLoader.getContract());
			classes.add(Dummy.class);

			return classes.toArray(new Class<?>[classes.size()]);
		}

		/**
		 * Adds to the actual parameters the implicit actuals that are passed
		 * to {@link io.takamaka.code.lang.Entry} methods or constructors. They are the caller of
		 * the entry and {@code null} for the dummy argument.
		 * 
		 * @return the resulting actual parameters
		 */
		protected final Object[] addExtraActualsForEntry() {
			int al = deserializedActuals.length;
			Object[] result = new Object[al + 2];
			System.arraycopy(deserializedActuals, 0, result, 0, al);
			result[al] = deserializedCaller;
			result[al + 1] = null; // Dummy is not used

			return result;
		}

		protected final boolean isChecked(Throwable t) {
			return !(t instanceof RuntimeException || t instanceof Error);
		}

		/**
		 * Yields the same exception, if it is checked and the executable is annotated as {@link io.takamaka.code.lang.ThrowsExceptions}.
		 * Otherwise, yields its cause.
		 * 
		 * @param e the exception
		 * @param executable the method or constructor whose execution has thrown the exception
		 * @return the same exception, or its cause
		 */
		protected final Throwable unwrapInvocationException(InvocationTargetException e, Executable executable) {
			if (isChecked(e.getCause()) && hasAnnotation(executable, ClassType.THROWS_EXCEPTIONS.name))
				return e;
			else
				return e.getCause();
		}

		/**
		 * Checks that the given method or constructor can be called from Takamaka code, that is,
		 * is white-listed and its white-listing proof-obligations hold.
		 * 
		 * @param executable the method or constructor
		 * @param actuals the actual arguments passed to {@code executable}, including the
		 *                receiver for instance methods
		 * @throws ClassNotFoundException if some class could not be found during the check
		 */
		protected final void ensureWhiteListingOf(Executable executable, Object[] actuals) throws ClassNotFoundException {
			Optional<? extends Executable> model;
			if (executable instanceof Constructor<?>) {
				model = classLoader.getWhiteListingWizard().whiteListingModelOf((Constructor<?>) executable);
				if (!model.isPresent())
					throw new NonWhiteListedCallException("illegal call to non-white-listed constructor of "
						+ ((ConstructorSignature) methodOrConstructor).definingClass.name);
			}
			else {
				model = classLoader.getWhiteListingWizard().whiteListingModelOf((Method) executable);
				if (!model.isPresent())
					throw new NonWhiteListedCallException("illegal call to non-white-listed method "
						+ ((MethodSignature) methodOrConstructor).definingClass.name + "." + ((MethodSignature) methodOrConstructor).methodName);
			}

			if (executable instanceof java.lang.reflect.Method && !Modifier.isStatic(executable.getModifiers()))
				checkWhiteListingProofObligations(model.get().getName(), deserializedReceiver, model.get().getAnnotations());

			Annotation[][] anns = model.get().getParameterAnnotations();
			for (int pos = 0; pos < anns.length; pos++)
				checkWhiteListingProofObligations(model.get().getName(), actuals[pos], anns[pos]);
		}

		private void checkWhiteListingProofObligations(String methodName, Object value, Annotation[] annotations) {
			Stream.of(annotations)
				.map(Annotation::annotationType)
				.map(this::getWhiteListingCheckFor)
				.filter(Optional::isPresent)
				.map(Optional::get)
				.forEachOrdered(checkMethod -> {
					try {
						// white-listing check methods are static
						checkMethod.invoke(null, value, methodName);
					}
					catch (InvocationTargetException e) {
						throw (NonWhiteListedCallException) e.getCause();
					}
					catch (IllegalAccessException | IllegalArgumentException e) {
						throw new IllegalStateException("could not check white-listing proof-obligations for " + methodName, e);
					}
				});
		}

		private Optional<Method> getWhiteListingCheckFor(Class<? extends Annotation> annotationType) {
			if (annotationType.isAnnotationPresent(WhiteListingProofObligation.class)) {
				String checkName = lowerInitial(annotationType.getSimpleName());
				Optional<Method> checkMethod = Stream.of(Runtime.class.getDeclaredMethods())
					.filter(method -> method.getName().equals(checkName)).findFirst();
		
				if (!checkMethod.isPresent())
					throw new IllegalStateException("unexpected white-list annotation " + annotationType.getSimpleName());
		
				return checkMethod;
			}
		
			return Optional.empty();
		}

		private String lowerInitial(String name) {
			return Character.toLowerCase(name.charAt(0)) + name.substring(1);
		}

		protected final boolean hasAnnotation(Executable executable, String annotationName) {
			return Stream.of(executable.getAnnotations())
				.anyMatch(annotation -> annotation.annotationType().getName().equals(annotationName));
		}
	}

	/**
	 * The thread that executes a constructor of a storage object. It creates the class loader
	 * from the class path and deserializes the actuals. Then calls the code and serializes
	 * the resulting value back.
	 */
	private class ConstructorExecutor extends CodeExecutor {

		/**
		 * Builds the executor of a constructor.
		 * 
		 * @param constructor the constructor to call
		 * @param deseralizedCaller the deserialized caller
		 * @param actuals the actuals provided to the constructor
		 */
		private ConstructorExecutor(ConstructorSignature constructor, Object deserializedCaller, Stream<StorageValue> actuals) {
			super(deserializedCaller, constructor, null, actuals);
		}

		/**
		 * Resolves the constructor that must be called.
		 * 
		 * @return the constructor
		 * @throws NoSuchMethodException if the constructor could not be found
		 * @throws SecurityException if the constructor could not be accessed
		 * @throws ClassNotFoundException if the class of the constructor or of some parameter cannot be found
		 */
		private Constructor<?> getConstructor() throws ClassNotFoundException, NoSuchMethodException {
			Class<?>[] argTypes = formalsAsClass();

			return classLoader.resolveConstructor(methodOrConstructor.definingClass.name, argTypes)
				.orElseThrow(() -> new NoSuchMethodException(methodOrConstructor.toString()));
		}

		/**
		 * Resolves the constructor that must be called, assuming that it is an entry.
		 * 
		 * @return the constructor
		 * @throws NoSuchMethodException if the constructor could not be found
		 * @throws SecurityException if the constructor could not be accessed
		 * @throws ClassNotFoundException if the class of the constructor or of some parameter cannot be found
		 */
		private Constructor<?> getEntryConstructor() throws ClassNotFoundException, NoSuchMethodException {
			Class<?>[] argTypes = formalsAsClassForEntry();

			return classLoader.resolveConstructor(methodOrConstructor.definingClass.name, argTypes)
				.orElseThrow(() -> new NoSuchMethodException(methodOrConstructor.toString()));
		}

		@Override
		public void run() {
			try {
				Constructor<?> constructorJVM;
				Object[] deserializedActuals;

				try {
					// we first try to call the constructor with exactly the parameter types explicitly provided
					constructorJVM = getConstructor();
					deserializedActuals = this.deserializedActuals;
				}
				catch (NoSuchMethodException e) {
					// if not found, we try to add the trailing types that characterize the @Entry constructors
					try {
						constructorJVM = getEntryConstructor();
						deserializedActuals = addExtraActualsForEntry();
					}
					catch (NoSuchMethodException ee) {
						throw e; // the message must be relative to the constructor as the user sees it
					}
				}

				ensureWhiteListingOf(constructorJVM, deserializedActuals);
				if (hasAnnotation(constructorJVM, io.takamaka.code.constants.Constants.RED_PAYABLE_NAME))
					checkIsRedGreenExternallyOwned(deserializedCaller);

				try {
					result = constructorJVM.newInstance(deserializedActuals);
				}
				catch (InvocationTargetException e) {
					exception = unwrapInvocationException(e, constructorJVM);
				}
			}
			catch (Throwable t) {
				exception = t;
			}
		}
	}

	/**
	 * The thread that executes an instance method of a storage object. It creates the class loader
	 * from the class path and deserializes receiver and actuals. Then calls the code and serializes
	 * the resulting value back.
	 */
	private class InstanceMethodExecutor extends CodeExecutor {

		/**
		 * Builds the executor of an instance method.
		 * 
		 * @param method the method to call
		 * @param deseralizedCaller the deserialized caller
		 * @param receiver the receiver of the method
		 * @param actuals the actuals provided to the method
		 */
		private InstanceMethodExecutor(MethodSignature method, Object deserializedCaller, StorageReference receiver, Stream<StorageValue> actuals) {
			super(deserializedCaller, method, receiver, actuals);
		}

		@Override
		public void run() {
			try {
				Method methodJVM;
				Object[] deserializedActuals;

				try {
					// we first try to call the method with exactly the parameter types explicitly provided
					methodJVM = getMethod();
					deserializedActuals = this.deserializedActuals;
				}
				catch (NoSuchMethodException e) {
					// if not found, we try to add the trailing types that characterize the @Entry methods
					try {
						methodJVM = getEntryMethod();
						deserializedActuals = addExtraActualsForEntry();
					}
					catch (NoSuchMethodException ee) {
						throw e; // the message must be relative to the method as the user sees it
					}
				}

				if (Modifier.isStatic(methodJVM.getModifiers()))
					throw new NoSuchMethodException("Cannot call a static method: use addStaticMethodCallTransaction instead");

				ensureWhiteListingOf(methodJVM, deserializedActuals);

				isVoidMethod = methodJVM.getReturnType() == void.class;
				isViewMethod = hasAnnotation(methodJVM, io.takamaka.code.constants.Constants.VIEW_NAME);
				if (hasAnnotation(methodJVM, io.takamaka.code.constants.Constants.RED_PAYABLE_NAME))
					checkIsRedGreenExternallyOwned(deserializedCaller);

				try {
					result = methodJVM.invoke(deserializedReceiver, deserializedActuals);
				}
				catch (InvocationTargetException e) {
					exception = unwrapInvocationException(e, methodJVM);
				}
			}
			catch (Throwable t) {
				exception = t;
			}
		}
	}

	/**
	 * The thread that executes a static method of a storage object. It creates the class loader
	 * from the class path and deserializes the actuals. Then calls the code and serializes
	 * the resulting value back.
	 */
	private class StaticMethodExecutor extends CodeExecutor {

		/**
		 * Builds the executor of a static method.
		 * 
		 * @param method the method to call
		 * @param caller the caller, that pays for the execution
		 * @param deseralizedCaller the deserialized caller
		 * @param actuals the actuals provided to the method
		 */
		private StaticMethodExecutor(MethodSignature method, Object deserializedCaller, Stream<StorageValue> actuals) {
			super(deserializedCaller, method, null, actuals);
		}

		@Override
		public void run() {
			try {
				Method methodJVM = getMethod();

				if (!Modifier.isStatic(methodJVM.getModifiers()))
					throw new NoSuchMethodException("Cannot call an instance method: use addInstanceMethodCallTransaction instead");

				ensureWhiteListingOf(methodJVM, deserializedActuals);

				isVoidMethod = methodJVM.getReturnType() == void.class;
				isViewMethod = hasAnnotation(methodJVM, io.takamaka.code.constants.Constants.VIEW_NAME);

				try {
					result = methodJVM.invoke(null, deserializedActuals);
				}
				catch (InvocationTargetException e) {
					exception = unwrapInvocationException(e, methodJVM);
				}
			}
			catch (Throwable t) {
				exception = t;
			}
		}
	}

	/**
	 * Yields the amount of gas still available to the
	 * currently executing transaction.
	 * 
	 * @return the remaining gas
	 */
	private BigInteger remainingGas() {
		return gas;
	}

	/**
	 * Wraps the given throwable in a {@link io.takamaka.code.blockchain.TransactionException}, if it not
	 * already an instance of that exception.
	 * 
	 * @param t the throwable to wrap
	 * @param message the message used for the {@link io.takamaka.code.blockchain.TransactionException}, if wrapping occurs
	 * @return the wrapped or original exception
	 */
	protected static TransactionException wrapAsTransactionException(Throwable t, String message) {
		return t instanceof TransactionException ? (TransactionException) t : new TransactionException(message, t);
	}

	protected Class<?> getStorage() {
		return classLoader.getStorage();
	}
}