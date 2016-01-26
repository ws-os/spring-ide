/*******************************************************************************
 * Copyright (c) 2015, 2016 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.cloudfoundry.deployment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.cloudfoundry.client.lib.domain.CloudApplication;
import org.cloudfoundry.client.lib.domain.CloudDomain;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.springframework.ide.eclipse.boot.dash.cloudfoundry.CloudApplicationURL;
import org.springsource.ide.eclipse.commons.frameworks.core.ExceptionUtil;
import org.springsource.ide.eclipse.commons.livexp.core.LiveSet;
import org.springsource.ide.eclipse.commons.livexp.core.LiveVariable;
import org.springsource.ide.eclipse.commons.livexp.core.ValidationResult;
import org.springsource.ide.eclipse.commons.livexp.core.Validator;

public class CloudApplicationDeploymentProperties implements DeploymentProperties {

	public static final int DEFAULT_INSTANCES = 1;

	protected final LiveSet<String> boundServices = new LiveSet<String>(new HashSet<String>());
	protected final LiveVariable<Map<String, String>> environmentVariables = new LiveVariable<Map<String, String>>(
			new HashMap<String, String>());
	protected final LiveVariable<String> buildpack = new LiveVariable<String>("");

	protected final LiveVariable<Integer> instances = new LiveVariable<Integer>(DEFAULT_INSTANCES);

	private LiveVariable<Boolean> writeManifest = new LiveVariable<>(false);

	public static final int DEFAULT_MEMORY = 1024;

	/*
	 * URLs should never be null. If no URLs are needed, keep list empty
	 */
	protected final LiveSet<String> urls = new LiveSet<String>();

	protected final LiveVariable<String> host = new LiveVariable<>();

	protected final LiveVariable<String> domain = new LiveVariable<>();

	protected final LiveVariable<String> appName = new LiveVariable<>();

	protected final LiveVariable<IProject> project = new LiveVariable<>();

	protected final LiveVariable<Integer> memory = new LiveVariable<Integer>(DEFAULT_MEMORY);

	protected final LiveVariable<IFile> manifestFile = new LiveVariable<IFile>();

	protected Validator validator;

	public CloudApplicationDeploymentProperties() {

	}

	/*
	 * Additional properties
	 */

	private boolean shouldRestart = true;

	public void setProject(IProject project) {
		this.project.setValue(project);
	}

	public IProject getProject() {
		return this.project.getValue();
	}

	public void setMemory(int memory) {
		this.memory.setValue(memory);
	}

	public int getMemory() {
		return memory.getValue();
	}

	public void setManifestFile(IFile file) {
		this.manifestFile.setValue(file);
	}

	public IFile getManifestFile() {
		return this.manifestFile.getValue();
	}

	/**
	 * Returns a copy of the list of URLs for the application
	 *
	 * @return never null
	 */
	public List<String> getUrls() {
		return this.urls.getValues();
	}

	public void setUrls(List<String> urls) {
		if (urls == null) {
			urls = new ArrayList<String>();
		}
		this.urls.addAll(urls);
	}

	public String getHost() {
		return host.getValue();
	}

	public void setHost(String value) {
		host.setValue(value);
	}

	public String getDomain() {
		return domain.getValue();
	}

	public void setDomain(String value) {
		domain.setValue(value);
	}

	public void setAppName(String appName) {
		this.appName.setValue(appName);
	}

	public String getAppName() {
		return this.appName.getValue();
	}

	/**
	 *
	 * @return non-null validator
	 */
	public Validator getValidator() {
		return this.validator == null ? this.validator = new BasicValidator() : this.validator;
	}

	public void setBuildpack(String buildpack) {
		this.buildpack.setValue(buildpack);
	}

	public void setServices(List<String> services) {
		if (services == null) {
			services = new ArrayList<String>();
		}
		this.boundServices.addAll(services);
	}

	public void setInstances(int instances) {
		this.instances.setValue(instances);
	}

	public void setEnvironmentVariables(Map<String, String> environmentVariables) {
		if (environmentVariables == null) {
			environmentVariables = new HashMap<String, String>();
		}
		this.environmentVariables.setValue(environmentVariables);
	}

	public void setShouldRestart(boolean shouldRestart) {
		this.shouldRestart = shouldRestart;
	}

	public boolean shouldRestart() {
		return this.shouldRestart;
	}

	public String getBuildpack() {
		return buildpack.getValue() == null || buildpack.getValue().isEmpty() ? null : buildpack.getValue();
	}

	public int getInstances() {
		return instances.getValue();
	}

	public boolean writeManifest() {
		return writeManifest.getValue();
	}

	public void setWriteManifest(boolean writeManifest) {
		this.writeManifest.setValue(writeManifest);
	}

	public void addValidator(Validator validator) {

		this.validator = validator;

		if (validator != null) {
			validator.dependsOn(boundServices);
			validator.dependsOn(environmentVariables);
			validator.dependsOn(buildpack);
			validator.dependsOn(memory);
			validator.dependsOn(memory);
			validator.dependsOn(appName);
			validator.dependsOn(project);
			validator.dependsOn(manifestFile);
		}
	}

	/**
	 *
	 * @return never null
	 */
	public Map<String, String> getEnvironmentVariables() {
		return environmentVariables.getValue();
	}

	/**
	 *
	 * @return never null
	 */
	public List<String> getServices() {
		return boundServices.getValues();
	}

	/**
	 * Merges properties of this source into a target properties. Properties
	 * like application name and project in the user defined version replace
	 * those in the target one. However, URLs and other "collections" are merged
	 * into the target collections, rather than replace them.
	 *
	 * @param toMerge
	 *            target properties that will be updated with properties from
	 *            this source.
	 * @return merged properties or null if no properties to merge
	 */
	public CloudApplicationDeploymentProperties mergeInto(CloudApplicationDeploymentProperties target) {
		if (target == null) {
			return null;
		}

		target.setAppName(this.getAppName());
		target.setProject(this.getProject());
		target.setHost(getHost());
		target.setDomain(getDomain());

		// Instead of replacing URLs, merge them
		List<String> targetUrls = target.getUrls();
		for (String url : this.getUrls()) {
			if (!targetUrls.contains(url)) {
				targetUrls.add(url);
			}
		}
		target.setUrls(targetUrls);
		return target;

	}

	public static CloudApplicationDeploymentProperties getFor(IProject project, List<CloudDomain> domains, CloudApplication app) throws Exception {

		CloudApplicationDeploymentProperties properties = new CloudApplicationDeploymentProperties();

		properties.setAppName(app == null ? project.getName() : app.getName());
		properties.setProject(project);
		properties.setBuildpack(app == null || app.getStaging() == null ? null : app.getStaging().getBuildpackUrl());
		/*
		 * TODO: Re-evaluate whether JAVA_OPTS need to be treated differently
		 * Boot Dash Tooling adds staff to JAVA-OPTS behind the scenes. Consider
		 * JAVA_OPTS env variable as the one not exposed to users
		 */
		Map<String, String> env = new LinkedHashMap<>();
		if (app != null) {
			env.putAll(app.getEnvAsMap());
			env.remove("JAVA_OPTS");
		}
		properties.setEnvironmentVariables(env);

		properties.setInstances(app == null ? 1 : app.getInstances());
		properties.setMemory(app == null ? 1024 : app.getMemory());
		properties.setServices(app == null ? Collections.<String>emptyList() : app.getServices());

		if (app == null) {
			CloudApplicationURL cloudAppUrl = new CloudApplicationURL(project.getName(), domains.get(0).getName());
			properties.setUrls(Collections.singletonList(cloudAppUrl.getUrl()));
			properties.setHost(cloudAppUrl.getSubdomain());
			properties.setDomain(cloudAppUrl.getDomain());
		} else {
			properties.setUrls(app.getUris());
			for (String url : properties.getUrls()) {
				try {
					// Find the first valid URL
					CloudApplicationURL cloudAppUrl = CloudApplicationURL.getCloudApplicationURL(url, domains);
					properties.setHost(cloudAppUrl.getSubdomain());
					properties.setDomain(cloudAppUrl.getDomain());
					break;
				} catch (Exception e) {
					// ignore
				}
			}
		}
		Validator validator = properties.getValidator();

		ValidationResult result = validator.getValue();
		if (!result.isOk()) {
			throw ExceptionUtil.coreException(result.msg);
		}

		return properties;
	}

	class BasicValidator extends DeploymentPropertiesValidator {

		@Override
		protected CloudApplicationDeploymentProperties getProperties() {
			return CloudApplicationDeploymentProperties.this;
		}

	}

}