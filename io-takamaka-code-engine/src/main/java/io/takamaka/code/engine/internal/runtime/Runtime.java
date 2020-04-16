package io.takamaka.code.engine.internal.runtime;

import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.util.concurrent.Callable;

import io.hotmoka.beans.signatures.FieldSignature;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.nodes.NonWhiteListedCallException;
import io.hotmoka.nodes.OutOfGasError;
import io.takamaka.code.engine.internal.EngineClassLoader;
import io.takamaka.code.engine.internal.transactions.AbstractResponseBuilder;
import io.takamaka.code.whitelisting.WhiteListingPredicate;

/**
 * A class that contains utility methods called by instrumented
 * Takamaka code stored in a node. This class is not installed in
 * the node's store, hence it is not visible to Takamaka programmers
 * and needn't obey to Takamaka code constraints. It is only visible
 * at run time, since its package is opened by the module-info descriptor
 * of this module.
 */
public abstract class Runtime {

	/**
	 * Transaction builders spawn a thread when they need to execute code that could
	 * call into this class. This allows the execution of more transactions in parallel
	 * and with distinct class loaders.
	 * This method yields the transaction builder that is using that thread.
	 * 
	 * @return the transaction builder that is using the current thread
	 */
	private static AbstractResponseBuilder<?,?> getBuilder() {
		return getCurrentThread().getBuilder();
	}

	/**
	 * Transaction builders spawn a thread when they need to execute code that could
	 * call into this class. This allows the execution of more transactions in parallel
	 * and with distinct class loaders.
	 * This method yields the transaction builder that is using that thread.
	 * 
	 * @return the transaction builder that is using the current thread
	 */
	private static AbstractResponseBuilder<?,?>.ResponseCreator getResponseCreator() {
		return getCurrentThread().getResponseCreator();
	}

	/**
	 * Transaction builders spawn a thread when they need to execute code that could
	 * call into this class. This allows the execution of more transactions in parallel
	 * and with distinct class loaders. This method yields that thread.
	 * 
	 * @return the current thread
	 */
	private static AbstractResponseBuilder<?,?>.ResponseCreator.TakamakaThread getCurrentThread() {
		return (AbstractResponseBuilder<?,?>.ResponseCreator.TakamakaThread) Thread.currentThread();
	}
	/**
	 * Yields the last value assigned to the given lazy, non-{@code final} field of the given storage object.
	 * 
	 * @param object the container of the field
	 * @param definingClass the class of the field. This can only be the class of {@code object}
	 *                      or one of its superclasses
	 * @param name the name of the field
	 * @param fieldClassName the name of the type of the field
	 * @return the value of the field
	 * @throws Exception if the value could not be found
	 */
	public static Object deserializeLastLazyUpdateFor(Object object, String definingClass, String name, String fieldClassName) throws Exception {
		AbstractResponseBuilder<?,?> builder = getBuilder();
		return getResponseCreator().deserializeLastLazyUpdateFor(builder.getClassLoader().getStorageReferenceOf(object), FieldSignature.mk(definingClass, name, ClassType.mk(fieldClassName)));
	}

	/**
	 * Yields the last value assigned to the given lazy, {@code final} field of the given storage object.
	 * 
	 * @param object the container of the field
	 * @param definingClass the class of the field. This can only be the class of {@code object}
	 *                      or one of its superclasses
	 * @param name the name of the field
	 * @param fieldClassName the name of the type of the field
	 * @return the value of the field
	 * @throws Exception if the value could not be found
	 */
	public static Object deserializeLastLazyUpdateForFinal(Object object, String definingClass, String name, String fieldClassName) throws Exception {
		AbstractResponseBuilder<?,?> builder = getBuilder();
		return getResponseCreator().deserializeLastLazyUpdateForFinal(builder.getClassLoader().getStorageReferenceOf(object), FieldSignature.mk(definingClass, name, ClassType.mk(fieldClassName)));
	}

	/**
	 * Called at the beginning of the instrumentation of an entry method or constructor
	 * of a contract. It forwards the call to {@code io.takamaka.code.lang.Contract.entry()}.
	 * 
	 * @param callee the contract whose entry is called
	 * @param caller the caller of the entry
	 * @throws any possible exception thrown inside {@code io.takamaka.code.lang.Contract.entry()}
	 */
	public static void entry(Object callee, Object caller) throws Throwable {
		getBuilder().getClassLoader().entry(callee, caller);
	}

	/**
	 * Called at the beginning of the instrumentation of a payable entry method or constructor.
	 * It forwards the call to {@code io.takamaka.code.lang.Contract.payableEntry()}.
	 * 
	 * @param callee the contract whose entry is called
	 * @param caller the caller of the entry
	 * @param amount the amount of coins
	 * @throws any possible exception thrown inside {@code io.takamaka.code.lang.Contract.payableEntry()}
	 */
	public static void payableEntry(Object callee, Object caller, BigInteger amount) throws Throwable {
		getBuilder().getClassLoader().payableEntry(callee, caller, amount);
	}

	/**
	 * Called at the beginning of the instrumentation of a red payable entry method or constructor.
	 * It forwards the call to {@code io.takamaka.code.lang.Contract.entry()} and then to
	 * {@code io.takamaka.code.lang.RedGreenContract.redPayable()}.
	 * 
	 * @param callee the contract whose entry is called
	 * @param caller the caller of the entry
	 * @param amount the amount of coins
	 * @throws any possible exception thrown inside or {@code io.takamaka.code.lang.Contract.entry()}
	 *         or {@code io.takamaka.code.lang.RedGreenContract.redPayable()}
	 */
	public static void redPayableEntry(Object callee, Object caller, BigInteger amount) throws Throwable {
		getBuilder().getClassLoader().redPayableEntry(callee, caller, amount);
	}

	/**
	 * Called at the beginning of the instrumentation of a payable entry method or constructor.
	 * It forwards the call to {@code io.takamaka.code.lang.Contract.payableEntry()}.
	 * 
	 * @param callee the contract whose entry is called
	 * @param caller the caller of the entry
	 * @param amount the amount of coins
	 * @throws any possible exception thrown inside {@code io.takamaka.code.lang.Contract.entry()}
	 */
	public static void payableEntry(Object callee, Object caller, int amount) throws Throwable {
		getBuilder().getClassLoader().payableEntry(callee, caller, amount);
	}

	/**
	 * Called at the beginning of the instrumentation of a red payable entry method or constructor.
	 * It forwards the call to {@code io.takamaka.code.lang.Contract.entry()} and then to
	 * {@code io.takamaka.code.lang.RedGreenContract.redPayable()}.
	 * 
	 * @param callee the contract whose entry is called
	 * @param caller the caller of the entry
	 * @param amount the amount of coins
	 * @throws any possible exception thrown inside or {@code io.takamaka.code.lang.Contract.entry()}
	 *         or {@code io.takamaka.code.lang.RedGreenContract.redPayable()}
	 */
	public static void redPayableEntry(Object callee, Object caller, int amount) throws Throwable {
		getBuilder().getClassLoader().redPayableEntry(callee, caller, amount);
	}

	/**
	 * Called at the beginning of the instrumentation of a payable entry method or constructor.
	 * It forwards the call to {@code io.takamaka.code.lang.Contract.payableEntry()}.
	 * 
	 * @param callee the contract whose entry is called
	 * @param caller the caller of the entry
	 * @param amount the amount of coins
	 * @throws any possible exception thrown inside {@code io.takamaka.code.lang.Contract.entry()}
	 */
	public static void payableEntry(Object callee, Object caller, long amount) throws Throwable {
		getBuilder().getClassLoader().payableEntry(callee, caller, amount);
	}

	/**
	 * Called at the beginning of the instrumentation of a red payable entry method or constructor.
	 * It forwards the call to {@code io.takamaka.code.lang.Contract.entry()} and then to
	 * {@code io.takamaka.code.lang.RedGreenContract.redPayable()}.
	 * 
	 * @param callee the contract whose entry is called
	 * @param caller the caller of the entry
	 * @param amount the amount of coins
	 * @throws any possible exception thrown inside or {@code io.takamaka.code.lang.Contract.entry()}
	 *         or {@code io.takamaka.code.lang.RedGreenContract.redPayable()}
	 */
	public static void redPayableEntry(Object callee, Object caller, long amount) throws Throwable {
		getBuilder().getClassLoader().redPayableEntry(callee, caller, amount);
	}

	/**
	 * Takes note of the given event.
	 *
	 * @param event the event
	 */
	public static void event(Object event) {
		getResponseCreator().event(event);
	}

	/**
	 * Runs a given piece of code with a subset of the available gas.
	 * It first charges the given amount of gas. Then runs the code
	 * with the charged gas only. At its end, the remaining gas is added
	 * to the available gas to continue the computation.
	 * 
	 * @param amount the amount of gas provided to the code
	 * @param what the code to run
	 * @return the result of the execution of {@code what}
	 * @throws OutOfGasError if there is not enough gas
	 * @throws Exception if the code runs into this exception
	 */
	public static <T> T withGas(BigInteger amount, Callable<T> what) throws Exception {
		return getBuilder().withGas(amount, what);
	}

	/**
	 * Yields the execution time of the transaction.
	 * 
	 * @return the time
	 */
	public static long now() {
		return getCurrentThread().now();
	}

	/**
	 * Yields the value of field {@code inStorage} of the given storage object.
	 * 
	 * @param object the storage object
	 * @return the value of the field
	 */
	public static boolean inStorageOf(Object object) {
		return getBuilder().getClassLoader().getInStorageOf(object);
	}

	/**
	 * Compares the storage references of the given two storage objects, according
	 * to the natural ordering of storage references.
	 * 
	 * @param o1 the first storage object
	 * @param o2 the second storage object
	 * @return the result of the comparison
	 */
	public static int compareStorageReferencesOf(Object o1, Object o2) {
		if (o1 == o2)
			return 0;
		else if (o1 == null)
			return -1;
		else if (o2 == null)
			return 1;
		else {
			EngineClassLoader classLoader = getBuilder().getClassLoader();
			return classLoader.getStorageReferenceOf(o1).compareTo(classLoader.getStorageReferenceOf(o2));
		}
	}

	/**
	 * Yields the next storage reference for the current transaction.
	 * It can be used to refer to a newly created object.
	 * 
	 * @return the next storage reference
	 */
	public static Object getNextStorageReference() {
		return getCurrentThread().getNextStorageReference();
	}

	/**
	 * Checks if the given white-listing predicate is satisfied by the given value.
	 * 
	 * @param value the value to check against the predicate
	 * @param predicateClass the class of the predicate to check
	 * @param methodName the name of the method or constructor to which {@code value} is passed as parameter
	 */
	public static void checkWhiteListingPredicate(Object value, Class<? extends WhiteListingPredicate> predicateClass, String methodName) {
		try {
			WhiteListingPredicate predicate = predicateClass.getConstructor().newInstance();
			if (!predicate.test(value, getBuilder().getClassLoader().getWhiteListingWizard()))
				throw new NonWhiteListedCallException(predicate.messageIfFailed(methodName));
		}
		catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException e) {
			throw new IllegalStateException(e);
		}
	}

	/**
	 * Charges the given amount of gas for RAM usage for the current transaction.
	 * 
	 * @param ram the amount of gas to consume for RAM consumption
	 */
	public static void chargeForRAM(BigInteger ram) {
		getBuilder().chargeGasForRAM(ram);
	}

	/**
	 * Charges the given amount of gas for RAM usage for the current transaction.
	 * 
	 * @param ram the amount of gas to consume for RAM consumption
	 */
	public static void chargeForRAM(long ram) {
		getBuilder().chargeGasForRAM(BigInteger.valueOf(ram));
	}

	/**
	 * Charges the given amount of gas for RAM usage for the current transaction.
	 * 
	 * @param ram the amount of gas to consume for RAM consumption
	 */
	public static void chargeForRAM(int ram) {
		getBuilder().chargeGasForRAM(BigInteger.valueOf(ram));
	}

	/**
	 * Charges the given amount of gas for CPU usage for the current transaction.
	 * 
	 * @param cpu the amount of gas to consume
	 */
	public static void charge(long cpu) {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(cpu));
	}

	/**
	 * Charges the given amount of gas for CPU usage for the current transaction.
	 * 
	 * @param cpu the amount of gas to consume
	 */
	public static void charge(int cpu) {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(cpu));
	}

	/**
	 * Charges one unit of gas for CPU usage for the current transaction.
	 */
	public static void charge1() {
		getBuilder().chargeGasForCPU(BigInteger.ONE);
	}

	/**
	 * Charges two units of gas for CPU usage for the current transaction.
	 */
	public static void charge2() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(2L));
	}

	/**
	 * Charges three units of gas for CPU usage for the current transaction.
	 */
	public static void charge3() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(3L));
	}

	/**
	 * Charges four units of gas for CPU usage for the current transaction.
	 */
	public static void charge4() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(4L));
	}

	/**
	 * Charges five units of gas for CPU usage for the current transaction.
	 */
	public static void charge5() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(5L));
	}

	/**
	 * Charges six units of gas for CPU usage for the current transaction.
	 */
	public static void charge6() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(6L));
	}

	/**
	 * Charges seven units of gas for CPU usage for the current transaction.
	 */
	public static void charge7() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(7L));
	}

	/**
	 * Charges eight units of gas for CPU usage for the current transaction.
	 */
	public static void charge8() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(8L));
	}

	/**
	 * Charges nine units of gas for CPU usage for the current transaction.
	 */
	public static void charge9() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(9L));
	}

	/**
	 * Charges ten units of gas for CPU usage for the current transaction.
	 */
	public static void charge10() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(10L));
	}

	/**
	 * Charges eleven units of gas for CPU usage for the current transaction.
	 */
	public static void charge11() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(11L));
	}

	/**
	 * Charges twelve units of gas for CPU usage for the current transaction.
	 */
	public static void charge12() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(12L));
	}

	/**
	 * Charges 13 units of gas for CPU usage for the current transaction.
	 */
	public static void charge13() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(13L));
	}

	/**
	 * Charges 14 units of gas for CPU usage for the current transaction.
	 */
	public static void charge14() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(14L));
	}

	/**
	 * Charges 15 units of gas for CPU usage for the current transaction.
	 */
	public static void charge15() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(15L));
	}

	/**
	 * Charges 16 units of gas for CPU usage for the current transaction.
	 */
	public static void charge16() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(16L));
	}

	/**
	 * Charges 17 units of gas for CPU usage for the current transaction.
	 */
	public static void charge17() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(17L));
	}

	/**
	 * Charges 18 units of gas for CPU usage for the current transaction.
	 */
	public static void charge18() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(18L));
	}

	/**
	 * Charges 19 units of gas for CPU usage for the current transaction.
	 */
	public static void charge19() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(19L));
	}

	/**
	 * Charges 20 units of gas for CPU usage for the current transaction.
	 */
	public static void charge20() {
		getBuilder().chargeGasForCPU(BigInteger.valueOf(20L));
	}
}