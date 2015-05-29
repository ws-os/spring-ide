/*******************************************************************************
 * Copyright (c) 2015 Pivotal, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Pivotal, Inc. - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.boot.dash.model;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ISaveContext;
import org.eclipse.core.resources.ISaveParticipant;
import org.eclipse.core.resources.ISavedState;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.springframework.ide.eclipse.boot.dash.BootDashActivator;
import org.springsource.ide.eclipse.commons.frameworks.core.ExceptionUtil;

/**
 * @author Kris De Volder
 */
public class BootDashModelStateSaver implements ISaveParticipant {

	private static final String PREFERRED_LAUNCHES = "preferredLaunches";
	private Map<BootDashElement, ILaunchConfiguration> preferredLaunchconfigs = new HashMap<BootDashElement, ILaunchConfiguration>();
	private BootDashElementFactory factory;

	public BootDashModelStateSaver(BootDashElementFactory f) {
		this.factory = f;
	}

	@Override
	public void prepareToSave(ISaveContext context) throws CoreException {
	}

	@Override
	public void doneSaving(ISaveContext context) {
		BootDashActivator myPluginInstance = BootDashActivator.getDefault();
		// delete the old saved state since it is not necessary anymore
		int previousSaveNumber = context.getPreviousSaveNumber();
		String oldFileName = "save-" + Integer.toString(previousSaveNumber);
		File f = myPluginInstance.getStateLocation().append(oldFileName).toFile();
		f.delete();
	}

	@Override
	public void rollback(ISaveContext context) {
		BootDashActivator myPluginInstance = BootDashActivator.getDefault();
		// since the save operation has failed, delete the saved state we have just written
		int saveNumber = context.getSaveNumber();
		String saveFileName = PREFERRED_LAUNCHES+"-" +saveNumber;
		File f = myPluginInstance.getStateLocation().append(saveFileName).toFile();
		f.delete();
	}

	@Override
	public synchronized void saving(ISaveContext context) throws CoreException {
		try {
			int saveNum = context.getSaveNumber();
			IPath path = BootDashActivator.getDefault().getStateLocation().append(PREFERRED_LAUNCHES+"-"+saveNum);
			File file = path.toFile();
			Map<String, String> storage = new HashMap<String, String>();
			for (Entry<BootDashElement, ILaunchConfiguration> entry : preferredLaunchconfigs.entrySet()) {
				storage.put(entry.getKey().getProject().getName(), entry.getValue().getMemento());
			}
			ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file));
			try {
				out.writeObject(storage);
			} finally {
				out.close();
			}
			context.map(new Path(PREFERRED_LAUNCHES), path);
			context.needSaveNumber();
		} catch (Exception e) {
			throw ExceptionUtil.coreException(e);
		}
	}

	@SuppressWarnings("unchecked")
	public synchronized void restore(ISavedState lastState) {
		try {
			if (lastState!=null) {
				IPath path = lastState.lookup(new Path(PREFERRED_LAUNCHES));
				if (path!=null) {
					File file = path.toFile();
					ObjectInputStream in = new ObjectInputStream(new FileInputStream(file));
					try {
						Map<String, String> storage = (Map<String, String>) in.readObject();
						Map<BootDashElement, ILaunchConfiguration> restored = new HashMap<BootDashElement, ILaunchConfiguration>();
						for (Entry<String, String> e : storage.entrySet()) {
							BootDashElement p = getBootDashElement(e.getKey());
							if (p!=null) {
								ILaunchConfiguration v = getLaunchConf(e.getValue());
								if (v!=null) {
									restored.put(p, v);
								}
							}
						}
						preferredLaunchconfigs = restored;
					} finally {
						in.close();
					}
				}
			}
		} catch (Exception e) {
		}
	}

	private ILaunchConfiguration getLaunchConf(String memento) {
		try {
			ILaunchConfiguration conf = DebugPlugin.getDefault().getLaunchManager().getLaunchConfiguration(memento);
			if (conf.exists()) {
				return conf;
			}
		} catch (CoreException e) {
			BootDashActivator.log(e);
		}
		return null;
	}

	private BootDashElement getBootDashElement(String pname) {
		IProject p = ResourcesPlugin.getWorkspace().getRoot().getProject(pname);
		if (p!=null && p.exists()) {
			return factory.create(p);
		}
		return null;
	}

	public synchronized ILaunchConfiguration getPreferredConfigs(BootProjectDashElement e) {
		return preferredLaunchconfigs.get(e);
	}

	public synchronized void setPreferredConfig(BootProjectDashElement e,
			ILaunchConfiguration c) {
		preferredLaunchconfigs.put(e, c);
	}

}
