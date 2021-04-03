package io.takamaka.code.verification.internal.checksOnMethods;

import org.apache.bcel.Const;
import org.apache.bcel.generic.ArrayType;
import org.apache.bcel.generic.CodeExceptionGen;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.ObjectType;
import org.apache.bcel.generic.Type;

import io.takamaka.code.verification.ThrowIncompleteClasspathError;
import io.takamaka.code.verification.internal.CheckOnMethods;
import io.takamaka.code.verification.internal.VerifiedClassImpl;
import io.takamaka.code.verification.issues.UncheckedExceptionHandlerError;

/**
 * A check that the exception handlers of a method are only for checked exceptions.
 */
public class ExceptionHandlersAreForCheckedExceptionsCheck extends CheckOnMethods {

	public ExceptionHandlersAreForCheckedExceptionsCheck(VerifiedClassImpl.Verification builder, MethodGen method) {
		super(builder, method);

		for (CodeExceptionGen exc: method.getExceptionHandlers()) {
			ObjectType catchType = exc.getCatchType();
			String exceptionName = catchType == null ? "java.lang.Throwable" : catchType.getClassName();

			if (canCatchUncheckedExceptions(exceptionName) && !specialCatchInsideEnumInitializer(exceptionName) && !specialCatchInsideSwitchOnEnum(exceptionName))
				issue(new UncheckedExceptionHandlerError(inferSourceFile(), methodName, lineOf(exc.getHandlerPC()), exceptionName));
		}
	}

	/**
	 * enum's are sometimes compiled with synthetic methods that catch NoSuchFieldError.
	 * These handlers must be allowed in Takamaka code.
	 * 
	 * @param exceptionName the name of the caught exception
	 * @return true if the exception is NoSuchFieldError thrown inside the
	 *         static initializer of an enumeration
	 */
	private boolean specialCatchInsideEnumInitializer(String exceptionName) {
		return ((isEnum() && method.isSynthetic())
			|| (Const.STATIC_INITIALIZER_NAME.equals(methodName) && isSynthetic()))
			&& exceptionName.equals("java.lang.NoSuchFieldError");
	}

	/**
	 * A switch on an enum is sometimes compiled with a method that catch NoSuchFieldError.
	 * These handlers must be allowed in Takamaka code. The method is not even marked as synthetic
	 * by most compilers.
	 * 
	 * @param exceptionName the name of the caught exception
	 * @return true if the exception is NoSuchFieldError thrown inside the
	 *         method for a switch on an enumeration
	 */
	private boolean specialCatchInsideSwitchOnEnum(String exceptionName) {
		return !method.isPublic() && exceptionName.equals("java.lang.NoSuchFieldError")
			&& methodName.startsWith("$SWITCH_TABLE$")
			&& methodReturnType instanceof ArrayType && ((ArrayType) methodReturnType).getBasicType() == Type.INT;
	}

	private boolean canCatchUncheckedExceptions(String exceptionName) {
		return ThrowIncompleteClasspathError.insteadOfClassNotFoundException(() -> {
			Class<?> clazz = classLoader.loadClass(exceptionName);
			return RuntimeException.class.isAssignableFrom(clazz) || clazz.isAssignableFrom(RuntimeException.class) ||
				java.lang.Error.class.isAssignableFrom(clazz) || clazz.isAssignableFrom(java.lang.Error.class);
		});
	}
}