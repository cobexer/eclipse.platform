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
package org.eclipse.team.internal.ccvs.ui.mappings;

import org.eclipse.core.resources.mapping.ResourceMapping;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.ICache;
import org.eclipse.team.core.ICacheListener;
import org.eclipse.team.core.diff.*;
import org.eclipse.team.core.mapping.*;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.wizards.CommitWizard;
import org.eclipse.team.ui.mapping.ITeamContentProviderManager;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;
import org.eclipse.team.ui.synchronize.ModelParticipantAction;

/**
 * A commit action that will commit all outgoing canges in the context.
 */
public class WorkspaceCommitAction extends ModelParticipantAction implements IDiffChangeListener {

	/**
	 * Crate the action
	 * @param configuration the synchronize page configuration
	 */
	public WorkspaceCommitAction(ISynchronizePageConfiguration configuration) {
		super(null, configuration);
		final IDiffTree tree = getDiffTree();
		tree.addDiffChangeListener(this);
		getSynchronizationContext().getCache().addCacheListener(new ICacheListener() {
			public void cacheDisposed(ICache cache) {
				tree.removeDiffChangeListener(WorkspaceCommitAction.this);
			}
		});
		updateEnablement();
		
	}

	private IDiffTree getDiffTree() {
		ISynchronizationContext context = (ISynchronizationContext)getConfiguration().getProperty(ITeamContentProviderManager.P_SYNCHRONIZATION_CONTEXT);
		IDiffTree tree = context.getDiffTree();
		return tree;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.mapping.ModelProviderAction#isEnabledForSelection(org.eclipse.jface.viewers.IStructuredSelection)
	 */
	protected boolean isEnabledForSelection(IStructuredSelection selection) {
		// Enablement has nothing to do with selection
		return isEnabled();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.diff.IDiffChangeListener#diffChanged(org.eclipse.team.core.diff.IDiffChangeEvent, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void diffsChanged(IDiffChangeEvent event, IProgressMonitor monitor) {
		updateEnablement();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.core.diff.IDiffChangeListener#propertyChanged(int, org.eclipse.core.runtime.IPath[])
	 */
	public void propertyChanged(IDiffTree tree, int property, IPath[] paths) {
		// Do nothing
	}
	
	private void updateEnablement() {
		boolean enabled = (getDiffTree().countFor(IThreeWayDiff.OUTGOING, IThreeWayDiff.DIRECTION_MASK) > 0)
			&& (getDiffTree().countFor(IThreeWayDiff.CONFLICTING, IThreeWayDiff.DIRECTION_MASK) == 0);
		setEnabled(enabled);
	}
	
	public void run() {
		ISynchronizationContext context = (ISynchronizationContext)getConfiguration().getProperty(ITeamContentProviderManager.P_SYNCHRONIZATION_CONTEXT);
		ResourceMapping[] mappings = context.getScope().getMappings();
        Shell shell= getConfiguration().getSite().getShell();
        try {
        	// Include the subscriber operation as a job listener so that the busy feedback for the 
        	// commit will appear in the synchronize view
            CommitWizard.run(getConfiguration().getSite().getPart(), shell, mappings);
        } catch (CVSException e) {
            CVSUIPlugin.log(e);
        }
	}

}
