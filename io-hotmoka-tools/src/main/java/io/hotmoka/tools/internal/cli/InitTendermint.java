package io.hotmoka.tools.internal.cli;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.hotmoka.beans.CodeExecutionException;
import io.hotmoka.beans.TransactionException;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.nodes.ConsensusParams;
import io.hotmoka.nodes.ManifestHelper;
import io.hotmoka.nodes.views.InitializedNode;
import io.hotmoka.service.NodeService;
import io.hotmoka.service.NodeServiceConfig;
import io.hotmoka.tendermint.TendermintBlockchain;
import io.hotmoka.tendermint.TendermintBlockchainConfig;
import io.hotmoka.tendermint.views.TendermintInitializedNode;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "init-tendermint",
	description = "Initializes a new Hotmoka node based on Tendermint",
	showDefaultValues = true)
public class InitTendermint extends AbstractCommand {

	@Option(names = { "--balance" }, description = "sets the initial balance of the gamete", defaultValue = "0")
    private BigInteger balance;

	@Option(names = { "--balance-red" }, description = "sets the initial red balance of the gamete", defaultValue = "0")
    private BigInteger balanceRed;

	@Option(names = { "--open-unsigned-faucet" }, description = "opens the unsigned faucet of the gamete") 
	private boolean openUnsignedFaucet;

	@Option(names = { "--non-interactive" }, description = "runs in non-interactive mode") 
	private boolean nonInteractive;

	@Override
	public void run() {
		try {
			new Run();
		}
		catch (Exception e) {
			throw new CommandException(e);
		}
	}

	private class Run {
		private final ConsensusParams consensus;
		private final NodeServiceConfig networkConfig;
		private final TendermintBlockchainConfig nodeConfig;
		private final TendermintBlockchain node;
		private final InitializedNode initialized;

		private Run() throws Exception {
			askForConfirmation();

			nodeConfig = new TendermintBlockchainConfig.Builder()
				.setTendermintConfigurationToClone(Paths.get("io-hotmoka-tools/tendermint_configs/v1n0/node0"))
				.build();

			networkConfig = new NodeServiceConfig.Builder()
				.setSpringBannerModeOn(false)
				.build();

			consensus = new ConsensusParams.Builder()
				.allowUnsignedFaucet(openUnsignedFaucet)
				.build();

			Path takamakaCodeJar = Paths.get("modules/explicit/io-takamaka-code-1.0.0.jar");

			try (TendermintBlockchain node = this.node = TendermintBlockchain.init(nodeConfig, consensus);
				InitializedNode initialized = this.initialized = TendermintInitializedNode.of(node, consensus, takamakaCodeJar, balance, balanceRed);
				NodeService service = NodeService.of(networkConfig, node)) {

				printManifest();
				printBanner();
				dumpKeysOfGamete();
				waitForEnterKey();
			}
		}

		private void askForConfirmation() {
			if (!nonInteractive) {
				System.out.print("Do you really want to start a new node at this place (old blocks and store will be lost) [Y/N] ");
				String answer = System.console().readLine();
				if (!"Y".equals(answer))
					System.exit(0);
			}
		}

		private void waitForEnterKey() {
			System.out.println("Press enter to exit this program and turn off the node");
			System.console().readLine();
		}

		private void printBanner() {
			System.out.println("The Hotmoka node has been published at localhost:" + networkConfig.port);
			System.out.println("Try for instance in a browser: http://localhost:" + networkConfig.port + "/get/manifest");
		}

		private void printManifest() throws TransactionRejectedException, TransactionException, CodeExecutionException {
			System.out.println("\n" + new ManifestHelper(node));
		}

		private void dumpKeysOfGamete() throws FileNotFoundException, IOException {
			String fileName = dumpKeys(initialized.gamete(), initialized.keysOfGamete());
			System.out.println("\nThe keys of the gamete have been saved into the file " + fileName + "\n");
		}
	}
}