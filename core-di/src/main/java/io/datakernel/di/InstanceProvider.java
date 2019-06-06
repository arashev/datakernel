package io.datakernel.di;

public interface InstanceProvider<T> {
	T create();

	T get();
}