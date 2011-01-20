/*******************************************************************************
 * Copyright (c) 2006, 2007 Spring IDE Developers
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Spring IDE Developers - initial API and implementation
 *******************************************************************************/
package org.springframework.ide.eclipse.aop.core.model;

/**
 * @author Christian Dupuis
 */
public interface IAopModelChangedListener {

	/**
	 * Indicate that there has been a change in the set of advised elements
	 * 
	 */
	public void changed();
}