package io.takamaka.code.engine.internal.transactions;

import java.lang.reflect.Field;
import java.math.BigInteger;

import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.GameteCreationTransactionRequest;
import io.hotmoka.beans.responses.GameteCreationTransactionResponse;
import io.takamaka.code.engine.IllegalTransactionRequestException;
import io.takamaka.code.engine.Node;
import io.takamaka.code.engine.internal.EngineClassLoader;
import io.takamaka.code.verification.TakamakaClassLoader;

public class GameteCreationTransactionRun extends AbstractTransactionRun<GameteCreationTransactionRequest, GameteCreationTransactionResponse> {

	public GameteCreationTransactionRun(GameteCreationTransactionRequest request, TransactionReference current, Node node) throws TransactionException {
		super(request, current, node, BigInteger.valueOf(-1L)); // we do not count gas for this creation
	}

	@Override
	protected GameteCreationTransactionResponse computeResponse() throws Exception {
		if (request.initialAmount.signum() < 0)
			throw new IllegalTransactionRequestException("The gamete must be initialized with a non-negative amount of coins");

		try (TakamakaClassLoader classLoader = this.classLoader = new EngineClassLoader(request.classpath, this)) {
			// we create an initial gamete ExternallyOwnedContract and we fund it with the initial amount
			Object gamete = classLoader.getExternallyOwnedAccount().getDeclaredConstructor().newInstance();
			// we set the balance field of the gamete
			Field balanceField = classLoader.getContract().getDeclaredField("balance");
			balanceField.setAccessible(true); // since the field is private
			balanceField.set(gamete, request.initialAmount);

			return new GameteCreationTransactionResponse(collectUpdates(null, null, null, gamete).stream(), getStorageReferenceOf(gamete));
		}
	}
}