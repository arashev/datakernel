<p align="center">
  <a href="https://datakernel.io" target="_blank">
    <img alt="DataKernel Logo" src="http://datakernel.io/static/images/logo-icon.svg" width="350">
  </a>
</p>

[![Maven Central](https://img.shields.io/maven-central/v/io.datakernel/datakernel)](https://mvnrepository.com/artifact/io.datakernel)
[![Download](https://img.shields.io/badge/download-3.0.0--SNAPSHOT-blue.svg)](https://github.com/softindex/datakernel/archive/master.zip)
[![Apache license](https://img.shields.io/badge/license-apache2-green.svg)](https://github.com/softindex/datakernel/blob/master/LICENSE)
[![Twitter](https://img.shields.io/badge/twitter-%40datakernel__io-38A1F3.svg)](https://twitter.com/datakernel_io)

## Introduction

DataKernel is a full-featured alternative Java framework, created from ground up for **efficient** and **scalable** solutions.

## Features

- 💕 Fully **asynchronous** modular framework
- 🧩 Exceptionally fast, powerful and simple **Dependency Injection**
- ⏱ Magnificently **fast build** and **start-up times** of your applications with **extremely small** JAR sizes
- 🚀 A wide selection of application launchers and **embedded servers**
- ⚙️ **Elegant** data structures and components with outstanding **performance**
- 🌎 Supports **HTTP, TCP, UDP** protocols and data streaming with modern reactive API 
- 🏎 Scalable and fault-tolerant **microservice** architecture with ultra-fast binary messaging 
- 📖 Low entry barrier; archetypes for HTTP and RPC applications scaffolding with **minimal configuration**

## Getting started

Just insert this snippet to your terminal...

```
mvn archetype:generate \
        -DarchetypeGroupId=io.datakernel                  \
        -DarchetypeArtifactId=datakernel-http-archetype   \
        -DarchetypeVersion=3.0.0-beta1                    \
        -DgroupId=org.example                             \
        -DartifactId=dkapp                                \
        -DmainClassName=MyFirstDkApp 
```

... and open project in your favourite IDE. Then, build the application and run it. Open your browser on [localhost:8080](http://localhost:8080) to see the "Hello World" message. 

To learn more about DataKernel, visit [**datakernel.io**](https://datakernel.io) or follow 5-minute getting-started [guide](https://datakernel.io/docs/core/tutorials/getting-started). 

## Examples

### Basic HTTP server in less than 15 lines of code:
```java
public final class HelloWorldExample { 
    public static void main(String[] args) throws IOException {
        Eventloop eventloop = Eventloop.create();
        AsyncHttpServer server = AsyncHttpServer.create(eventloop,
                request -> Promise.of(
                        HttpResponse.ok200()
                                .withPlainText("Hello world!")))
                .withListenPort(8080);
        server.listen();
        eventloop.run();
    }
}
```
`AsyncHttpServer` is a built-in implementation of an HTTP server which asynchronously runs in a Node.js-inspired Event Loop.

📌 *The JAR file size of this example is only 723KB*

📌 *`AsyncHttpServer` handles ~170K requests per second on single core*

### Even simpler asynchronous HTTP server:
```java
public final class HttpHelloWorldExample extends HttpServerLauncher { 
    @Provides
    AsyncServlet servlet() { 
        return request -> Promise.of(ok200().withPlainText("Hello World"));
    }

    public static void main(String[] args) throws Exception {
        Launcher launcher = new HttpHelloWorldExample();
        launcher.launch(args); 
    }
}
```
`HttpServerLauncher` - a predefined DataKernel [Launcher](https://datakernel.io/docs/core/launcher.html), which takes care of application lifecycle and provides needed components for our server

`@Provides` - one of the [DataKernel DI](https://datakernel.io/docs/core/di.html) annotations

`AsyncServlet` - asynchronous servlet interface

`Promise` - Node.js-inspired async single-threaded Promises, an alternative to `CompletableFuture`

📌 *This example utilizes quite a few components - [Eventloop](https://datakernel.io/docs/core/eventloop.html), [DI](https://datakernel.io/docs/core/di.html), [Promise](https://datakernel.io/docs/core/promise.html), [HTTP](https://datakernel.io/docs/core/http.html), [Launcher](https://datakernel.io/docs/core/launcher.html). Yet, it builds and starts in 0.1 second.*

📌 *DataKernel [DI](https://datakernel.io/docs/core/di.html) is 2.5 times faster than Guice and 100s times faster than Spring.*

📌 *DataKernel [Promise](https://datakernel.io/docs/core/promise.html) is 7 times faster than Java `CompletableFuture`.*

### Lightning-fast RPC server:
```java
...
public RpcServer rpcServer(Eventloop eventloop, Config config) {
    return RpcServer.create(eventloop)
            .withStreamProtocol(...)
            .withListenPort(config.get(ofInteger(), "rpc.server.port"))
            .withMessageTypes(Integer.class)
            .withHandler(Integer.class, Integer.class, req -> Promise.of(req * 2));
}
...
```
📌 *This RPC server handles up to [15M requests](https://datakernel.io/docs/cloud/rpc.html#benchmarks) per second on single core*.

## Documentation
See the docs, examples and tutorials on [our website](https://datakernel.io).

## Need help or found a bug?
Feel free to open a [GitHub issue](https://github.com/softindex/datakernel/issues).

## Communication
* Twitter: [@datakernel_io](https://twitter.com/datakernel_io)
