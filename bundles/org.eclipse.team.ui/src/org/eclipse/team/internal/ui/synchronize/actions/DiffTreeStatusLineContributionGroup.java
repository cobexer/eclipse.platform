/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ui.synchronize.actions;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.diff.*;
import org.eclipse.team.core.mapping.ISynchronizationContext;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.ui.mapping.ISynchronizationConstants;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;

public class DiffTreeStatusLineContributionGroup extends
		StatusLineContributionGroup implements IDiffChangeListener {

	public DiffTreeStatusLineContributionGroup(Shell shell, ISynchronizePageConfiguration configuration) {
		super(shell, configuration);
		getSynchronizationContext().getDiffTree().addDiffChangeListener(this);
	}
	
	public void dispose() {
		getSynchronizationContext().getDiffTree().removeDiffChangeListener(this);
		super.dispose();
	}

	protected int getChangeCount() {
		return getSynchronizationContext().getDiffTree().size();
	}

	private ISynchronizationContext getSynchronizationContext() {
		return (ISynchronizationContext)getConfiguration().getProperty(ISynchronizationConstants.P_SYNCHRONIZATION_CONTEXT);
	}

	protected int countFor(int state) {
		switch (state) {
		case SyncInfo.OUTGOING:
			state = IThreeWayDiff.OUTGOING;
			break;
		case SyncInfo.INCOMING:
			state = IThreeWayDiff.INCOMING;
			break;
		case SyncInfo.CONFLICTING:
			state = IThreeWayDiff.CONFLICTING;
			break;
		}
		return (int)getSynchronizationContext().getDiffTree().countFor(state, IThreeWayDiff.DIRECTION_MASK);
	}

	public void diffChanged(IDiffChangeEvent event, IProgressMonitor monitor) {
		updateCounts();
	}

}
