package io.takamaka.code.engine;

import java.nio.file.Path;
import java.nio.file.Paths;

import io.hotmoka.beans.references.TransactionReference;

/**
 * The configuration of a node.
 */
public class Config {

	/**
	 * The directory where the node's data will be persisted.
	 * It defaults to {@code chain} in the current directory.
	 */
	public final Path dir;

	/**
	 * True if and only if {@linkplain #dir} must be deleted when
	 * the node starts. It defaults to true.
	 */
	public final boolean delete;

	/**
	 * The maximal number of polling attempts, in milliseconds,
	 * while waiting for the result of a posted transaction.
	 * It defaults to 100.
	 */
	public final int maxPollingAttempts;

	/**
	 * The delay of two subsequent polling attempts, in milliseconds,
	 * while waiting for the result of a posted transaction.
	 * This delay is then increased by 10% at each subsequent attempt.
	 * It defaults to 10.
	 */
	public final int pollingDelay;

	/**
	 * The size of the cache for the {@linkplain io.hotmoka.nodes.Node#getRequestAt(TransactionReference)} method.
	 * It defaults to 1,000.
	 */
	public final int requestCacheSize;

	/**
	 * The size of the cache for the {@linkplain io.hotmoka.nodes.Node#getResponseAt(TransactionReference)} method.
	 * It defaults to 1,000.
	 */
	public final int responseCacheSize;

	/**
	 * The size of the cache for the response builders of the node.
	 * It defaults to 10,000.
	 */
	public final int builderCacheSize;

	/**
	 * The size of the cache for the object histories of the node.
	 * It defaults to 10,000.
	 */
	public final int historyCacheSize;

	/**
	 * The maximal length of the error message kept in the store of the node.
	 * Beyond this threshold, the message gets truncated.
	 * It defaults to 200 characters.
	 */
	public final int maxErrorLength;

	/**
	 * Full constructor for the builder pattern.
	 */
	private Config(Path dir, boolean delete, int maxPollingAttempts,
			       int pollingDelay, int requestCacheSize,
			       int responseCacheSize, int builderCacheSize,
			       int historyCacheSize, int maxErrorLength) {

		this.dir = dir;
		this.delete = delete;
		this.maxPollingAttempts = maxPollingAttempts;
		this.pollingDelay = pollingDelay;
		this.requestCacheSize = requestCacheSize;
		this.responseCacheSize = responseCacheSize;
		this.builderCacheSize = builderCacheSize;
		this.historyCacheSize = historyCacheSize;
		this.maxErrorLength = maxErrorLength;
	}

	/**
	 * Copy-constructor for subclassing.
	 */
	protected Config(Config parent) {
		this.dir = parent.dir;
		this.delete = parent.delete;
		this.maxPollingAttempts = parent.maxPollingAttempts;
		this.pollingDelay = parent.pollingDelay;
		this.requestCacheSize = parent.requestCacheSize;
		this.responseCacheSize = parent.responseCacheSize;
		this.builderCacheSize = parent.builderCacheSize;
		this.historyCacheSize = parent.historyCacheSize;
		this.maxErrorLength = parent.maxErrorLength;
	}

	/**
	 * The builder of a configuration object.
	 */
	public abstract static class Builder<T extends Builder<T>> {
		private Path dir = Paths.get("chain");
		private boolean delete = true;
		private int maxPollingAttempts = 100;
		private int pollingDelay = 10;
		private int requestCacheSize = 1_000;
		private int responseCacheSize = 1_000;
		private int builderCacheSize = 10_000;
		private int historyCacheSize = 10_000;
		private int maxErrorLength = 200;

		/**
		 * Standard design pattern. See http://www.angelikalanger.com/GenericsFAQ/FAQSections/ProgrammingIdioms.html#FAQ205
		 */
		protected abstract T getThis();

		/**
		 * Sets the directory where the node's data will be persisted.
		 * It defaults to {@code chain} in the current directory.
		 * 
		 * @param dir the directory
		 * @return this builder
		 */
		public T setDir(Path dir) {
			this.dir = dir;
			return getThis();
		}

		/**
		 * Sets the flag that determines if the directory where
		 * the node stores its data must be deleted at start-up.
		 * It defaults to true.
		 * 
		 * @param delete the new value of the flag
		 * @return this builder
		 */
		public T setDelete(boolean delete) {
			this.delete = delete;
			return getThis();
		}

		/**
		 * Sets the maximal number of polling attempts, in milliseconds,
		 * while waiting for the result of a posted transaction.
		 * It defaults to 100.
		 * 
		 * @param maxPollingAttempts the the maximal number of polling attempts
		 * @return this builder
		 */
		public T setMaxPollingAttempts(int maxPollingAttempts) {
			this.maxPollingAttempts = maxPollingAttempts;
			return getThis();
		}

		/**
		 * Sets the delay of two subsequent polling attempts, in milliseconds,
		 * while waiting for the result of a posted transaction.
		 * This delay is then increased by 10% at each subsequent attempt.
		 * It defaults to 10.
		 * 
		 * @param pollingDelay the delay
		 * @return this builder
		 */
		public T setPollingDelay(int pollingDelay) {
			this.pollingDelay = pollingDelay;
			return getThis();
		}

		/**
		 * Sets size of the cache for the {@linkplain io.hotmoka.nodes.Node#getRequestAt(TransactionReference)} method.
		 * It defaults to 1,000.
		 * 
		 * @param requestCacheSize the cache size
		 * @return this builder
		 */
		public T setRequestCacheSize(int requestCacheSize) {
			this.requestCacheSize = requestCacheSize;
			return getThis();
		}

		/**
		 * Sets size of the cache for the {@linkplain io.hotmoka.nodes.Node#getResponseAt(TransactionReference)} method.
		 * It defaults to 1,000.
		 * 
		 * @param builderCacheSize the cache size
		 * @return this builder
		 */
		public T setResponseCacheSize(int responseCacheSize) {
			this.responseCacheSize = responseCacheSize;
			return getThis();
		}

		/**
		 * Sets size of the cache for the response builders of the node.
		 * It defaults to 10,000.
		 * 
		 * @param builderCacheSize the cache size
		 * @return this builder
		 */
		public T setBuilderCacheSize(int builderCacheSize) {
			this.builderCacheSize = builderCacheSize;
			return getThis();
		}

		/**
		 * Sets size of the cache for the object histories of the node.
		 * It defaults to 10,000.
		 * 
		 * @param historyCacheSize the cache size
		 * @return this builder
		 */
		public T setHistoryCacheSize(int historyCacheSize) {
			this.historyCacheSize = historyCacheSize;
			return getThis();
		}

		/**
		 * Sets the maximal length of the error message kept in the store of the node.
		 * Beyond this threshold, the message gets truncated.
		 * It defaults to 200 characters.
		 * 
		 * @param maxErrorLength the maximal error length
		 * @return this builder
		 */
		public T setMaxErrorLength(int maxErrorLength) {
			this.maxErrorLength = maxErrorLength;
			return getThis();
		}

		/**
		 * Builds the configuration.
		 * 
		 * @return the configuration
		 */
		public Config build() {
			return new Config(dir, delete, maxPollingAttempts, pollingDelay, requestCacheSize, responseCacheSize, builderCacheSize, historyCacheSize, maxErrorLength);
		}
	}
}