package io.takamaka.code.blockchain.responses;

import java.math.BigInteger;
import java.util.stream.Collectors;

import io.takamaka.code.blockchain.GasCostModel;
import io.takamaka.code.blockchain.annotations.Immutable;
import io.takamaka.code.blockchain.updates.Update;

/**
 * A response for a transaction that should call a constructor of a storage class in blockchain.
 */
@Immutable
public abstract class ConstructorCallTransactionResponse implements TransactionResponseWithGas, TransactionResponseWithUpdates {

	private static final long serialVersionUID = 6999069256965379003L;

	/**
	 * The amount of gas consumed by the transaction for CPU execution.
	 */
	private final BigInteger gasConsumedForCPU;

	/**
	 * The amount of gas consumed by the transaction for RAM allocation.
	 */
	private final BigInteger gasConsumedForRAM;

	/**
	 * The amount of gas consumed by the transaction for storage consumption.
	 */
	private final BigInteger gasConsumedForStorage;

	/**
	 * Builds the transaction response.
	 * 
	 * @param gasConsumedForCPU the amount of gas consumed by the transaction for CPU execution
	 * @param gasConsumedForRAM the amount of gas consumed by the transaction for RAM allocation
	 * @param gasConsumedForStorage the amount of gas consumed by the transaction for storage consumption
	 */
	public ConstructorCallTransactionResponse(BigInteger gasConsumedForCPU, BigInteger gasConsumedForRAM, BigInteger gasConsumedForStorage) {
		this.gasConsumedForCPU = gasConsumedForCPU;
		this.gasConsumedForRAM = gasConsumedForRAM;
		this.gasConsumedForStorage = gasConsumedForStorage;
	}

	@Override
	public String toString() {
        return getClass().getSimpleName() + ":\n" + gasToString()
        	+ "  updates:\n" + getUpdates().map(Update::toString).collect(Collectors.joining("\n    ", "    ", ""));
	}

	/**
	 * Yields a description of the gas consumption.
	 * 
	 * @return the description
	 */
	protected String gasToString() {
		return "  gas consumed for CPU execution: " + gasConsumedForCPU + "\n"
			+ "  gas consumed for RAM allocation: " + gasConsumedForRAM + "\n"
	        + "  gas consumed for storage consumption: " + gasConsumedForStorage + "\n";
	}

	@Override
	public BigInteger gasConsumedForCPU() {
		return gasConsumedForCPU;
	}

	@Override
	public BigInteger gasConsumedForRAM() {
		return gasConsumedForRAM;
	}

	@Override
	public BigInteger gasConsumedForStorage() {
		return gasConsumedForStorage;
	}

	@Override
	public BigInteger size(GasCostModel gasCostModel) {
		return BigInteger.valueOf(gasCostModel.storageCostPerSlot())
			.add(gasCostModel.storageCostOf(gasConsumedForCPU)).add(gasCostModel.storageCostOf(gasConsumedForRAM))
			.add(gasCostModel.storageCostOf(gasConsumedForStorage))
			.add(getUpdates().map(update -> update.size(gasCostModel)).reduce(BigInteger.ZERO, BigInteger::add));
	}
}