package io.global.ot;

import io.datakernel.codec.StructuredCodec;
import io.datakernel.di.annotation.Provides;
import io.datakernel.di.module.AbstractModule;
import io.datakernel.ot.OTSystem;
import io.global.ot.client.OTDriver;
import io.global.ot.map.MapOTSystem;
import io.global.ot.map.MapOperation;

import java.util.Comparator;

import static io.global.ot.OTUtils.getMapOperationCodec;

public abstract class MapModule<K, V> extends AbstractModule {
	public final String mapRepoPrefix;

	public MapModule(String mapRepoPrefix) {
		this.mapRepoPrefix = mapRepoPrefix;
	}

	@Provides
	DynamicOTUplinkServlet<MapOperation<K, V>> mapServlet(OTDriver driver, OTSystem<MapOperation<K, V>> otSystem,
			StructuredCodec<MapOperation<K, V>> mapCodec) {
		return DynamicOTUplinkServlet.create(driver, otSystem, mapCodec, mapRepoPrefix);
	}

	@Provides
	<T extends Comparable<? super T>> Comparator<T> comparator() {
		return Comparator.naturalOrder();
	}

	@Provides
	OTSystem<MapOperation<K, V>> otSystem(Comparator<V> valueComparator) {
		return MapOTSystem.create(valueComparator);
	}

	@Provides
	StructuredCodec<MapOperation<K, V>> mapCodec(StructuredCodec<K> keyCodec, StructuredCodec<V> valueCodec) {
		return getMapOperationCodec(keyCodec, valueCodec);
	}
}