Maven CloudFormation Plugin
===========================

A simple Maven plugin to upload artifacts to S3 and update a CloudFormation stack.

To use this library in another Maven Project:
---------------------------------------------

Add the following to the pom.xml for releases:

```
<repositories>
    <repository>
        <id>maven.leadoperations.co-release</id>
        <name>AWS S3 Release Repository</name>
        <url>http://maven.leadoperations.co/release</url>
    </repository>
</repositories>
```

or for snapshots:

```
<repositories>
    <repository>
        <id>maven.leadoperations.co-snapshot</id>
        <name>AWS S3 Snapshot Repository</name>
        <url>http://maven.leadoperations.co/snapshot</url>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>
```

Then add the plugin:

```
<plugin>
	<groupId>com.cleanenergyexperts</groupId>
	<artifactId>maven-cloudformation-plugin</artifactId>
	<version>0.0.4</version>
	<configuration>
		<accessKey>[AWS Access Key]</accessKey>
		<secretKey>[AWS Secret Key]</secretKey>
		<bucketName>[S3 Bucket Name]</bucketName>
		<region>[Cloud Formation Region: i.e. us-west-2]</region>
		<stackName>[Cloud Formation Stack Name/ID]</stackName>
		<artifactFile>${project.build.directory}/${maven.build.timestamp}-${project.build.finalName}.zip</artifactFile>
		<stackParameters>
			<Key>[Any Parameters here will be feed into the Cloud Formation Stack Template]</Key>
		</stackParameters>
	</configuration>
	<executions>
		<execution>
			<id>cf-deploy</id>
			<phase>deploy</phase>
			<goals>
				<goal>update-cf</goal>
			</goals>
		</execution>
	</executions>
</plugin>
```

Releasing a new version:
------------------------
`mvn release:prepare`
`mvn release:perform`
