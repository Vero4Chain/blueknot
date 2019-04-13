package takamaka.blockchain;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

import takamaka.blockchain.types.StorageType;
import takamaka.blockchain.values.StorageReference;
import takamaka.blockchain.values.StorageValue;
import takamaka.lang.Storage;
import takamaka.translator.Dummy;
import takamaka.translator.JarInstrumentation;
import takamaka.translator.Program;

/**
 * A generic implementation of a blockchain. Specific implementations can subclass this class
 * and just implement the abstract template methods. The rest of code should work instead
 * as an general layer for all blockchain implementations.
 */
public abstract class AbstractBlockchain implements Blockchain {
	private static final String CONTRACT_NAME = "takamaka.lang.Contract";
	private static final String EXTERNALLY_OWNED_ACCOUNT_NAME = "takamaka.lang.ExternallyOwnedAccount";

	/**
	 * The maximal length of an event message. This prevents spamming with long event messages.
	 */
	public final static int MAX_EVENT_LENGTH = 1000;

	/**
	 * The maximal length of the name of a jar installed in this blockchain, including its suffix.
	 */
	public final static int MAX_JAR_NAME_LENGTH = 100;

	/**
	 * A blockchain is initially under initialization. After the gamete has been created,
	 * the blockchain passed into the initialized state.
	 */
	private boolean isInitialized = false;

	/**
	 * The events accumulated during the current transaction. This is reset at each transaction.
	 */
	private final List<String> events = new ArrayList<>();

	/**
	 * A map from each storage reference to its deserialized object. This is needed in order to guarantee that
	 * repeated deserialization of the same storage reference yields the same object and can also
	 * work as an efficiency measure. This is reset at each transaction since each transaction uses
	 * a distinct class loader and each storage object keeps a reference to its class loader, as
	 * always in Java.
	 */
	private final Map<StorageReference, Storage> cache = new HashMap<>();

	// ABSTRACT TEMPLATE METHODS
	// Any implementation of a blockchain must implement the following and leave the rest unchanged

	/**
	 * Yields the reference to the transaction currently being executed.
	 * If no transaction is currently under execution, this is the reference to the
	 * next transaction that will be executed.
	 * 
	 * @return the reference
	 */
	public abstract TransactionReference getCurrentTransactionReference();

	/**
	 * Yields the transaction reference from its string representation.
	 * It must hold that {@code r.equals(mkTransactionReferenceFrom(r.toString()))}.
	 * 
	 * @param s the string representation
	 * @return the transaction reference
	 */
	public abstract TransactionReference mkTransactionReferenceFrom(String s);

	/**
	 * Specific implementation at the end of the successful creation of a gamete. Any blockchain implementation
	 * has here the opportunity to deal with the result of the creation.
	 * 
	 * @param takamakaBase the reference to the jar containing the basic Takamaka classes
	 * @param initialAmount the amount of coin provided to the gamete. This cannot be negative
	 * @param gamete the reference to the gamete that has been created
	 * @param updates the memory updates induced by the transaction. This includes at least
	 *                those that describe the new gamete
	 * @throws Exception if something goes wrong in this method. In that case, the gamete creation
	 *                   transaction will be aborted
	 */
	protected abstract void addGameteCreationTransactionInternal(Classpath takamakaBase, BigInteger initialAmount, StorageReference gamete, SortedSet<Update> updates) throws Exception;

	// BLOCKCHAIN-AGNOSTIC IMPLEMENTATION

	/**
	 * Adds an event to those occurred during the execution of the current transaction.
	 * 
	 * @param event the event description
	 * @throws IllegalArgumentException if the event is {@code null} or longer than {@link AbstractBlockchain#MAX_EVENT_LENGTH}
	 */
	public final void event(String event) {
		if (event == null)
			throw new IllegalArgumentException("Events cannot be null");
		else if (event.length() > MAX_EVENT_LENGTH)
			throw new IllegalArgumentException("Events cannot be longer than " + MAX_EVENT_LENGTH + " characters");

		events.add(event);
	}

	@Override
	public final StorageReference addGameteCreationTransaction(Classpath takamakaBase, BigInteger initialAmount) throws TransactionException {
		if (isInitialized)
			throw new TransactionException("Blockchain already initialized");

		checkNotFull();

		Storage gamete;
		try (BlockchainClassLoader classLoader = mkBlockchainClassLoader(takamakaBase)) {
			// we create an initial gamete ExternallyOwnedContract and we fund it with the initial amount
			Class<?> gameteClass = classLoader.loadClass(EXTERNALLY_OWNED_ACCOUNT_NAME);
			Class<?> contractClass = classLoader.loadClass(CONTRACT_NAME);
			initTransaction(classLoader);
			gamete = (Storage) gameteClass.newInstance();
			// we set the balance field of the gamete
			Field balanceField = contractClass.getDeclaredField("balance");
			balanceField.setAccessible(true); // since the field is private
			balanceField.set(gamete, initialAmount);
			SortedSet<Update> updates = collectUpdates(null, null, null, gamete);
			StorageReference gameteRef = gamete.storageReference;
			addGameteCreationTransactionInternal(takamakaBase, initialAmount, gameteRef, updates);
			increaseCurrentTransactionReference();
			isInitialized = true;
			return gameteRef;
		}
		catch (Throwable t) {
			throw wrapAsTransactionException(t, "Cannot complete the transaction");
		}
	}

	@Override
	public final TransactionReference addJarStoreInitialTransaction(Path jar, Classpath... dependencies) throws TransactionException {
		try {
			if (isInitialized)
				throw new TransactionException("Blockchain already initialized");

			Gas.init(BigInteger.ZERO);
			TransactionReference jarReference = addJarStoreTransactionCommon(null, null, BigInteger.ZERO, null, jar, dependencies);
			increaseCurrentTransactionReference();
			return jarReference;
		}
		catch (Throwable t) {
			throw wrapAsTransactionException(t, "Cannot complete the transaction");
		}
	}

	@Override
	public final TransactionReference addJarStoreTransaction(StorageReference caller, BigInteger gas, Classpath classpath, Path jar, Classpath... dependencies) throws TransactionException {
		try (BlockchainClassLoader classLoader = mkBlockchainClassLoader(classpath)) {
			checkNotFull();
			initTransaction(classLoader);
			Storage deserializedCaller = caller.deserialize(classLoader, this);
			checkIsExternallyOwned(classLoader, deserializedCaller);
			Gas.init(gas);

			// we sell all gas first. What remains will be paid back at the end
			decreaseBalance(deserializedCaller, gas);

			Gas.charge(GasCosts.BASE_TRANSACTION_COST);
			Gas.charge((long) (dependencies.length * GasCosts.GAS_PER_DEPENDENCY_OF_JAR));
			Gas.charge((long) (Files.size(jar) * GasCosts.GAS_PER_BYTE_IN_JAR));
			TransactionReference jarReference = addJarStoreTransactionCommon(caller, classpath, gas, deserializedCaller, jar, dependencies);
			increaseCurrentTransactionReference();
			return jarReference;
		}
		catch (Throwable t) {
			throw wrapAsTransactionException(t, "Cannot complete the transaction");
		}
	}

	/**
	 * Used by both {@link takamaka.blockchain.AbstractBlockchain#addJarStoreInitialTransaction(Path, Classpath...)} and
	 * {@link takamaka.blockchain.AbstractBlockchain#addJarStoreTransaction(StorageReference, BigInteger, Classpath, Path, Classpath...)}
	 * to perform common tasks.
	 * 
	 * @param caller the externally owned caller contract that pays for the transaction
	 * @param classpath the class path where the {@code caller} is interpreted
	 * @param gas the maximal amount of gas that can be consumed by the transaction
	 * @param deserializedCaller the caller, after deserialization
	 * @param jar the jar to install
	 * @param dependencies the dependencies of the jar, already installed in this blockchain
	 * @return the reference to the transaction, that can be used to refer to this jar in a class path or as future dependency of other jars
	 * @throws Exception if something goes wrong. In that case, the transaction will be aborted
	 */
	private TransactionReference addJarStoreTransactionCommon(StorageReference caller, Classpath classpath, BigInteger gas, Storage deserializedCaller, Path jar, Classpath... dependencies) throws Exception {
		checkNotFull();

		Path jarName = jar.getFileName();
		String jn = jarName.toString();
		if (!jn.endsWith(".jar"))
			throw new TransactionException("Jar file should end in .jar");

		if (jn.length() > MAX_JAR_NAME_LENGTH)
			throw new TransactionException("Jar file name cannot be longer than " + MAX_JAR_NAME_LENGTH + " characters");

		TransactionReference ref = getCurrentTransactionReference();
		for (Classpath dependency: dependencies)
			if (!dependency.transaction.isOlderThan(ref))
				throw new TransactionException("A transaction can only depend on older transactions");

		// we create a temporary file to hold the instrumented jar
		Path instrumented = Files.createTempFile("instrumented", "jar");
		new JarInstrumentation(jar, instrumented, mkProgram(jar, dependencies));
	
		if (deserializedCaller != null)
			increaseBalance(deserializedCaller, Gas.remaining());

		addJarStoreTransactionInternal(caller, classpath, jar, instrumented, collectUpdates(null, deserializedCaller, null, null), gas.subtract(Gas.remaining()), dependencies);

		return ref;
	}

	protected abstract void addJarStoreTransactionInternal(StorageReference caller, Classpath classpath, Path jar, Path instrumented, SortedSet<Update> updates, BigInteger consumedGas, Classpath... dependencies) throws Exception;

	@Override
	public final StorageReference addConstructorCallTransaction(StorageReference caller, BigInteger gas, Classpath classpath, ConstructorReference constructor, StorageValue... actuals) throws TransactionException, CodeExecutionException {
		return (StorageReference) transaction(caller, gas, classpath,
				(classLoader, deserializedCaller) -> new ConstructorExecutor(classpath, classLoader, constructor, caller, deserializedCaller, gas, actuals));
	}

	protected abstract void addConstructorCallTransactionInternal(CodeExecutor executor) throws Exception;

	@Override
	public final StorageValue addInstanceMethodCallTransaction(StorageReference caller, BigInteger gas, Classpath classpath, MethodReference method, StorageReference receiver, StorageValue... actuals) throws TransactionException, CodeExecutionException {
		return transaction(caller, gas, classpath,
				(classLoader, deserializedCaller) -> new InstanceMethodExecutor(classpath, classLoader, method, caller, deserializedCaller, gas, receiver, actuals));
	}

	protected abstract void addInstanceMethodCallTransactionInternal(CodeExecutor executor) throws Exception;

	@Override
	public final StorageValue addStaticMethodCallTransaction(StorageReference caller, BigInteger gas, Classpath classpath, MethodReference method, StorageValue... actuals) throws TransactionException, CodeExecutionException {
		return transaction(caller, gas, classpath,
				(classLoader, deserializedCaller) -> new StaticMethodExecutor(classpath, classLoader, method, caller, deserializedCaller, gas, actuals));
	}

	protected abstract void addStaticMethodCallTransactionInternal(CodeExecutor executor) throws Exception;

	private interface ExecutorProducer {
		CodeExecutor produce(BlockchainClassLoader classLoader, Storage deserializedCaller) throws Exception;
	}

	private StorageValue transaction(StorageReference caller, BigInteger gas, Classpath classpath, ExecutorProducer executorProducer) throws TransactionException, CodeExecutionException {
		try (BlockchainClassLoader classLoader = mkBlockchainClassLoader(classpath)) {
			checkNotFull();
			initTransaction(classLoader);
			Storage deserializedCaller = caller.deserialize(classLoader, this);
			checkIsExternallyOwned(classLoader, deserializedCaller);
			Gas.init(gas);
			decreaseBalance(deserializedCaller, gas);
			Gas.charge(GasCosts.BASE_TRANSACTION_COST);

			CodeExecutor executor = executorProducer.produce(classLoader, deserializedCaller);
			executor.start();
			executor.join();

			if (executor.exception instanceof TransactionException)
				throw (TransactionException) executor.exception;

			increaseBalance(deserializedCaller, Gas.remaining());
			executor.addTransactionInternal();
			increaseCurrentTransactionReference();

			if (executor.exception != null)
				throw new CodeExecutionException("Code execution threw exception", executor.exception);
			else
				return StorageValue.serialize(executor.result);
		}
		catch (CodeExecutionException e) {
			throw e; // do not wrap into a TransactionException
		}
		catch (Throwable t) {
			throw wrapAsTransactionException(t, "Cannot complete the transaction");
		}
	}

	public final Storage deserialize(BlockchainClassLoader classLoader, StorageReference reference) throws TransactionException {
		try {
			return cache.computeIfAbsent(reference, _reference -> deserializeAnew(classLoader, _reference));
		}
		catch (RuntimeException e) {
			throw wrapAsTransactionException(e.getCause(), "Cannot deserialize " + reference);
		}
	}

	private Storage deserializeAnew(BlockchainClassLoader classLoader, StorageReference reference) {
		// this comparator puts updates in the order required for the parameter
		// of the deserialization constructor of storage objects: fields of superclasses first;
		// for the same class, fields are ordered by name and then by type
		Comparator<Update> updateComparator = new Comparator<Update>() {
	
			@Override
			public int compare(Update update1, Update update2) {
				FieldReference field1 = update1.field;
				FieldReference field2 = update2.field;
	
				try {
					String className1 = field1.definingClass.name;
					String className2 = field2.definingClass.name;
	
					if (className1.equals(className2)) {
						int diff = field1.name.compareTo(field2.name);
						if (diff != 0)
							return diff;
						else
							return field1.type.toString().compareTo(field2.type.toString());
					}
	
					Class<?> clazz1 = classLoader.loadClass(className1);
					Class<?> clazz2 = classLoader.loadClass(className2);
					if (clazz1.isAssignableFrom(clazz2)) // clazz1 superclass of clazz2
						return -1;
					else if (clazz2.isAssignableFrom(clazz1)) // clazz2 superclass of clazz1
						return 1;
					else
						throw new IllegalStateException("Updates are not on the same supeclass chain");
				}
				catch (ClassNotFoundException e) {
					throw new IllegalStateException(e);
				}
			}
		};
	
		try {
			SortedSet<Update> updates = new TreeSet<>(updateComparator);
			collectUpdatesFor(reference, updates);
	
			Optional<Update> classTag = updates.stream()
					.filter(Update::isClassTag)
					.findAny();
	
			if (!classTag.isPresent())
				throw new TransactionException("No class tag found for " + reference);
	
			String className = classTag.get().field.definingClass.name;
			List<Class<?>> formals = new ArrayList<>();
			List<Object> actuals = new ArrayList<>();
			// the constructor for deserialization has a first parameter
			// that receives the storage reference of the object
			formals.add(StorageReference.class);
			actuals.add(reference);
	
			for (Update update: updates)
				if (!update.isClassTag()) {
					formals.add(update.field.type.toClass(classLoader));
					actuals.add(update.value.deserialize(classLoader, this));
				}
	
			Class<?> clazz = classLoader.loadClass(className);
			Constructor<?> constructor = clazz.getConstructor(formals.toArray(new Class<?>[formals.size()]));
			return (Storage) constructor.newInstance(actuals.toArray(new Object[actuals.size()]));
		}
		catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	public final Object deserializeLastUpdateFor(BlockchainClassLoader classLoader, StorageReference reference, FieldReference field) throws TransactionException {
		try {
			return getLastUpdateFor(reference, field).value.deserialize(classLoader, this);
		}
		catch (Throwable t) {
			throw wrapAsTransactionException(t, "Could not deserialize " + reference);
		}
	}

	protected abstract Update getLastUpdateFor(StorageReference reference, FieldReference field) throws TransactionException;

	/**
	 * Sells the given amount of gas to the given externally owned account.
	 * 
	 * @param eoa the reference to the externally owned account
	 * @param gas the gas to sell
	 * @throws InsufficientFundsError if the account has not enough money to pay for the gas
	 * @throws ClassNotFoundException if the balance of the account cannot be correctly modified
	 * @throws NoSuchFieldException if the balance of the account cannot be correctly modified
	 * @throws SecurityException if the balance of the account cannot be correctly modified
	 * @throws IllegalArgumentException if the balance of the account cannot be correctly modified
	 * @throws IllegalAccessException if the balance of the account cannot be correctly modified
	 */
	private static void decreaseBalance(Storage eoa, BigInteger gas)
			throws InsufficientFundsError, ClassNotFoundException, NoSuchFieldException,
			SecurityException, IllegalArgumentException, IllegalAccessException {
	
		BigInteger delta = GasCosts.toCoin(gas);
		Class<?> contractClass = eoa.getClass().getClassLoader().loadClass(CONTRACT_NAME);
		Field balanceField = contractClass.getDeclaredField("balance");
		balanceField.setAccessible(true); // since the field is private
		BigInteger previousBalance = (BigInteger) balanceField.get(eoa);
		if (previousBalance.compareTo(delta) < 0)
			throw new InsufficientFundsError();
		else
			balanceField.set(eoa, previousBalance.subtract(delta));
	}

	/**
	 * Buys back the given amount of gas from the given externally owned account.
	 * 
	 * @param eoa the reference to the externally owned account
	 * @param gas the gas to buy back
	 * @throws ClassNotFoundException if the balance of the account cannot be correctly modified
	 * @throws NoSuchFieldException if the balance of the account cannot be correctly modified
	 * @throws SecurityException if the balance of the account cannot be correctly modified
	 * @throws IllegalArgumentException if the balance of the account cannot be correctly modified
	 * @throws IllegalAccessException if the balance of the account cannot be correctly modified
	 */
	private static void increaseBalance(Storage eoa, BigInteger gas)
			throws ClassNotFoundException, NoSuchFieldException,
			SecurityException, IllegalArgumentException, IllegalAccessException {
	
		BigInteger delta = GasCosts.toCoin(gas);
		Class<?> contractClass = eoa.getClass().getClassLoader().loadClass(CONTRACT_NAME);
		Field balanceField = contractClass.getDeclaredField("balance");
		balanceField.setAccessible(true); // since the field is private
		BigInteger previousBalance = (BigInteger) balanceField.get(eoa);
		balanceField.set(eoa, previousBalance.add(delta));
	}

	/**
	 * Checks if the given object is an externally owned account or a subclass.
	 * 
	 * @param object the object to check
	 * @throws ClassNotFoundException if the {@link takamaka.lang.ExternallyOwnedAccount} class cannot be found
	 *                                in the class path of the transaction
	 */
	private static void checkIsExternallyOwned(BlockchainClassLoader classLoader, Storage object) throws ClassNotFoundException {
		Class<?> eoaClass = classLoader.loadClass(EXTERNALLY_OWNED_ACCOUNT_NAME);
		if (!eoaClass.isAssignableFrom(object.getClass()))
			throw new IllegalArgumentException("Only an externally owned contract can start a transaction");
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
	private static SortedSet<Update> collectUpdates(Object[] actuals, Storage caller, Storage receiver, Object result) {
		List<Storage> potentiallyAffectedObjects = new ArrayList<>();
		if (caller != null)
			potentiallyAffectedObjects.add(caller);
		if (receiver != null)
			potentiallyAffectedObjects.add(receiver);
		if (result instanceof Storage)
			potentiallyAffectedObjects.add((Storage) result);

		if (actuals != null)
			for (Object actual: actuals)
				if (actual instanceof Storage)
					potentiallyAffectedObjects.add((Storage) actual);

		Set<StorageReference> seen = new HashSet<>();
		SortedSet<Update> updates = new TreeSet<>();
		potentiallyAffectedObjects.forEach(storage -> storage.updates(updates, seen));

		return updates;
	}

	public abstract class CodeExecutor extends Thread {
		protected Throwable exception;
		protected Object result;
		private final Classpath classpath;
		protected final BlockchainClassLoader classLoader;
		private final StorageReference caller;
		protected final Storage deserializedCaller;
		protected final BigInteger gas;
		protected final CodeReference methodOrConstructor;
		private final StorageReference receiver; // it might be null
		protected final Storage deserializedReceiver; // it might be null
		private final StorageValue[] actuals;
		protected final Object[] deserializedActuals;

		private CodeExecutor(Classpath classpath, BlockchainClassLoader classLoader, StorageReference caller, Storage deseralizedCaller, BigInteger gas, CodeReference methodOrConstructor, StorageReference receiver, StorageValue... actuals) throws Exception {
			this.classpath = classpath;
			this.classLoader = classLoader;
			this.caller = caller;
			this.deserializedCaller = deseralizedCaller;
			this.gas = gas;
			this.methodOrConstructor = methodOrConstructor;
			this.receiver = receiver;
			this.deserializedReceiver = receiver != null ? receiver.deserialize(classLoader, AbstractBlockchain.this) : null;
			this.actuals = actuals;
			this.deserializedActuals = deserialize(classLoader, actuals);

			setContextClassLoader(new ClassLoader(classLoader.getParent()) {

				@Override
				public Class<?> loadClass(String name) throws ClassNotFoundException {
					return classLoader.loadClass(name);
				}
			});
		}

		public final BigInteger getGas() {
			return gas;
		}

		public final Classpath getClasspath() {
			return classpath;
		}

		public final StorageReference getReceiver() {
			return receiver;
		}

		public final StorageValue[] getActuals() {
			return actuals;
		}

		public final Throwable getException() {
			return exception;
		}

		public final StorageValue getResult() {
			return exception == null ? StorageValue.serialize(result) : null;
		}

		public final StorageReference getCaller() {
			return caller;
		}

		public final CodeReference getMethodOrConstructor() {
			return methodOrConstructor;
		}

		public final SortedSet<Update> updates() {
			return collectUpdates(deserializedActuals, deserializedCaller, deserializedReceiver, result);
		}

		public final List<String> events() {
			return events;
		}

		public final BigInteger gasConsumed() {
			return gas.subtract(Gas.remaining());
		}

		protected abstract void addTransactionInternal() throws Exception;
	}

	private class ConstructorExecutor extends CodeExecutor {
		private ConstructorExecutor(Classpath classpath, BlockchainClassLoader classLoader, ConstructorReference constructor, StorageReference caller, Storage deserializedCaller, BigInteger gas, StorageValue... actuals) throws Exception {
			super(classpath, classLoader, caller, deserializedCaller, gas, constructor, null, actuals);
		}

		@Override
		public void run() {
			try {
				Class<?> clazz = classLoader.loadClass(methodOrConstructor.definingClass.name);
				Constructor<?> constructorJVM;
				Object[] deserializedActuals = this.deserializedActuals;

				try {
					// we first try to call the constructor with exactly the parameter types explicitly provided
					constructorJVM = clazz.getConstructor(formalsAsClass(classLoader, methodOrConstructor));
				}
				catch (NoSuchMethodException e) {
					// if not found, we try to add the trailing types that characterize the @Entry constructors
					try {
						constructorJVM = clazz.getConstructor(formalsAsClassForEntry(classLoader, methodOrConstructor));
					}
					catch (NoSuchMethodException ee) {
						throw e; // the message must be relative to the constructor as the user sees it
					}
					deserializedActuals = addExtraActualsForEntry(deserializedActuals, deserializedCaller);
				}

				result = ((Storage) constructorJVM.newInstance(deserializedActuals));
			}
			catch (InvocationTargetException e) {
				exception = e.getCause();
			}
			catch (Throwable t) {
				exception = wrapAsTransactionException(t, "Could not call the constructor");
			}
		}

		@Override
		protected void addTransactionInternal() throws Exception {
			addConstructorCallTransactionInternal(this);
		}
	}

	private class InstanceMethodExecutor extends CodeExecutor {
		private InstanceMethodExecutor(Classpath classpath, BlockchainClassLoader classLoader, MethodReference method, StorageReference caller, Storage deserializedCaller, BigInteger gas, StorageReference receiver, StorageValue... actuals) throws Exception {
			super(classpath, classLoader, caller, deserializedCaller, gas, method, receiver, actuals);
		}

		@Override
		public void run() {
			try {
				Class<?> clazz = classLoader.loadClass(methodOrConstructor.definingClass.name);
				String methodName = ((MethodReference) methodOrConstructor).methodName;
				Method methodJVM;
				Object[] deserializedActuals = this.deserializedActuals;

				try {
					// we first try to call the method with exactly the parameter types explicitly provided
					methodJVM = clazz.getMethod(methodName, formalsAsClass(classLoader, methodOrConstructor));
				}
				catch (NoSuchMethodException e) {
					// if not found, we try to add the trailing types that characterize the @Entry methods
					try {
						methodJVM = clazz.getMethod(methodName, formalsAsClassForEntry(classLoader, methodOrConstructor));
					}
					catch (NoSuchMethodException ee) {
						throw e; // the message must be relative to the method as the user sees it
					}
					deserializedActuals = addExtraActualsForEntry(deserializedActuals, deserializedCaller);
				}

				if (Modifier.isStatic(methodJVM.getModifiers()))
					throw new NoSuchMethodException("Cannot call a static method: use addStaticMethodCallTransaction instead");

				result = methodJVM.invoke(deserializedReceiver, deserializedActuals);
			}
			catch (InvocationTargetException e) {
				exception = e.getCause();
			}
			catch (Throwable t) {
				exception = wrapAsTransactionException(t, "Could not call the method");
			}
		}

		@Override
		protected void addTransactionInternal() throws Exception {
			addInstanceMethodCallTransactionInternal(this);
		}
	}

	private class StaticMethodExecutor extends CodeExecutor {
		private StaticMethodExecutor(Classpath classpath, BlockchainClassLoader classLoader, MethodReference method, StorageReference caller, Storage deserializedCaller, BigInteger gas, StorageValue... actuals) throws Exception {
			super(classpath, classLoader, caller, deserializedCaller, gas, method, null, actuals);
		}

		@Override
		public void run() {
			try {
				Class<?> clazz = classLoader.loadClass(methodOrConstructor.definingClass.name);
				Method methodJVM = clazz.getMethod(((MethodReference) methodOrConstructor).methodName, formalsAsClass(classLoader, methodOrConstructor));

				if (!Modifier.isStatic(methodJVM.getModifiers()))
					throw new NoSuchMethodException("Cannot call an instance method: use addInstanceMethodCallTransaction instead");

				result = methodJVM.invoke(null, deserializedActuals);
			}
			catch (InvocationTargetException e) {
				exception = e.getCause();
			}
			catch (Throwable t) {
				exception = wrapAsTransactionException(t, "Could not call the method");
			}
		}

		@Override
		protected void addTransactionInternal() throws Exception {
			addStaticMethodCallTransactionInternal(this);
		}
	}

	private Program mkProgram(Path jar, Classpath... dependencies) {
		List<Path> result = new ArrayList<>();
		result.add(jar);

		try {
			for (Classpath dependency: dependencies)
				extractPathsRecursively(dependency, result);

			return new Program(result.stream());
		}
		catch (IOException e) {
			throw new UncheckedIOException("Cannot build the set of all classes in the class path", e);
		}
	}

	protected abstract void extractPathsRecursively(Classpath classpath, List<Path> result) throws IOException;

	protected abstract void collectUpdatesFor(StorageReference reference, Set<Update> where) throws TransactionException;

	protected abstract BlockchainClassLoader mkBlockchainClassLoader(Classpath classpath) throws TransactionException;

	protected abstract boolean blockchainIsFull();

	protected abstract void increaseCurrentTransactionReference();

	/**
	 * Wraps the given throwable in a {@link takamaka.blockchain.TransactionException}, if it not
	 * already an instance of that exception.
	 * 
	 * @param t the throwable to wrap
	 * @param message the message added to the {@link takamaka.blockchain.TransactionException}, if wrapping occurs
	 * @return the wrapped or original exception
	 */
	protected final static TransactionException wrapAsTransactionException(Throwable t, String message) {
		if (t instanceof TransactionException)
			return (TransactionException) t;
		else
			return new TransactionException(message, t);
	}

	private static Class<?>[] formalsAsClass(BlockchainClassLoader classLoader, CodeReference methodOrConstructor) throws ClassNotFoundException {
		List<Class<?>> classes = new ArrayList<>();
		for (StorageType type: methodOrConstructor.formals().collect(Collectors.toList()))
			classes.add(type.toClass(classLoader));
	
		return classes.toArray(new Class<?>[classes.size()]);
	}

	private static Class<?>[] formalsAsClassForEntry(BlockchainClassLoader classLoader, CodeReference methodOrConstructor) throws ClassNotFoundException {
		List<Class<?>> classes = new ArrayList<>();
		for (StorageType type: methodOrConstructor.formals().collect(Collectors.toList()))
			classes.add(type.toClass(classLoader));

		classes.add(classLoader.loadClass("takamaka.lang.Contract"));
		classes.add(Dummy.class);

		return classes.toArray(new Class<?>[classes.size()]);
	}

	private static Object[] addExtraActualsForEntry(Object[] actuals, Storage caller) {
		Object[] result = new Object[actuals.length + 2];
		System.arraycopy(actuals, 0, result, 0, actuals.length);
		result[actuals.length] = caller;
		result[actuals.length + 1] = null; // Dummy is not used

		return result;
	}

	private Object[] deserialize(BlockchainClassLoader classLoader, StorageValue[] actuals) throws TransactionException {
		Object[] deserialized = new Object[actuals.length];
		for (int pos = 0; pos < actuals.length; pos++)
			deserialized[pos] = actuals[pos].deserialize(classLoader, this);
		
		return deserialized;
	}

	private void checkNotFull() throws TransactionException {
		if (blockchainIsFull())
			throw new TransactionException("No more transactions available in blockchain");
	}

	private void initTransaction(BlockchainClassLoader classLoader) {
		Storage.init(AbstractBlockchain.this, classLoader); // this blockchain will be used during the execution of the code
		events.clear();
		// the cache must be cleaned at least when a transaction failed and partial state of objects should be thrown away;
		// for the moment, we do it always
		cache.clear();
	}
}