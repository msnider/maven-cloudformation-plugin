package com.cleanenergyexperts.aws.cf;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.cloudformation.AmazonCloudFormationClient;
import com.amazonaws.services.cloudformation.model.DescribeStacksRequest;
import com.amazonaws.services.cloudformation.model.DescribeStacksResult;
import com.amazonaws.services.cloudformation.model.GetTemplateRequest;
import com.amazonaws.services.cloudformation.model.GetTemplateResult;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.Stack;
import com.amazonaws.services.cloudformation.model.UpdateStackRequest;
import com.amazonaws.services.s3.AmazonS3Client;

/**
 * Goal which uploads an artifact to S3 and then updates a Cloud Formation stack in AWS.
 * <p>
 * To run it, call `mvn cloudformation:update-cf`
 *
 * @prefix cloudformation
 * @requiresOnline true
 * @description Uploads an artifact to S3 and then updates a Clouf Formation stack in AWS
 * @goal update-cf
 * @phase deploy
 */
public class CloudFormationMojo extends AbstractMojo {
	
	/**
	 * Maven Settings (settings.xml)
	 * @parameter property="settings"
	 * @required
	 * @readonly
	 */
	protected Settings settings;
	
	/**
	 * Server ID in Maven settings.xml
	 * @parameter property="serverId"
	 */
	private String serverId;
	
	/**
	 * @parameter property="accessKey"
	 */
	private String accessKey;
	
	/**
	 * @parameter property="secretKey"
	 */
	private String secretKey;
	
	/**
	 * @parameter property="bucketName"
	 * @required
	 */
	private String bucketName;
	
	/**
	 * @parameter property="region" default-value="us-east-1"
	 * @required
	 */
	private String region;
	
	/**
	 * @parameter property="stackNames"
	 * @required
	 */
	private List<String> stackNames;
	
    /**
     * Location of the artifact file.
     * @parameter property="artifactFile" default-value="${project.build.directory}/${project.build.finalName}"
     * @required
     */
    private File artifactFile;
    
    /**
     * <stackParameters>
     * 	<key1>value1</key1>
     * </stackParameters>
     * @parameter property="stackParameters"
     */
    private Map<String, String> stackParameters;

    public void execute() throws MojoExecutionException {
        getLog().info("Bucket Name: " + bucketName);
        //getLog().info("Cloud Formation Stack Name: " + stackName);
        
        if (artifactFile == null || !artifactFile.isFile()) {
        	throw new MojoExecutionException("Cannot find artifact file to upload");
        }
        String artifactKey = artifactFile.getName();
        getLog().info("Artifact Name: " + artifactKey);
        
        BasicAWSCredentials awsCredentials = new BasicAWSCredentials(accessKey, secretKey);
        AmazonCloudFormationClient cfClient = new AmazonCloudFormationClient(awsCredentials);
        cfClient.setEndpoint(getCloudFormationEndPoint());
        AmazonS3Client s3Client = new AmazonS3Client(awsCredentials);
        
        // Upload Artifact to S3
        try {
        	getLog().info("Uploading artifact to S3...");
        	s3Client.putObject(bucketName, artifactKey, artifactFile);
        } catch (AmazonServiceException e) {
        	throw new MojoExecutionException("[SERVICE] Could Not Upload File to S3", e);
        } catch (AmazonClientException e) {
        	throw new MojoExecutionException("[CLIENT] Could Not Upload File to S3", e);
        }
        
        // Update each stack with the new artifact file
        for(String stackName : stackNames) {
        	getLog().info("Cloud Formation Stack Name: " + stackName);
        	String templateBody = getTemplateBody(cfClient, stackName);
        	Stack stack = getStack(cfClient, stackName);
        	
        	// If passed additional parameters, update them
            List<Parameter> parameters = stack.getParameters();
            if (stackParameters != null && !stackParameters.isEmpty()) {
            	List<Parameter> tmpParams = new ArrayList<Parameter>();
            	
            	// Add Existing Parameters we haven't locally overwritten
            	for(Parameter oldParam : parameters) {
            		String oldKey = oldParam.getParameterKey();
            		if (!stackParameters.containsKey(oldKey)) {
            			tmpParams.add(oldParam);
            		}
            	}
            	
            	// Add Overwrite parameters
            	for(String key : stackParameters.keySet()) {
            		Parameter newParam = new Parameter();
            		newParam.setParameterKey(key);
            		newParam.setParameterValue(stackParameters.get(key));
            		tmpParams.add(newParam);
            	}
            	parameters = tmpParams;
            }
            
            // Update the Stack
            UpdateStackRequest updateStackRequest = new UpdateStackRequest();
            updateStackRequest.setStackName(stackName);
            updateStackRequest.setTemplateBody(templateBody);
            updateStackRequest.setParameters(parameters);
            updateStackRequest.setCapabilities(stack.getCapabilities());
            try {
            	getLog().info("Updating Cloud Formation Stack...");
            	cfClient.updateStack(updateStackRequest);
            } catch (AmazonServiceException e) {
    	    	throw new MojoExecutionException("[SERVICE] Could Not Update Cloud Formation Stack", e);
    	    } catch (AmazonClientException e) {
    	    	throw new MojoExecutionException("[CLIENT] Could Not Update Cloud Formation Stack", e);
    	    }
            getLog().info("Cloud Formation Stack " + stackName + "is now updating...");
        }
        
        getLog().info("All stacks have been updated. Complete.");
    }
    
    protected String getTemplateBody(AmazonCloudFormationClient cfClient, String stackName) throws MojoExecutionException {
    	String templateBody = null;
        try {
            GetTemplateRequest getTemplateRequest = new GetTemplateRequest();
            getTemplateRequest.setStackName(stackName);
        	getLog().info("Getting Cloud Formation Stack Template...");
	        GetTemplateResult getTemplateResult = cfClient.getTemplate(getTemplateRequest);
	        if (getTemplateResult == null) {
	        	throw new MojoExecutionException("[NULL] Could Not Get Cloud Formation Stack Template");
	        }
	        templateBody = getTemplateResult.getTemplateBody();
        } catch (AmazonServiceException e) {
        	throw new MojoExecutionException("[SERVICE] Could Not Get Cloud Formation Stack Template", e);
        } catch (AmazonClientException e) {
        	throw new MojoExecutionException("[CLIENT] Could Not Get Cloud Formation Stack Template", e);
        }
        return templateBody;
    }
    
    protected Stack getStack(AmazonCloudFormationClient cfClient, String stackName) throws MojoExecutionException {
    	Stack stack = null;
        try {
            DescribeStacksRequest describeStacksRequest = new DescribeStacksRequest();
            describeStacksRequest.setStackName(stackName);
        	getLog().info("Getting Cloud Formation Stack Details...");
        	DescribeStacksResult describeStacksResult = cfClient.describeStacks(describeStacksRequest);
        	if (describeStacksResult == null || describeStacksResult.getStacks() == null || describeStacksResult.getStacks().isEmpty()) {
        		throw new MojoExecutionException("[NULL] Could Not Get Cloud Formation Stack Details");
        	}
        	stack = describeStacksResult.getStacks().get(0);
	    } catch (AmazonServiceException e) {
	    	throw new MojoExecutionException("[SERVICE] Could Not Get Cloud Formation Stack Details", e);
	    } catch (AmazonClientException e) {
	    	throw new MojoExecutionException("[CLIENT] Could Not Get Cloud Formation Stack Details", e);
	    }
        return stack;
    }
    
    protected AWSCredentials getAWSCredentials() throws MojoExecutionException {
    	if (settings != null && serverId != null) {
    		Server server = settings.getServer(serverId);
    		if (server != null) {
    			accessKey = server.getUsername().trim();
    			secretKey = server.getPassword().trim();
    			// TODO: Decrypt https://bitbucket.org/aldrinleal/beanstalker/src/d72b183f832cd81c670ca1e4ae764868cdfd16b9/beanstalker-common/src/main/java/br/com/ingenieux/mojo/aws/AbstractAWSMojo.java?at=default
    		}
    	}
    	if (accessKey == null || secretKey == null || accessKey.isEmpty() || secretKey.isEmpty()) {
    		throw new MojoExecutionException("Missing either accessKey and secretKey.");
    	}
    	return new BasicAWSCredentials(accessKey, secretKey);
    }
    
    /**
     * @see http://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/cloudformation/AmazonCloudFormationClient.html#setEndpoint(java.lang.String, java.lang.String, java.lang.String)
     * @return CloudFormation EndPoint
     */
    protected String getCloudFormationEndPoint() {
    	return "https://cloudformation." + region + ".amazonaws.com";
    }
}
