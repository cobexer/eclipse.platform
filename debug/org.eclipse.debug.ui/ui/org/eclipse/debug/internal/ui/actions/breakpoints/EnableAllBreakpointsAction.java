/*******************************************************************************
 * Copyright (c) 2024 IBM Corporation and others.
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
package org.eclipse.debug.internal.ui.actions.breakpoints;

import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.IBreakpointsListener;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.actions.AbstractEnableAllActionDelegate;
import org.eclipse.debug.internal.ui.actions.ActionMessages;
import org.eclipse.debug.internal.ui.preferences.IDebugPreferenceConstants;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ui.IWorkbenchWindow;

public class EnableAllBreakpointsAction extends AbstractEnableAllActionDelegate implements IBreakpointsListener {

	@Override
	protected boolean isEnabled() {
		boolean allEnabled = true;
		IBreakpointManager breakpointManager = DebugPlugin.getDefault().getBreakpointManager();
		IBreakpoint[] breakpoints = breakpointManager.getBreakpoints();

		for (IBreakpoint bp : breakpoints) {
			try {
				if (!bp.isEnabled()) {
					allEnabled = false;
					break;
				}
			} catch (CoreException e) {
				DebugUIPlugin.log(e);
			}
		}
		if (breakpoints.length > 0 && allEnabled) {
			return false;
		}
		return true;
	}

	@Override
	public void breakpointsAdded(IBreakpoint[] breakpoints) {
		update();
	}

	@Override
	public void breakpointsChanged(IBreakpoint[] breakpoints, IMarkerDelta[] deltas) {
		update();
	}

	@Override
	public void breakpointsRemoved(IBreakpoint[] breakpoints, IMarkerDelta[] deltas) {
		if (getAction() != null) {
			update();
		}
	}

	@Override
	protected void initialize() {
		DebugPlugin.getDefault().getBreakpointManager().addBreakpointListener(this);
	}

	@Override
	public void dispose() {
		DebugPlugin.getDefault().getBreakpointManager().removeBreakpointListener(this);
		super.dispose();
	}

	@Override
	public void run(IAction action) {
		IBreakpointManager breakpointManager = DebugPlugin.getDefault().getBreakpointManager();
		IBreakpoint[] breakpoints = breakpointManager.getBreakpoints();
		if (breakpoints.length < 1) {
			return;
		}
		IWorkbenchWindow window = DebugUIPlugin.getActiveWorkbenchWindow();
		if (window == null) {
			return;
		}
		IPreferenceStore store = DebugUIPlugin.getDefault().getPreferenceStore();
		boolean prompt = store.getBoolean(IDebugPreferenceConstants.PREF_PROMPT_ENABLE_ALL_BREAKPOINTS);
		boolean proceed = true;
		if (prompt) {
			MessageDialogWithToggle mdwt = MessageDialogWithToggle.openYesNoQuestion(window.getShell(),
					ActionMessages.EnableAllBreakpointsAction_0, ActionMessages.EnableAllBreakpointsAction_1,
					ActionMessages.EnableAllBreakpointsAction_3, !prompt, null, null);
			if (mdwt.getReturnCode() != IDialogConstants.YES_ID) {
				proceed = false;
			} else {
				store.setValue(IDebugPreferenceConstants.PREF_PROMPT_ENABLE_ALL_BREAKPOINTS, !mdwt.getToggleState());
			}
		}
		if (proceed) {
			new Job(ActionMessages.EnableAllBreakpointsAction_1) {
				@Override
				protected IStatus run(IProgressMonitor monitor) {
					try {
						for (IBreakpoint breakpoint : breakpoints) {
							breakpoint.setEnabled(true);
						}
					} catch (CoreException e) {
						DebugUIPlugin.log(e);
						return Status.CANCEL_STATUS;
					}
					return Status.OK_STATUS;
				}
			}.schedule();
		}
	}
}