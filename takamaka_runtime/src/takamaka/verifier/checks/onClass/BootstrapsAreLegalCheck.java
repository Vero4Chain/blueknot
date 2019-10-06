package takamaka.verifier.checks.onClass;

import takamaka.verifier.VerifiedClassGen;
import takamaka.verifier.errors.IllegalBootstrapMethodError;

/**
 * A check that lambda bootstraps are only among those allowed by Takamaka.
 */
public class BootstrapsAreLegalCheck extends VerifiedClassGen.Verification.Check {

	public BootstrapsAreLegalCheck(VerifiedClassGen.Verification verification) {
		verification.super();

		clazz.getClassBootstraps().getBootstraps()
			.map(clazz.getClassBootstraps()::getTargetOf)
			.filter(target -> !target.isPresent())
			.findAny()
			.ifPresent(target -> issue(new IllegalBootstrapMethodError(clazz)));
	}
}