/*
Copyright 2021 Fausto Spoto

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package io.hotmoka.tools.internal.moka;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Scanner;

import io.hotmoka.beans.CodeExecutionException;
import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest;
import io.hotmoka.beans.requests.TransactionRequest;
import io.hotmoka.beans.signatures.CodeSignature;
import io.hotmoka.beans.values.StringValue;
import io.hotmoka.crypto.Account;
import io.hotmoka.crypto.SignatureAlgorithm;
import io.hotmoka.nodes.Node;
import io.hotmoka.remote.RemoteNodeConfig;
import io.hotmoka.views.SignatureHelper;

public abstract class AbstractCommand implements Runnable {
	protected static final BigInteger _100_000 = BigInteger.valueOf(100_000L);
	protected static final String ANSI_RESET = "\u001B[0m";
	protected static final String ANSI_BLACK = "\u001B[30m";
	protected static final String ANSI_RED = "\u001B[31m";
	protected static final String ANSI_GREEN = "\u001B[32m";
	protected static final String ANSI_YELLOW = "\u001B[33m";
	protected static final String ANSI_BLUE = "\u001B[34m";
	protected static final String ANSI_PURPLE = "\u001B[35m";
	protected static final String ANSI_CYAN = "\u001B[36m";
	protected static final String ANSI_WHITE = "\u001B[37m";

	@Override
	public final void run() {
		try {
			execute();
		}
		catch (CommandException e) {
			throw e;
		}
		catch (Throwable t) {
			throw new CommandException(t);
		}
	}

	protected abstract void execute() throws Exception;

	protected RemoteNodeConfig remoteNodeConfig(String url) {
		return new RemoteNodeConfig.Builder().setURL(url).build();
	}

	/**
	 * Reconstructs the key pair of the given account, from the entropy contained in the PEM file with the name of the account.
	 * Uses the password to reconstruct the key pair and then checks that the reconstructed public key matches the key
	 * in the account stored in the node.
	 * 
	 * @param account the account
	 * @param node the node where the account exists
	 * @param password the password of the account
	 * @return the key pair
	 * @throws IllegalArgumentException if the password is not correct (it does  not match what stored in the account in the node)
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 * @throws ClassNotFoundException
	 * @throws TransactionRejectedException
	 * @throws TransactionException
	 * @throws CodeExecutionException
	 */
	protected KeyPair readKeys(Account account, Node node, String password) throws IOException, NoSuchAlgorithmException, ClassNotFoundException, TransactionRejectedException, TransactionException, CodeExecutionException {
		SignatureAlgorithm<SignedTransactionRequest> algorithm = new SignatureHelper(node).signatureAlgorithmFor(account.reference);
		var keys = account.keys(password, algorithm);
		// we read the public key stored inside the account in the node (it is Base64-encoded)
		String publicKeyAsFound = ((StringValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
			(account.reference, _100_000, node.getTakamakaCode(), CodeSignature.PUBLIC_KEY, account.reference))).value;
		// we compare it with what we reconstruct from entropy and password
		String publicKeyAsGiven = Base64.getEncoder().encodeToString(algorithm.encodingOf(keys.getPublic()));
		if (!publicKeyAsGiven.equals(publicKeyAsFound))
			throw new IllegalArgumentException("Incorrect password");

		return keys;
	}

	protected BigInteger gasForTransactionWhosePayerHasSignature(String signature, Node node) {
		switch (signature) {
		case "ed25519":
		case "sha256dsa":
			return _100_000;
		case "qtesla1":
			return BigInteger.valueOf(300_000L);
		case "qtesla3":
			return BigInteger.valueOf(400_000L);
		case "empty":
			return _100_000;
		default:
			throw new IllegalArgumentException("unknown signature algorithm " + signature);
		}
	}

	/**
	 * A counter of the gas consumed for the execution of a set of requests.
	 */
	private class MyGasCounter extends io.hotmoka.views.GasCounter {

		/**
		 * Creates the counter of the gas consumed for the execution of a set of requests.
		 * 
		 * @param node the node that executed the requests
		 * @param requests the requests
		 */
		public MyGasCounter(Node node, TransactionRequest<?>... requests) {
			super(node, requests);
		}

		@Override
		public String toString() {
			String result = ANSI_CYAN + "Total gas consumed: " + forCPU.add(forRAM).add(forStorage).add(forPenalty) + "\n";
			result += ANSI_GREEN + "  for CPU: " + forCPU + "\n";
			result += "  for RAM: " + forRAM + "\n";
			result += "  for storage: " + forStorage + "\n";
			result += "  for penalty: " + forPenalty + ANSI_RESET;

			return result;
		}
	}

	protected void printCosts(Node node, TransactionRequest<?>... requests) {
		System.out.println(new MyGasCounter(node, requests));
	}

	protected void yesNo(String message) {
		System.out.print(message);
		@SuppressWarnings("resource")
		Scanner keyboard = new Scanner(System.in);
		String answer = keyboard.nextLine();
		if (!"Y".equals(answer))
			throw new CommandException("Stopped");
	}

	protected String askForPassword(String message) {
		System.out.print(message);
		@SuppressWarnings("resource")
		Scanner keyboard = new Scanner(System.in);
		return keyboard.nextLine();
	}

	protected String ensurePassword(String password, String actor, boolean nonInteractive, boolean isFaucet) {
		if (password != null && isFaucet)
			throw new IllegalArgumentException("the password of " + actor + " has no meaning when it is the faucet");
		
		if (password != null && !nonInteractive)
			throw new IllegalArgumentException("the password of " + actor + " can be provided as command switch only in non-interactive mode.");

		if (password == null && !isFaucet)
			if (nonInteractive) {
				System.out.println("Using the empty string as password of " + actor + ".");
				return "";
			}
			else
				return askForPassword("Please specify the password of " + actor + ": ");
		else
			return password;
	}
}