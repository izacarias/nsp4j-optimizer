# nsp4j-optimizer

Network and Service management Planning framework for Java - Optimizer

Prerequisites

- [Apache Maven](https://maven.apache.org/)
- [openjdk](https://openjdk.org/)
- [Gurobi optimizer](https://www.gurobi.com/)


## Build and run (Maven)

1. Install `gurobi.jar` library on your local maven repository:
   
   	```shell
  GRB_JAR=`find /opt -name gurobi.jar 2>/dev/null`
	mvn install:install-file -Dfile=$GRB_JAR -DgroupId=com.gurobi -DartifactId=gurobi-optimizer -Dversion=21.0.1 -Dpackaging=jar
	```


2. Build:

	```shell
	git clone https://github.com/fcarp10/nsp4j-optimizer.git
	cd nsp4j-optimizer/
	mvn install
	mvn package
	```

3. Run:
	
	```shell
	cp src/main/resources/scenarios/{example.dgs,example.txt,example.yml} target
	cd target/
	java -jar nsp4j-optimizer-${VERSION}-jar-with-dependencies.jar
	```

## Build and run (Gradle)

1. Clone the repository
  
  ``` shell
  git clone https://github.com/fcarp10/nsp4j-optimizer.git
  cd nsp4j-optimizer
  ```

2. Install `gurobi.jar` library in the local directory `libs`
  ```
  mkdir libs
  GRB_JAR=`find /opt -name gurobi.jar 2>/dev/null`
  cp $GRB_JAR ./libs
  ```

3. Build and run:

	```shell
	./gradlew build
  ./gradlew run
	```

4. By default, GUI runs in: `localhost:8082`.

5. Check in `target/results/` for the generated results.

## API

Upload a file:
```
curl -s -X POST http://SERVER:PORT/upload -F 'file=@test.txt'
```

Delete a file:
```
curl -s -X DELETE http://localhost:8082/delete/test.txt
```

Run a model (example):
```
curl -s -X POST http://localhost:8082/run -d '{ 
  "inputFileName":"example",
  "objFunc":"UTIL_COSTS",
  "maximization":false,
  "name":"LP",
  "constraints":{
    "RP1":true,
    "RP2":true,
    "PF1":true,
    "PF2":true,
    "PF3":true,
    "FD1":true,
    "FD2":true,
    "FD3":true,
    "sync_traffic":false,
    "max_serv_delay":false,
    "cloud_only":false,
    "edge_only":false,
    "single_path":false,
    "set_init_plc":false,
    "force_src_dst":false,
    "const_rep":false
    }
  }'
```

Stop the optimizer:
```
curl -s -X GET http://localhost:8082/stop 
```

Retrieve results:
```
curl -s -X GET http://localhost:8082/results 
```

## Configuration files

- `*.dgs`: topology description file
- `*.txt`: paths file
- `*.yml`: parameters file


### Topology description file

The network topology is specified using GraphStream guidelines:

```
DGS004
test 0 0

an n1 x:100 y:150 num_servers:1 server_capacity:1000 
an n2 x:150 y:100
an n3 x:200 y:100

ae n1n2 n1 > n2 capacity:1000
ae n2n3 n2 > n3

```

- `an` adds a node. The command is followed by a unique node identifier, that
  can be a single word or a string delimited by the double quote character.
  Values x and y on the server represent the coordinates of the nodes. For each
  node, additional parameters can be specified, for instance, the number of
  servers or capacity.

- `ae` adds an link. This command must be followed by a unique identifier of the
  link, following with the identifiers of two connecting nodes. For each link,
  additional parameters can be specified, for instance, the link capacity.

For further information, see
[Graphstream](http://graphstream-project.org/doc/Advanced-Concepts/The-DGS-File-Format/)
documentation.


### Paths file

File containing all admissible paths:

``` 
[n1, n2, n3, n7, n9]
[n1, n4, n5, n3, n7, n9]
[n1, n4, n5, n6, n7, n9]
```


### Parameters file

Parameters for the optimization model:

```
# optimization parameters
gap: 0
weights: [0, 1.0, 0]
# auxiliary parameters
aux: {
  "iterations": 1000,
  "offset_results": 0,
  "scaling_x": 1.0,
  "scaling_y": 1.0
}
# service definitions
serviceChains:
- id: 0
  chain: [0, 1, 2]
  attributes: {
    "minPaths": 1,
    "maxPaths": 2
  }
# function definitions
functionTypes:
- type: 0
  attributes: {
    "replicable": false,
    "load": 1.0,
    "overhead": 10,
    "sync_load": 0.1,
    "delay": 10
  }
- type: 1
  attributes: {
    "replicable": true,
    "load": 1.0,
    "overhead": 10,
    "sync_load": 0.1,
    "delay": 10
  }
# traffic flow definitions
traffic_flows:
  - src: "n1"
    dst: "n2"
    demands_specific: [3, 35, 17]
    services: [1]
    service_length: [3]

```