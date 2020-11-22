package io.takamaka.tests.storagemap;

import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import io.takamaka.code.lang.Exported;
import io.takamaka.code.lang.Storage;
import io.takamaka.code.util.StorageMap;
import io.takamaka.code.util.StorageMapView;

/**
 * A view of a parent storage map. A view contains the same bindings
 * as the parent storage map. Moreover, a view is exported, so that
 * it can be safely divulged outside the store of a node. Calls to the view
 * are simply forwarded to the parent map.
 *
 * @param <K> the type of the keys
 * @param <V> the type of the values
 */

@Exported
public class ExportedModifiableStorageMap<K,V> extends Storage implements StorageMap<K,V> {

	private final StorageMap<K,V> parent;

	/**
	 * Builds a view of the given parent map. Any change to the parent map will be
	 * reflected in this view and vice versa.
	 * 
	 * @param parent the reflected parent map
	 */
	public ExportedModifiableStorageMap(StorageMap<K,V> parent) {
		this.parent = parent;
	}

	@Override
	public void put(K key, V value) {
		parent.put(key, value);
	}

	@Override
	public V putIfAbsent(K key, V value) {
		return parent.putIfAbsent(key, value);
	}

	@Override
	public V computeIfAbsent(K key, Supplier<? extends V> supplier) {
		return parent.computeIfAbsent(key, supplier);
	}

	@Override
	public V computeIfAbsent(K key, Function<? super K, ? extends V> supplier) {
		return parent.computeIfAbsent(key, supplier);
	}

	@Override
	public void removeMin() {
		parent.removeMin();
	}

	@Override
	public void removeMax() {
		parent.removeMax();
	}

	@Override
	public void remove(Object key) {
		parent.remove(key);
	}

	@Override
	public void update(K key, UnaryOperator<V> how) {
		parent.update(key, how);
	}

	@Override
	public void update(K key, V _default, UnaryOperator<V> how) {
		parent.update(key, _default, how);
	}

	@Override
	public void update(K key, Supplier<? extends V> _default, UnaryOperator<V> how) {
		parent.update(key, _default, how);
	}

	@Override
	public int size() {
		return parent.size();
	}

	@Override
	public boolean isEmpty() {
		return parent.isEmpty();
	}

	@Override
	public V get(Object key) {
		return parent.get(key);
	}

	@Override
	public V getOrDefault(Object key, V _default) {
		return parent.getOrDefault(key, _default);
	}

	@Override
	public V getOrDefault(Object key, Supplier<? extends V> _default) {
		return parent.getOrDefault(key, _default);
	}

	@Override
	public boolean contains(Object key) {
		return parent.contains(key);
	}

	@Override
	public K min() {
		return parent.min();
	}

	@Override
	public K max() {
		return parent.max();
	}

	@Override
	public K floorKey(K key) {
		return parent.floorKey(key);
	}

	@Override
	public K ceilingKey(K key) {
		return parent.ceilingKey(key);
	}

	@Override
	public K select(int k) {
		return parent.select(k);
	}

	@Override
	public int rank(K key) {
		return parent.rank(key);
	}

	@Override
	public Stream<Entry<K, V>> stream() {
		return parent.stream();
	}

	@Override
	public List<K> keyList() {
		return parent.keyList();
	}

	@Override
	public Stream<K> keys() {
		return parent.keys();
	}

	@Override
	public StorageMapView<K,V> view() {
		return this;
	}

	@Override
	public StorageMapView<K,V> snapshot() {
		return parent.snapshot();
	}

	@Override
	public Iterator<Entry<K, V>> iterator() {
		return parent.iterator();
	}
}