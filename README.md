# Maven CloudFormation Plugin

A simple Maven plugin to upload artifacts to S3 and update a CloudFormation stack.

In the future, we may add additional or more generic functions, but for now it is 
very simple.

## Installing the Plugin

We don't yet have a hosted maven repository so the steps to get the plugin working are:

1. `git clone git@github.com:msnider/maven-cloudformation-plugin.git`
2. `cd maven-cloudformation-plugin`
3. `mvn install`

## Adding the Plugin to Your Project
The following configuration will upload a zip file to S3 and then update the stack on mvn deploy:

	<plugin>
		<groupId>com.cleanenergyexperts</groupId>
		<artifactId>maven-cloudformation-plugin</artifactId>
		<version>0.0.1-SNAPSHOT</version>
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
