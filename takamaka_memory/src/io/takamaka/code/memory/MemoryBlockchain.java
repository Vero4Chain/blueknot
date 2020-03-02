package io.takamaka.code.memory;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;

import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.references.Classpath;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.nodes.CodeExecutionException;
import io.hotmoka.nodes.SequentialNode;
import io.takamaka.code.memory.internal.MemoryBlockchainImpl;

/**
 * An implementation of a blockchain that stores, sequentially, transactions in a directory
 * on disk memory. It is only meant for experimentation and testing. It is not
 * really a blockchain, since there is no peer-to-peer network, nor mining.
 * Updates are stored inside the blocks, rather than in an external database.
 * It provides support for the creation of a given number of initial accounts.
 */
public interface MemoryBlockchain extends SequentialNode {

	/**
	 * Yields a blockchain in disk memory and initializes user accounts with the given initial funds.
	 * 
	 * @param takamakaCodePath the path where the base Takamaka classes can be found. They will be
	 *                         installed in blockchain and will be available later as {@link io.takamaka.code.memory.MemoryBlockchain#takamakaCode()}
	 * @param funds the initial funds of the accounts that are created
	 * @throws IOException if a disk error occurs
	 * @throws TransactionException if some transaction for initialization fails
	 * @throws CodeExecutionException if some transaction for initialization throws an exception
	 */
	static MemoryBlockchain of(Path takamakaCodePath, BigInteger... funds) throws IOException, TransactionException, CodeExecutionException {
		return new MemoryBlockchainImpl(takamakaCodePath, funds);
	}

	/**
	 * The number of transactions that fit inside a block.
	 */
	final static int TRANSACTIONS_PER_BLOCK = 5;

	/**
	 * Yields the reference, in the blockchain, where the base Takamaka classes have been installed.
	 */
	Classpath takamakaCode();

	/**
	 * Yields the {@code i}th account.
	 * 
	 * @param i the account number
	 * @return the reference to the account, in blockchain. This is a {@link #io.takamaka.code.lang.TestExternallyOwnedAccount}}
	 */
	StorageReference account(int i);
}