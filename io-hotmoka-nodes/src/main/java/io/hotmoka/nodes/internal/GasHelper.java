package io.hotmoka.nodes.internal;

import java.math.BigInteger;

import io.hotmoka.beans.CodeExecutionException;
import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.signatures.CodeSignature;
import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.BooleanValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.nodes.Node;

/**
 * An object that helps with gas operations.
 */
class GasHelper {
	private final Node node;
	private final StorageReference gasStation;

	/**
	 * Creates an object that helps with gas operations.
	 * 
	 * @param node the node whose gas is considered
	 */
	GasHelper(Node node) throws TransactionRejectedException, TransactionException, CodeExecutionException {
		this.node = node;

		TransactionReference takamakaCode = node.getTakamakaCode();
		StorageReference manifest = node.getManifest();
		BigInteger _10_000 = BigInteger.valueOf(10_000);

		this.gasStation = (StorageReference) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
			(manifest, _10_000, takamakaCode, CodeSignature.GET_GAS_STATION, manifest));
	}

	/**
	 * Yields the gas price for a transaction.
	 * 
	 * @return the gas price
	 */
	BigInteger getGasPrice() throws TransactionRejectedException, TransactionException, CodeExecutionException {
		TransactionReference takamakaCode = node.getTakamakaCode();
		StorageReference manifest = node.getManifest();
		BigInteger _10_000 = BigInteger.valueOf(10_000);

		boolean ignoresGasPrice = ((BooleanValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
			(manifest, _10_000, takamakaCode, CodeSignature.IGNORES_GAS_PRICE, gasStation))).value;

		// this helps with testing, since otherwise previous tests might make the gas price explode for the subsequent tests
		if (ignoresGasPrice)
			return BigInteger.ONE;

		BigInteger minimalGasPrice = ((BigIntegerValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
			(manifest, _10_000, takamakaCode, CodeSignature.GET_GAS_PRICE, gasStation))).value;

		// we double the minimal price, to be sure that the transaction won't be rejected
		return BigInteger.TWO.multiply(minimalGasPrice);
	}
}