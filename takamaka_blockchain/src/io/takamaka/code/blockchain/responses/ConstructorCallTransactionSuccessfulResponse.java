package io.takamaka.code.blockchain.responses;

import java.math.BigInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.hotmoka.beans.annotations.Immutable;
import io.hotmoka.beans.updates.Update;
import io.hotmoka.beans.values.StorageReference;

/**
 * A response for a successful transaction that calls a constructor of a storage
 * class in blockchain. The constructor has been called without problems and
 * without generating exceptions.
 */
@Immutable
public class ConstructorCallTransactionSuccessfulResponse extends ConstructorCallTransactionResponse implements TransactionResponseWithEvents {

	private static final long serialVersionUID = -7514325398187177242L;

	/**
	 * The updates resulting from the execution of the transaction.
	 */
	private final Update[] updates;

	/**
	 * The events generated by this transaction.
	 */
	private final StorageReference[] events;

	/**
	 * The object that has been created by the constructor call.
	 */
	public final StorageReference newObject;

	/**
	 * Builds the transaction response.
	 * 
	 * @param newObject the object that has been successfully created
	 * @param updates the updates resulting from the execution of the transaction
	 * @param events the events resulting from the execution of the transaction
	 * @param gasConsumedForCPU the amount of gas consumed by the transaction for CPU execution
	 * @param gasConsumedForRAM the amount of gas consumed by the transaction for RAM allocation
	 * @param gasConsumedForStorage the amount of gas consumed by the transaction for storage consumption
	 */
	public ConstructorCallTransactionSuccessfulResponse(StorageReference newObject, Stream<Update> updates, Stream<StorageReference> events, BigInteger gasConsumedForCPU, BigInteger gasConsumedForRAM, BigInteger gasConsumedForStorage) {
		super(gasConsumedForCPU, gasConsumedForRAM, gasConsumedForStorage);

		this.newObject = newObject;
		this.updates = updates.toArray(Update[]::new);
		this.events = events.toArray(StorageReference[]::new);
	}

	@Override
	public String toString() {
        return super.toString() + "\n"
       		+ "  new object: " + newObject + "\n"
        	+ "  events:\n" + getEvents().map(StorageReference::toString).collect(Collectors.joining("\n    ", "    ", ""));
	}

	@Override
	public final Stream<Update> getUpdates() {
		return Stream.of(updates);
	}

	@Override
	public Stream<StorageReference> getEvents() {
		return Stream.of(events);
	}
}