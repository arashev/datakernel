package io.datakernel.memcache.protocol;

import io.datakernel.rpc.hash.HashFunction;
import io.datakernel.rpc.protocol.RpcMandatoryData;
import io.datakernel.serializer.annotations.Deserialize;
import io.datakernel.serializer.annotations.Serialize;
import io.datakernel.serializer.annotations.SerializeNullable;

import java.util.Arrays;
import java.util.List;

public class MemcacheRpcMessage {
	public static final HashFunction<Object> HASH_FUNCTION =
			item -> {
				if (item instanceof GetRequest) {
					GetRequest request = (GetRequest) item;
					return Arrays.hashCode(request.getKey());
				} else if (item instanceof PutRequest) {
					PutRequest request = (PutRequest) item;
					return Arrays.hashCode(request.getKey());
				}
				throw new IllegalArgumentException("Unknown request type " + item);
			};

	public static final List<Class<?>> MESSAGE_TYPES = Arrays.asList(GetRequest.class, GetResponse.class, PutRequest.class, PutResponse.class);

	public static final class GetRequest implements RpcMandatoryData {
		private final byte[] key;

		public GetRequest(@Deserialize("key") byte[] key) {
			this.key = key;
		}

		@Serialize(order = 1)
		public byte[] getKey() {
			return key;
		}
	}

	public static final class GetResponse {
		private final Slice data;

		public GetResponse(@Deserialize("data") Slice data) {
			this.data = data;
		}

		@Serialize(order = 1)
		@SerializeNullable
		public Slice getData() {
			return data;
		}
	}

	public static final class PutRequest {
		private final byte[] key;
		private final Slice data;

		public PutRequest(@Deserialize("key") byte[] key, @Deserialize("data") Slice data) {
			this.key = key;
			this.data = data;
		}

		@Serialize(order = 1)
		public byte[] getKey() {
			return key;
		}

		@SerializeNullable
		@Serialize(order = 2)
		public Slice getData() {
			return data;
		}
	}

	public static final class PutResponse {
		public static final PutResponse INSTANCE = new PutResponse();
	}

	public static final class Slice {
		private final byte[] array;
		private final int offset;
		private final int length;

		public Slice(byte[] array) {
			this.array = array;
			this.offset = 0;
			this.length = array.length;
		}

		public Slice(byte[] array, int offset, int length) {
			this.array = array;
			this.offset = offset;
			this.length = length;
		}

		public byte[] array() {
			return array;
		}

		public int offset() {
			return offset;
		}

		public int length() {
			return length;
		}
	}
}
