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

package io.hotmoka.local.internal;

import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import io.hotmoka.beans.InternalFailureException;
import io.hotmoka.beans.SignatureAlgorithm;
import io.hotmoka.beans.TransactionRejectedException;
import io.hotmoka.beans.references.TransactionReference;
import io.hotmoka.beans.requests.InstanceMethodCallTransactionRequest;
import io.hotmoka.beans.requests.SignedTransactionRequest;
import io.hotmoka.beans.requests.TransactionRequest;
import io.hotmoka.beans.responses.InitializationTransactionResponse;
import io.hotmoka.beans.responses.TransactionResponse;
import io.hotmoka.beans.responses.TransactionResponseWithEvents;
import io.hotmoka.beans.responses.TransactionResponseWithUpdates;
import io.hotmoka.beans.signatures.CodeSignature;
import io.hotmoka.beans.signatures.FieldSignature;
import io.hotmoka.beans.signatures.NonVoidMethodSignature;
import io.hotmoka.beans.types.BasicTypes;
import io.hotmoka.beans.types.ClassType;
import io.hotmoka.beans.updates.UpdateOfString;
import io.hotmoka.beans.values.BigIntegerValue;
import io.hotmoka.beans.values.BooleanValue;
import io.hotmoka.beans.values.IntValue;
import io.hotmoka.beans.values.LongValue;
import io.hotmoka.beans.values.StorageReference;
import io.hotmoka.beans.values.StringValue;
import io.hotmoka.local.AbstractLocalNode;
import io.hotmoka.local.EngineClassLoader;
import io.hotmoka.local.NodeCaches;
import io.hotmoka.nodes.ConsensusParams;

/**
 * An implementation of the caches of a local node.
 */
public class NodeCachesImpl implements NodeCaches {
	protected final static Logger logger = Logger.getLogger(NodeCachesImpl.class.getName());

	private final NodeInternal node;

	/**
	 * The cache for the requests.
	 */
	private final LRUCache<TransactionReference, TransactionRequest<?>> requests;

	/**
	 * The cache for the committed responses.
	 */
	private final LRUCache<TransactionReference, TransactionResponse> responses;

	/**
	 * Cached recent requests that have had their signature checked.
	 * This avoids repeated signature checking in {@link AbstractLocalNode#checkTransaction(TransactionRequest)}
	 * and {@link AbstractLocalNode#deliverTransaction(TransactionRequest)}.
	 */
	private final LRUCache<SignedTransactionRequest, Boolean> checkedSignatures;

	/**
	 * The cache for the class loaders.
	 */
	private final LRUCache<TransactionReference, EngineClassLoader> classLoaders = new LRUCache<>(100, 1000);

	/**
	 * The consensus parameters of the node.
	 */
	private volatile ConsensusParams consensus;

	/**
	 * The reference to the gamete account of the node.
	 */
	private volatile Optional<StorageReference> gamete;

	/**
	 * The reference to the contract that manages the validators of the node.
	 * After each transaction that consumes gas, this contract receives the
	 * price of the gas, that can later be redistributed to the validators.
	 */
	private volatile Optional<StorageReference> validators;

	/**
	 * The reference to the object that manages the versions of the modules of the node.
	 */
	private volatile Optional<StorageReference> versions;

	/**
	 * The reference to the object that computes the cost of the gas.
	 */
	private volatile Optional<StorageReference> gasStation;

	/**
	 * A cache for the current gas price. It gets reset if it changes.
	 */
	private volatile BigInteger gasPrice;

	/**
	 * A cache for the current inflation. It gets reset if it changes.
	 */
	private volatile Long inflation;

	/**
	 * Enough gas for a simple get method.
	 */
	private final static BigInteger _100_000 = BigInteger.valueOf(100_000L);

	/**
	 * Builds the caches for the given node.
	 * 
	 * @param node the node
	 * @param consensus the consensus parameters of the node
	 */
	public NodeCachesImpl(NodeInternal node, ConsensusParams consensus) {
		this.node = node;
		this.requests = new LRUCache<>(100, node.getConfig().requestCacheSize);
		this.responses = new LRUCache<>(100, node.getConfig().responseCacheSize);
		this.checkedSignatures = new LRUCache<>(100, 1000);
		this.validators = Optional.empty();
		this.versions = Optional.empty();
		this.gasStation = Optional.empty();
		this.gamete = Optional.empty();
		this.consensus = consensus;
	}

	@Override
	public final void invalidate() {
		requests.clear();
		responses.clear();
		checkedSignatures.clear();
		classLoaders.clear();
		consensus = null;
		validators = Optional.empty();
		versions = Optional.empty();
		gasStation = Optional.empty();
		gamete = Optional.empty();
		gasPrice = null;
		inflation = null;
	}

	@Override
	public final void invalidateIfNeeded(TransactionResponse response, EngineClassLoader classLoader) {
		if (consensusParametersMightHaveChanged(response, classLoader)) {
			int versionBefore = consensus.verificationVersion;
			logger.info("recomputing the consensus cache since the information in the manifest might have changed");
			recomputeConsensus();
			logger.info("the consensus cache has been recomputed");
			classLoaders.clear();
			if (versionBefore != consensus.verificationVersion)
				logger.info("the version of the verification module has changed from " + versionBefore + " to " + consensus.verificationVersion);
		}

		if (gasPriceMightHaveChanged(response, classLoader)) {
			BigInteger gasPriceBefore = gasPrice;
			logger.info("recomputing the gas price cache since it has changed");
			recomputeGasPrice();
			logger.info("the gas price cache has been recomputed and changed from " + gasPriceBefore + " to " + gasPrice);
		}

		if (inflationMightHaveChanged(response, classLoader)) {
			Long inflationBefore = inflation;
			logger.info("recomputing the inflation cache since it has changed");
			recomputeInflation();
			logger.info("the inflation cache has been recomputed and changed from " + inflationBefore + " to " + inflation);
		}
	}

	@Override
	public final void recomputeConsensus() {
		try {
			StorageReference gasStation = getGasStation().get();
			StorageReference validators = getValidators().get();
			StorageReference versions = getVersions().get();
			TransactionReference takamakaCode = node.getStoreUtilities().getTakamakaCodeUncommitted().get();
			StorageReference manifest = node.getStore().getManifestUncommitted().get();
	
			String genesisTime = ((StringValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_GENESIS_TIME, manifest))).value;

			String chainId = ((StringValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_CHAIN_ID, manifest))).value;
	
			StorageReference gamete = (StorageReference) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_GAMETE, manifest));

			String publicKeyOfGamete = ((StringValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.PUBLIC_KEY, gamete))).value;

			int maxErrorLength = ((IntValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_MAX_ERROR_LENGTH, manifest))).value;

			int maxDependencies = ((IntValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_MAX_DEPENDENCIES, manifest))).value;

			long maxCumulativeSizeOfDependencies = ((LongValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_MAX_CUMULATIVE_SIZE_OF_DEPENDENCIES, manifest))).value;

			boolean allowsSelfCharged = ((BooleanValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.ALLOWS_SELF_CHARGED, manifest))).value;
	
			boolean allowsFaucet = ((BooleanValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.ALLOWS_UNSIGNED_FAUCET, manifest))).value;

			boolean allowsMintBurnFromGamete = ((BooleanValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.ALLOWS_MINT_BURN_FROM_GAMETE, manifest))).value;

			boolean skipsVerification = ((BooleanValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.SKIPS_VERIFICATION, manifest))).value;

			String signature = ((StringValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_SIGNATURE, manifest))).value;

			BigInteger ticketForNewPoll = ((BigIntegerValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_TICKET_FOR_NEW_POLL, validators))).value;

			BigInteger initialGasPrice = ((BigIntegerValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_INITIAL_GAS_PRICE, gasStation))).value;

			BigInteger maxGasPerTransaction = ((BigIntegerValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_MAX_GAS_PER_TRANSACTION, gasStation))).value;
	
			boolean ignoresGasPrice = ((BooleanValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.IGNORES_GAS_PRICE, gasStation))).value;
	
			BigInteger targetGasAtReward = ((BigIntegerValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_TARGET_GAS_AT_REWARD, gasStation))).value;
	
			long oblivion = ((LongValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_OBLIVION, gasStation))).value;
	
			long initialInflation = ((LongValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_INITIAL_INFLATION, validators))).value;

			int verificationVersion = ((IntValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_VERIFICATION_VERSION, versions))).value;

			BigInteger initialSupply = ((BigIntegerValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_INITIAL_SUPPLY, validators))).value;

			BigInteger initialRedSupply = ((BigIntegerValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_INITIAL_RED_SUPPLY, validators))).value;

			BigInteger finalSupply = ((BigIntegerValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, CodeSignature.GET_FINAL_SUPPLY, validators))).value;

			int buyerSurcharge = ((IntValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, new NonVoidMethodSignature(ClassType.VALIDATORS, "getBuyerSurcharge", BasicTypes.INT), validators))).value;

			int slashingForMisbehaving = ((IntValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, new NonVoidMethodSignature(ClassType.VALIDATORS, "getSlashingForMisbehaving", BasicTypes.INT), validators))).value;

			int slashingForNotBehaving = ((IntValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, new NonVoidMethodSignature(ClassType.VALIDATORS, "getSlashingForNotBehaving", BasicTypes.INT), validators))).value;

			int percentStaked = ((IntValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
				(manifest, _100_000, takamakaCode, new NonVoidMethodSignature(ClassType.VALIDATORS, "getPercentStaked", BasicTypes.INT), validators))).value;

			consensus = new ConsensusParams.Builder()
				.setGenesisTime(genesisTime)
				.setChainId(chainId)
				.setMaxGasPerTransaction(maxGasPerTransaction)
				.ignoreGasPrice(ignoresGasPrice)
				.signRequestsWith(signature)
				.setInitialGasPrice(initialGasPrice)
				.setTargetGasAtReward(targetGasAtReward)
				.setOblivion(oblivion)
				.setInitialInflation(initialInflation)
				.setMaxErrorLength(maxErrorLength)
				.setMaxDependencies(maxDependencies)
				.setMaxCumulativeSizeOfDependencies(maxCumulativeSizeOfDependencies)
				.allowSelfCharged(allowsSelfCharged)
				.allowUnsignedFaucet(allowsFaucet)
				.allowMintBurnFromGamete(allowsMintBurnFromGamete)
				.skipVerification(skipsVerification)
				.setVerificationVersion(verificationVersion)
				.setTicketForNewPoll(ticketForNewPoll)
				.setInitialSupply(initialSupply)
				.setFinalSupply(finalSupply)
				.setInitialRedSupply(initialRedSupply)
				.setPublicKeyOfGamete(publicKeyOfGamete)
				.setPercentStaked(percentStaked)
				.setBuyerSurcharge(buyerSurcharge)
				.setSlashingForMisbehaving(slashingForMisbehaving)
				.setSlashingForNotBehaving(slashingForNotBehaving)
				.build();
		}
		catch (Throwable t) {
			logger.log(Level.SEVERE, "could not reconstruct the consensus parameters from the manifest", t);
			throw InternalFailureException.of("could not reconstruct the consensus parameters from the manifest", t);
		}
	}

	@Override
	public final Optional<TransactionRequest<?>> getRequest(TransactionReference reference) {
		Objects.requireNonNull(reference);

		return requests.computeIfAbsentOptional(reference, _reference -> node.getStore().getRequest(_reference));
	}

	@Override
	public final Optional<TransactionResponse> getResponse(TransactionReference reference) {
		Objects.requireNonNull(reference);

		return responses.computeIfAbsentOptional(reference, _reference -> node.getStore().getResponse(_reference));
	}

	@Override
	public final Optional<TransactionResponse> getResponseUncommitted(TransactionReference reference) {
		return getResponse(reference).or(() -> node.getStore().getResponseUncommitted(reference));
	}

	@Override
	public final EngineClassLoader getClassLoader(TransactionReference classpath) {
		return classLoaders.computeIfAbsentNoException(classpath, _classpath -> new EngineClassLoaderImpl(null, Stream.of(_classpath), node, true, consensus));
	}

	@Override
	public final boolean signatureIsValid(SignedTransactionRequest request, SignatureAlgorithm<SignedTransactionRequest> signatureAlgorithm) throws Exception {
		return checkedSignatures.computeIfAbsent(request, _request -> signatureAlgorithm.verify(_request, getPublicKey(_request.getCaller(), signatureAlgorithm), _request.getSignature()));
	}

	@Override
	public final ConsensusParams getConsensusParams() {
		return consensus;
	}

	@Override
	public final Optional<StorageReference> getGamete() {
		if (gamete.isEmpty())
			gamete = node.getStoreUtilities().getGameteUncommitted();

		return gamete;
	}

	@Override
	public final Optional<StorageReference> getValidators() {
		if (validators.isEmpty())
			validators = node.getStoreUtilities().getValidatorsUncommitted();

		return validators;
	}

	@Override
	public final Optional<StorageReference> getVersions() {
		if (versions.isEmpty())
			versions = node.getStoreUtilities().getVersionsUncommitted();

		return versions;
	}

	@Override
	public final Optional<StorageReference> getGasStation() {
		if (gasStation.isEmpty())
			gasStation = node.getStoreUtilities().getGasStationUncommitted();
	
		return gasStation;
	}

	@Override
	public final Optional<BigInteger> getGasPrice() {
		if (gasPrice == null)
			recomputeGasPrice();

		return Optional.ofNullable(gasPrice);
	}

	@Override
	public final Optional<Long> getCurrentInflation() {
		if (inflation == null)
			recomputeInflation();

		return Optional.ofNullable(inflation);
	}

	/**
	 * Yields the public key of the given externally owned account.
	 * 
	 * @param reference the account
	 * @param signatureAlgorithm the signing algorithm used for the request
	 * @return the public key
	 * @throws NoSuchAlgorithmException if the signing algorithm is unknown
	 * @throws NoSuchProviderException of the signing provider is unknown
	 * @throws InvalidKeySpecException of the key specification is invalid
	 */
	private PublicKey getPublicKey(StorageReference reference, SignatureAlgorithm<SignedTransactionRequest> signatureAlgorithm) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeySpecException {
		// we go straight to the transaction that created the object
		TransactionResponse response;
		try {
			response = node.getResponse(reference.transaction);
		}
		catch (TransactionRejectedException e) {
			throw new NoSuchElementException("unknown transaction reference " + reference.transaction);
		}
	
		if (!(response instanceof TransactionResponseWithUpdates))
			throw new NoSuchElementException("transaction reference " + reference.transaction + " does not contain updates");
	
		String publicKeyEncodedBase64 = ((TransactionResponseWithUpdates) response).getUpdates()
			.filter(update -> update instanceof UpdateOfString && update.object.equals(reference))
			.map(update -> (UpdateOfString) update)
			.filter(update -> update.getField().equals(FieldSignature.EOA_PUBLIC_KEY_FIELD))
			.findFirst().get()
			.value;
	
		byte[] publicKeyEncoded = Base64.getDecoder().decode(publicKeyEncodedBase64);
		return signatureAlgorithm.publicKeyFromEncoding(publicKeyEncoded);
	}

	private void recomputeGasPrice() {
		Optional<StorageReference> manifest = node.getStore().getManifestUncommitted();
		if (manifest.isPresent())
			try {
				gasPrice = ((BigIntegerValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
					(manifest.get(), _100_000, node.getStoreUtilities().getTakamakaCodeUncommitted().get(),
					CodeSignature.GET_GAS_PRICE, getGasStation().get()))).value;
			}
			catch (Throwable t) {
				throw InternalFailureException.of("could not determine the gas price", t);
			}
	}

	private void recomputeInflation() {
		Optional<StorageReference> manifest = node.getStore().getManifestUncommitted();
		if (manifest.isPresent())
			try {
				inflation = ((LongValue) node.runInstanceMethodCallTransaction(new InstanceMethodCallTransactionRequest
					(manifest.get(), _100_000, node.getStoreUtilities().getTakamakaCodeUncommitted().get(),
					CodeSignature.GET_CURRENT_INFLATION, getValidators().get()))).value;
			}
			catch (Throwable t) {
				throw InternalFailureException.of("could not determine the current inflation", t);
			}
	}

	/**
	 * Determines if the given response might change the value of some consensus parameters.
	 * 
	 * @param response the response
	 * @param classLoader the class loader used to build the response
	 * @return true if the response changes the value of some consensus parameters; otherwise,
	 *         it is more efficient to return false, since true might trigger a recomputation
	 *         of the consensus parameters' cache
	 */
	private boolean consensusParametersMightHaveChanged(TransactionResponse response, EngineClassLoader classLoader) {
		if (response instanceof InitializationTransactionResponse)
			return true;

		// we check if there are events of type ConsensusUpdate triggered by the manifest, validators, gas station or versions
		if (isInitializedUncommitted() && response instanceof TransactionResponseWithEvents) {
			Stream<StorageReference> events = ((TransactionResponseWithEvents) response).getEvents();
			StorageReference manifest = node.getStore().getManifestUncommitted().get();
			StorageReference gasStation = getGasStation().get();
			StorageReference versions = getVersions().get();
			StorageReference validators = getValidators().get();

			return events.filter(event -> isConsensusUpdateEvent(event, classLoader))
				.map(node.getStoreUtilities()::getCreatorUncommitted)
				.anyMatch(creator -> creator.equals(manifest) || creator.equals(validators) || creator.equals(gasStation) || creator.equals(versions));
		}

		return false;
	}

	/**
	 * Determines if the node is initialized, that is, its manifest has been set,
	 * although possibly not yet committed.
	 * 
	 * @return true if and only if that condition holds
	 */
	private boolean isInitializedUncommitted() {
		return node.getStore().getManifestUncommitted().isPresent();
	}

	private boolean isConsensusUpdateEvent(StorageReference event, EngineClassLoader classLoader) {
		return classLoader.isConsensusUpdateEvent(node.getStoreUtilities().getClassNameUncommitted(event));
	}

	/**
	 * Determines if the given response might change the gas price.
	 * 
	 * @param response the response
	 * @param classLoader the class loader used to build the response
	 * @return true if the response changes the gas price
	 */
	private boolean gasPriceMightHaveChanged(TransactionResponse response, EngineClassLoader classLoader) {
		if (response instanceof InitializationTransactionResponse)
			return true;

		// we check if there are events of type GasPriceUpdate triggered by the gas station
		if (isInitializedUncommitted() && response instanceof TransactionResponseWithEvents) {
			Stream<StorageReference> events = ((TransactionResponseWithEvents) response).getEvents();
			StorageReference gasStation = getGasStation().get();

			return events.filter(event -> isGasPriceUpdateEvent(event, classLoader))
				.map(node.getStoreUtilities()::getCreatorUncommitted)
				.anyMatch(gasStation::equals);
		}

		return false;
	}

	/**
	 * Determines if the given response might change the current inflation.
	 * 
	 * @param response the response
	 * @param classLoader the class loader used to build the response
	 * @return true if the response changes the current inflation
	 */
	private boolean inflationMightHaveChanged(TransactionResponse response, EngineClassLoader classLoader) {
		if (response instanceof InitializationTransactionResponse)
			return true;

		// we check if there are events of type InflationUpdate triggered by the validators object
		if (isInitializedUncommitted() && response instanceof TransactionResponseWithEvents) {
			Stream<StorageReference> events = ((TransactionResponseWithEvents) response).getEvents();
			StorageReference validators = getValidators().get();

			return events.filter(event -> isInflationUpdateEvent(event, classLoader))
				.map(node.getStoreUtilities()::getCreatorUncommitted)
				.anyMatch(validators::equals);
		}

		return false;
	}

	private boolean isGasPriceUpdateEvent(StorageReference event, EngineClassLoader classLoader) {
		return classLoader.isGasPriceUpdateEvent(node.getStoreUtilities().getClassNameUncommitted(event));
	}

	private boolean isInflationUpdateEvent(StorageReference event, EngineClassLoader classLoader) {
		return classLoader.isInflationUpdateEvent(node.getStoreUtilities().getClassNameUncommitted(event));
	}
}