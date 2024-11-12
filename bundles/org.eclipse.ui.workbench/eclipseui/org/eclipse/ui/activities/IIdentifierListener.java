/*******************************************************************************
 * Copyright (c) 2000, 2015 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ui.activities;

/**
 * An instance of this interface can be used by clients to receive notification
 * of changes to one or more instances of <code>IIdentifier</code>.
 * <p>
 * This interface may be implemented by clients.
 * </p>
 *
 * @since 3.0
 * @see IIdentifier#addIdentifierListener(IIdentifierListener)
 * @see IIdentifier#removeIdentifierListener(IIdentifierListener)
 */
public interface IIdentifierListener {

	/**
	 * Notifies that one or more properties of an instance of
	 * <code>IIdentifier</code> have changed. Specific details are described in the
	 * <code>IdentifierEvent</code>.
	 *
	 * @param identifierEvent the identifier event. Guaranteed not to be
	 *                        <code>null</code>.
	 */
	void identifierChanged(IdentifierEvent identifierEvent);
}