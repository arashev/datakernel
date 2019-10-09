package io.datakernel.aggregation;

import io.datakernel.promise.Promise;

public class IdGeneratorStub implements IdGenerator<Long> {
	public long id;

	@Override
	public Promise<Long> createId() {
		return Promise.of(++id);
	}
}
