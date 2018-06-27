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

package io.datakernel.serializer.asm;

import io.datakernel.bytebuf.SerializationUtils;
import io.datakernel.codegen.Expression;
import io.datakernel.codegen.Variable;
import io.datakernel.serializer.CompatibilityLevel;
import io.datakernel.serializer.NullableOptimization;
import io.datakernel.serializer.SerializerBuilder;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static io.datakernel.codegen.Expressions.*;
import static io.datakernel.util.Preconditions.checkNotNull;
import static org.objectweb.asm.Type.getType;

@SuppressWarnings("PointlessArithmeticExpression")
public final class SerializerGenMap implements SerializerGen, NullableOptimization {
	private final SerializerGen keySerializer;
	private final SerializerGen valueSerializer;
	private final boolean nullable;

	public SerializerGenMap(SerializerGen keySerializer, SerializerGen valueSerializer, boolean nullable) {
		this.keySerializer = checkNotNull(keySerializer);
		this.valueSerializer = checkNotNull(valueSerializer);
		this.nullable = nullable;
	}

	public SerializerGenMap(SerializerGen keySerializer, SerializerGen valueSerializer) {
		this(keySerializer, valueSerializer, false);
	}

	@Override
	public void getVersions(VersionsCollector versions) {
		versions.addRecursive(valueSerializer);
	}

	@Override
	public boolean isInline() {
		return true;
	}

	@Override
	public Class<?> getRawType() {
		return Map.class;
	}

	@Override
	public void prepareSerializeStaticMethods(int version, SerializerBuilder.StaticMethods staticMethods, CompatibilityLevel compatibilityLevel) {
		keySerializer.prepareSerializeStaticMethods(version, staticMethods, compatibilityLevel);
		valueSerializer.prepareSerializeStaticMethods(version, staticMethods, compatibilityLevel);
	}

	@Override
	public Expression serialize(Expression byteArray, Variable off, Expression value, int version, SerializerBuilder.StaticMethods staticMethods, CompatibilityLevel compatibilityLevel) {
		Expression length = length(value);
		Expression writeLength = set(off, callStatic(SerializationUtils.class, "writeVarInt", byteArray, off, (!nullable ? length : inc(length))));
		Expression mapSerializer = mapForEach(value,
				k -> set(off, keySerializer.serialize(byteArray, off, cast(k, keySerializer.getRawType()), version, staticMethods, compatibilityLevel)),
				v -> set(off, valueSerializer.serialize(byteArray, off, cast(v, valueSerializer.getRawType()), version, staticMethods, compatibilityLevel)));

		if (!nullable) {
			return sequence(writeLength, mapSerializer, off);
		} else {
			return ifThenElse(isNull(value),
					sequence(set(off, callStatic(SerializationUtils.class, "writeVarInt", byteArray, off, value(0))), off),
					sequence(writeLength, mapSerializer, off));
		}
	}

	@Override
	public void prepareDeserializeStaticMethods(int version, SerializerBuilder.StaticMethods staticMethods, CompatibilityLevel compatibilityLevel) {
		keySerializer.prepareDeserializeStaticMethods(version, staticMethods, compatibilityLevel);
		valueSerializer.prepareDeserializeStaticMethods(version, staticMethods, compatibilityLevel);
	}

	@Override
	public Expression deserialize(Class<?> targetType, int version, SerializerBuilder.StaticMethods staticMethods, CompatibilityLevel compatibilityLevel) {
		boolean isEnum = keySerializer.getRawType().isEnum();

		if (!isEnum) {
			return deserializeSimple(version, staticMethods, compatibilityLevel);
		} else {
			return deserializeEnum(version, staticMethods, compatibilityLevel);
		}
	}

	public Expression deserializeSimple(int version, SerializerBuilder.StaticMethods staticMethods, CompatibilityLevel compatibilityLevel) {
		Expression length = let(call(arg(0), "readVarInt"));
		Expression local = let(constructor(LinkedHashMap.class, !nullable ? length : dec(length)));
		Expression forEach = expressionFor(value(0), !nullable ? length : dec(length),
				it -> sequence(
						call(local, "put",
								cast(keySerializer.deserialize(keySerializer.getRawType(), version, staticMethods, compatibilityLevel), Object.class),
								cast(valueSerializer.deserialize(valueSerializer.getRawType(), version, staticMethods, compatibilityLevel), Object.class)
						),
						voidExp()));
		if (!nullable) {
			return sequence(length, local, forEach, local);
		} else {
			return ifThenElse(cmpEq(length, value(0)),
					nullRef(LinkedHashMap.class),
					sequence(local, forEach, local));
		}

	}

	public Expression deserializeEnum(int version, SerializerBuilder.StaticMethods staticMethods, CompatibilityLevel compatibilityLevel) {
		Expression length = let(call(arg(0), "readVarInt"));

		Expression local = let(constructor(EnumMap.class, cast(value(getType(keySerializer.getRawType())), Class.class)));
		Expression forEach = expressionFor(value(0), !nullable ? length : dec(length),
				it -> sequence(
						call(local, "put",
								cast(keySerializer.deserialize(keySerializer.getRawType(), version, staticMethods, compatibilityLevel), Object.class),
								cast(valueSerializer.deserialize(valueSerializer.getRawType(), version, staticMethods, compatibilityLevel), Object.class)
						),
						voidExp()));
		if (!nullable) {
			return sequence(length, local, forEach, local);
		} else {
			return ifThenElse(cmpEq(length, value(0)),
					nullRef(EnumMap.class),
					sequence(local, forEach, local));
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		SerializerGenMap that = (SerializerGenMap) o;

		if (!keySerializer.equals(that.keySerializer)) return false;
		if (!valueSerializer.equals(that.valueSerializer)) return false;
		if (nullable != that.nullable) return false;

		return true;

	}

	@Override
	public int hashCode() {
		int result = keySerializer.hashCode();
		result = 31 * result + valueSerializer.hashCode();
		result = 31 * result + (nullable ? 1 : 0);
		return result;
	}

	@Override
	public SerializerGen setNullable() {
		return new SerializerGenMap(keySerializer, valueSerializer, true);
	}
}
