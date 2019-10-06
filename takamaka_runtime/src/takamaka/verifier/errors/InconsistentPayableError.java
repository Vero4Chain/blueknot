package takamaka.verifier.errors;

import org.apache.bcel.generic.ClassGen;

import takamaka.verifier.Error;

public class InconsistentPayableError extends Error {

	public InconsistentPayableError(ClassGen clazz, String methodName, String clazzWhereItWasDefined) {
		super(clazz, methodName, -1, "@Payable is inconsistent with definition of the same method in class " + clazzWhereItWasDefined);
	}
}