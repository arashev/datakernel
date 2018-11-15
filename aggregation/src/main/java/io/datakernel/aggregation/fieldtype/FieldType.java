/*
 * Copyright (C) 2015 SoftIndex LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.datakernel.aggregation.fieldtype;

import com.google.gson.TypeAdapter;
import io.datakernel.annotation.Nullable;
import io.datakernel.codegen.Expression;
import io.datakernel.serializer.asm.SerializerGen;

import java.lang.reflect.Type;

/**
 * Represents a type of aggregation field.
 */
public class FieldType<T> {
	private final Class<?> internalDataType;
	private final Type dataType;
	private final SerializerGen serializer;
	@Nullable
	private final TypeAdapter<?> internalJson;
	private final TypeAdapter<T> json;

	protected FieldType(Class<T> dataType, SerializerGen serializer, TypeAdapter<T> json) {
		this(dataType, dataType, serializer, json, json);
	}

	protected FieldType(Class<?> internalDataType, Type dataType, SerializerGen serializer, TypeAdapter<T> json, @Nullable TypeAdapter<?> internalJson) {
		this.internalDataType = internalDataType;
		this.dataType = dataType;
		this.serializer = serializer;
		this.internalJson = internalJson;
		this.json = json;
	}

	public final Class<?> getInternalDataType() {
		return internalDataType;
	}

	public final Type getDataType() {
		return dataType;
	}

	public SerializerGen getSerializer() {
		return serializer;
	}

	public TypeAdapter<T> getJson() {
		return json;
	}

	@Nullable
	public TypeAdapter<?> getInternalJson() {
		return internalJson;
	}

	public Expression toValue(Expression internalValue) {
		return internalValue;
	}

	public Object toInternalValue(T value) {
		return value;
	}

	@Override
	public String toString() {
		return "{" + internalDataType + '}';
	}

}
