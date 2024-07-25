# Building and Running the ORKtoSCAD Java Application

## Prerequisites
- Java Development Kit (JDK) installed.
- Basic knowledge of command line operations.

## Steps to Build and Run

### Step 1: Compile Your Java Files

Navigate to your project directory (where the `src` folder is located) and compile your Java files using the `javac` command:

```
javac -d bin src/orkToScad/*.java
```

This will compile the Java files and place the .class files in a bin directory.

### Step 2: Package into a JAR File

Use the jar command to create a JAR file. Run the following command from your project directory:

```
jar cfm orkToScad.jar manifest.txt -C bin .
```

This command creates a JAR file named orkToScad.jar, includes the manifest file, and packages all the .class files from the bin directory.

### Step 3: Run the JAR File

Run your JAR file and make sure everything is working correctly:

```
java -jar orkToScad.jar
```
