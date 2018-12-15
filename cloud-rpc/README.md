RPC module is the framework to build distributed applications requiring efficient client-server interconnections between 
servers.

* Ideal to create near-realtime (i.e. memcache-like) servers with application-specific business logic
* Up to ~5.7M of requests per second on single core
* Pluggable high-performance asynchronous binary RPC streaming protocol
* Consistent hashing and round-robin distribution strategies
* Fault tolerance is achieved with reconnections to fallback and replica servers
* Utilizes Serializer module for fast RPC message transferring.

## [Examples](https://github.com/softindex/datakernel/tree/master/examples/rpc)

1. ["Hello World" Client and Server](https://github.com/softindex/datakernel/blob/master/examples/rpc/src/main/java/io/datakernel/examples/RpcExample.java)

To run the example, you should execute these three lines in the console in appropriate folder:
```
$ git clone https://github.com/softindex/datakernel.git
$ cd datakernel/examples/rpc
$ mvn clean compile exec:java@RpcExample
```
