package io.hotmoka.tendermint.runs;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.stream.Stream;

import io.hotmoka.nodes.InitializedNode;
import io.hotmoka.tendermint.Config;
import io.hotmoka.tendermint.TendermintBlockchain;

public class StartNode {

	public static void main(String[] args) throws Exception {
		Config config = new Config.Builder().setDelete(false).build();

		System.out.println("usage: THIS_PROGRAM n t takamakaCode");
		System.out.println("  runs the n-th (1 to t) node over t");
		System.out.println("  installs takamakaCode inside the node");

		Integer n = Integer.valueOf(args[0]);
		Integer t = Integer.valueOf(args[1]);
		Path takamakaCode;
		if (args.length > 2)
			takamakaCode = Paths.get(args[2]);
		else
			takamakaCode = null;

		System.out.println("Starting node " + n + " of " + t);
		if (takamakaCode != null)
			System.out.println("Installing " + takamakaCode + " in it");

		// we delete the blockchain directory
		deleteRecursively(config.dir);

		// we replace the blockchain directory with the initialized data for the node
		Files.createDirectories(config.dir);

		copyRecursively(Paths.get(t + "-nodes").resolve("node" + (n - 1)), config.dir.resolve("blocks"));

		if (takamakaCode != null) {
			try (TendermintBlockchain blockchain = TendermintBlockchain.of(config, takamakaCode);
				 InitializedNode node = InitializedNode.of(blockchain, BigInteger.valueOf(200_000), BigInteger.valueOf(200_000), BigInteger.valueOf(200_000))) {

				while (true) {
					System.out.println(node.takamakaCode());
					Thread.sleep(1000);
				}
			}
		}
		else {
			try (TendermintBlockchain node = TendermintBlockchain.of(config)) {
				while (true) {
					System.out.println(node.takamakaCode());
					Thread.sleep(1000);
				}
			}
		}
	}

	/**
	 * Deletes the given directory, recursively.
	 * 
	 * @param dir the directory to delete
	 * @throws IOException if the directory or some of its subdirectories cannot be deleted
	 */
	private static void deleteRecursively(Path dir) throws IOException {
		if (Files.exists(dir))
			Files.walk(dir)
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
	}

	private static void copyRecursively(Path src, Path dest) throws IOException {
	    try (Stream<Path> stream = Files.walk(src)) {
	        stream.forEach(source -> copy(source, dest.resolve(src.relativize(source))));
	    }
	    catch (UncheckedIOException e) {
	    	throw e.getCause();
	    }
	}

	private static void copy(Path source, Path dest) {
		try {
			Files.copy(source, dest);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}