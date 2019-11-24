package io.takamaka.code.blockchain.response;

import java.math.BigInteger;
import java.util.stream.Stream;

import io.takamaka.code.blockchain.GasCosts;
import io.takamaka.code.blockchain.TransactionException;
import io.takamaka.code.blockchain.Update;
import io.takamaka.code.blockchain.UpdateOfBalance;
import io.takamaka.code.blockchain.annotations.Immutable;

/**
 * A response for a failed transaction that should have called a constructor
 * of a storage class in blockchain.
 */
@Immutable
public class ConstructorCallTransactionFailedResponse extends ConstructorCallTransactionResponse implements TransactionResponseFailed {

	private static final long serialVersionUID = 3291328917017257182L;

	/**
	 * The exception that justifies why the transaction failed. This is not reported
	 * in the serialization of this response.
	 */
	public final transient TransactionException cause;

	/**
	 * The update of balance of the caller of the transaction, for paying for the transaction.
	 */
	private final UpdateOfBalance callerBalanceUpdate;

	/**
	 * The amount of gas consumed by the transaction as penalty for the failure.
	 */
	private final BigInteger gasConsumedForPenalty;

	/**
	 * Builds the transaction response.
	 * 
	 * @param cause the exception that justifies why the transaction failed
	 * @param callerBalanceUpdate the update of balance of the caller of the transaction, for paying for the transaction
	 * @param gasConsumedForCPU the amount of gas consumed by the transaction for CPU execution
	 * @param gasConsumedForRAM the amount of gas consumed by the transaction for RAM allocation
	 * @param gasConsumedForStorage the amount of gas consumed by the transaction for storage consumption
	 * @param gasConsumedForPenalty the amount of gas consumed by the transaction as penalty for the failure
	 */
	public ConstructorCallTransactionFailedResponse(TransactionException cause, UpdateOfBalance callerBalanceUpdate, BigInteger gasConsumedForCPU, BigInteger gasConsumedForRAM, BigInteger gasConsumedForStorage, BigInteger gasConsumedForPenalty) {
		super(gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);

		this.cause = cause;
		this.callerBalanceUpdate = callerBalanceUpdate;
		this.gasConsumedForPenalty = gasConsumedForPenalty;
	}

	@Override
	protected String gasToString() {
		return super.gasToString() + "  gas consumed for penalty: " + gasConsumedForPenalty + "\n";
	}

	@Override
	public final Stream<Update> getUpdates() {
		return Stream.of(callerBalanceUpdate);
	}

	@Override
	public BigInteger gasConsumedForPenalty() {
		return gasConsumedForPenalty;
	}

	@Override
	public BigInteger size() {
		return super.size().add(GasCosts.storageCostOf(gasConsumedForPenalty));
	}
}