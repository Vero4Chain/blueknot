package io.hotmoka.tendermint.runs;

import java.math.BigInteger;
import java.nio.file.Paths;
import java.security.PrivateKey;

import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.nodes.Node;
import io.hotmoka.nodes.views.InitializedNode;
import io.hotmoka.nodes.views.NodeWithAccounts;
import io.hotmoka.tendermint.TendermintBlockchainConfig;
import io.hotmoka.tendermint.TendermintBlockchain;
import io.takamaka.code.constants.Constants;

/**
 * Creates a brand new blockchain and recreates it repeatedly, checking that the previous
 * state is available after each recreation.
 * 
 * This class is meant to be run from the parent directory, after building the project,
 * with this command-line:
 * 
 * java --module-path modules/explicit:modules/automatic --class-path "modules/unnamed/*" --module io.hotmoka.tendermint/io.hotmoka.tendermint.runs.MainRepeatedRecreations
 */
public class MainRepeatedRecreations {

	private static final BigInteger _2_000_000 = BigInteger.valueOf(2_000_000);
	private static final BigInteger _100 = BigInteger.valueOf(100);

	/**
	 * Initial green stake.
	 */
	private final static BigInteger GREEN = BigInteger.valueOf(999_999_999).pow(5);

	/**
	 * Initial red stake.
	 */
	private final static BigInteger RED = BigInteger.valueOf(999_999_999).pow(5);

	public static void main(String[] args) throws Exception {
		TendermintBlockchainConfig config = new TendermintBlockchainConfig.Builder().build();
		StorageReference account;
		PrivateKey privateKey;
		StorageReference newAccount;

		try (Node node = TendermintBlockchain.of(config)) {
			// update version number when needed
			InitializedNode initializedView = InitializedNode.of
				(node, Paths.get("modules/explicit/io-takamaka-code-1.0.0.jar"),
				Constants.MANIFEST_NAME, MainRepeatedRecreations.class.getName(), GREEN, RED);
			NodeWithAccounts viewWithAccounts = NodeWithAccounts.of(initializedView, initializedView.keysOfGamete().getPrivate(), _2_000_000);
			System.out.println("takamakaCode: " + viewWithAccounts.getTakamakaCode());
			account = newAccount = viewWithAccounts.account(0);
			privateKey = viewWithAccounts.privateKey(0);
		}

		config = new TendermintBlockchainConfig.Builder()
			.setDelete(false) // reuse the state already created by a previous execution
			.build();

		for (int i = 0; i < 100; i++)
			try (Node node = TendermintBlockchain.of(config)) {
				// before creating a new account, we check if the previously created is still accessible
				node.getState(newAccount).forEach(System.out::println);
				newAccount = NodeWithAccounts.of(node, account, privateKey, _100).account(0);
				System.out.println("done #" + i);
			}
			catch (Exception e) {
				e.printStackTrace();
				break;
			}
	}
}