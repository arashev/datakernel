[UIKernel Integration Example](https://github.com/softindex/datakernel/tree/master/examples/uikernel-integration/src/main/java/io/datakernel/examples) -
 integration of [UIKernel](http://uikernel.io) frontend JS library with DataKernel modules.

<img src="http://datakernel.io/static/images/uikernel-integration-example.gif">

You can launch this example in **4 steps**:

#### 1. Clone DataKernel from GitHub repository and install it:
```
$ git clone https://github.com/softindex/datakernel.git
$ cd datakernel
$ mvn clean install -DskipTests
```

#### 2. Install npm:
```
$ sudo apt install npm
```

#### 3. Enter the following commands:
```
$ cd datakernel/examples/uikernel-integration
$ sudo npm i
$ npm run-script build
```
If the commands won't work, try to enter this command after `sudo npm i`:
```
$ npm run-script postinstall 
```

#### 4. Open your favourite browser
Open your browser and go to [localhost:8080](http://localhost:8080). You will see an editable users grid table with 
some pre-defined information. This grid supports searching by name, age and gender. You can also add new people or 
edit information about them.
