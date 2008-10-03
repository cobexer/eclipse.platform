/*******************************************************************************
 * Copyright (c) 2007, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.importexport.launchconfigurations;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.internal.core.IInternalDebugCoreConstants;
import org.eclipse.debug.internal.core.LaunchConfiguration;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.IDebugHelpContextIds;
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants;
import org.eclipse.debug.internal.ui.SWTFactory;
import org.eclipse.debug.internal.ui.launchConfigurations.LaunchCategoryFilter;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.model.WorkbenchViewerComparator;
import org.eclipse.ui.progress.UIJob;

import com.ibm.icu.text.MessageFormat;

/**
 * This calls provides the one and only wizard page to the 
 * export launch configurations wizard.
 * @since 3.4.0
 */
public class ExportLaunchConfigurationsWizardPage extends WizardPage {

	/**
	 * The content provider for the tree viewer
	 * @since 3.4.0
	 */
	class ConfigContentProvider implements ITreeContentProvider {

		ILaunchManager lm = DebugPlugin.getDefault().getLaunchManager();
		
		public Object[] getChildren(Object parentElement) {
			if(parentElement instanceof ILaunchConfigurationType) {
				try {
					return lm.getLaunchConfigurations((ILaunchConfigurationType) parentElement);
				}
				catch (Exception e) {
					DebugUIPlugin.logErrorMessage(e.getMessage());
				}
			}
			return null;
		}
		public Object getParent(Object element) {
			if(element instanceof ILaunchConfiguration) {
				try {
					return ((ILaunchConfiguration)element).getType();
				} catch (CoreException e) {
					return null;
				}
			}
			return null;
		}
		public boolean hasChildren(Object element) {
			return element instanceof ILaunchConfigurationType;
		}
		public Object[] getElements(Object inputElement) {
			return lm.getLaunchConfigurationTypes();
		}
		public void dispose() {lm = null;}
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {}
		
	}
	private String OVERWRITE = "overwrite"; //$NON-NLS-1$
	private String OLD_PATH = "oldpath"; //$NON-NLS-1$
	private CheckboxTreeViewer fViewer = null;
	private Text fFilePath = null;
	private Button fOverwrite = null;
	
	/**
	 * Constructor
	 */
	protected ExportLaunchConfigurationsWizardPage() {
		super(WizardMessages.ExportLaunchConfigurationsWizard_0);
		setTitle(WizardMessages.ExportLaunchConfigurationsWizard_0);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		Composite comp = SWTFactory.createComposite(parent, 2, 1, GridData.FILL_BOTH);
	  //add the check table 
		createViewer(comp);
	  //add the file path and browse button
		createFilePath(comp);
	  //add the overwrite option
		fOverwrite = SWTFactory.createCheckButton(comp, WizardMessages.ExportLaunchConfigurationsWizardPage_1, null, getDialogSettings().getBoolean(OVERWRITE), 2);
		setControl(comp);
		PlatformUI .getWorkbench().getHelpSystem().setHelp(comp, IDebugHelpContextIds.EXPORT_LAUNCH_CONFIGURATIONS_PAGE);
		setMessage(WizardMessages.ExportLaunchConfigurationsWizardPage_7); 
		//do not set page complete, Eclipse UI guidelines states wizards cannot start off with an error showing
		setPageComplete(false);
	}

	/**
	 * Creates the check table viewer portion of the control
	 * @param parent the parent to add the check table viewer to
	 */
	protected void createViewer(Composite parent) {
		SWTFactory.createWrapLabel(parent, WizardMessages.ExportLaunchConfigurationsWizardPage_3, 2);
		Tree tree = new Tree(parent, SWT.BORDER | SWT.SINGLE | SWT.CHECK);
		GridData gd = new GridData(GridData.FILL_BOTH);
		gd.horizontalSpan = 2;
		tree.setLayoutData(gd);
		fViewer = new CheckboxTreeViewer(tree);
		fViewer.setLabelProvider(DebugUITools.newDebugModelPresentation());
		fViewer.setComparator(new WorkbenchViewerComparator());
		fViewer.setContentProvider(new ConfigContentProvider());
		fViewer.setInput(DebugPlugin.getDefault().getLaunchManager().getLaunchConfigurationTypes());
		//we don't want to see builders....
		fViewer.addFilter(new LaunchCategoryFilter(IInternalDebugUIConstants.ID_EXTERNAL_TOOL_BUILDER_LAUNCH_CATEGORY));
		//need to force load the children so that select all works initially
		fViewer.expandAll();
		fViewer.collapseAll();
		fViewer.addCheckStateListener(new ICheckStateListener() {
			public void checkStateChanged(CheckStateChangedEvent event) {
				updateCheckedState(event.getElement());
				setPageComplete(isComplete());
			}
		});
		Composite buttoncomp = SWTFactory.createComposite(parent, parent.getFont(), 2, 2, GridData.FILL_HORIZONTAL, 0, 0);
		Button button = SWTFactory.createPushButton(buttoncomp, WizardMessages.ExportLaunchConfigurationsWizardPage_8, null); 
		button.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				fViewer.setAllChecked(true);
				setPageComplete(isComplete());
			}
		});
		button = SWTFactory.createPushButton(buttoncomp, WizardMessages.ExportLaunchConfigurationsWizardPage_9, null); 
		button.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				fViewer.setAllChecked(false);
				setPageComplete(isComplete());
			}
		});
	}
	
	/**
	 * Updates the checked state of child launch configurations if the parent type is checked
	 * @param item
	 */
	protected void updateCheckedState(Object element) {
		boolean state = fViewer.getChecked(element);
		if(element instanceof ILaunchConfigurationType) {
			Object[] items = ((ConfigContentProvider)fViewer.getContentProvider()).getChildren(element);
			for(int i = 0; i < items.length; i++) {
				fViewer.setChecked(items[i], state);
			}
			fViewer.setGrayed(element, false);
		}
		else if(element instanceof ILaunchConfiguration) {
			ConfigContentProvider ccp = (ConfigContentProvider) fViewer.getContentProvider();
			Object parent = ccp.getParent(element);
			Object[] items = ccp.getChildren(parent);
			boolean checked = true;
			boolean onechecked = false;
			for(int i = 0; i < items.length; i++) {
				state = fViewer.getChecked(items[i]);
				checked &= state;
				if(state) {
					onechecked = true;
				}
			}
			fViewer.setGrayed(parent, onechecked & !checked);
			fViewer.setChecked(parent, checked | onechecked);
		}
	}
	
	/**
	 * Creates the controls for the file path selection area of the page
	 * @param parent
	 */
	protected void createFilePath(Composite parent) {
		Composite comp = SWTFactory.createComposite(parent, parent.getFont(), 3, 2, GridData.FILL_HORIZONTAL, 0, 10);
		SWTFactory.createLabel(comp, WizardMessages.ExportLaunchConfigurationsWizardPage_4, 1);
		fFilePath = SWTFactory.createText(comp, SWT.SINGLE | SWT.BORDER, 1);
		String opath = getDialogSettings().get(OLD_PATH);
		fFilePath.setText((opath == null ? IInternalDebugCoreConstants.EMPTY_STRING : opath));
		fFilePath.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				setPageComplete(isComplete());
			}
		});
		Button button = SWTFactory.createPushButton(comp, WizardMessages.ExportLaunchConfigurationsWizardPage_0, null, GridData.END);
		button.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				DirectoryDialog dd = new DirectoryDialog(getContainer().getShell());
				dd.setText(WizardMessages.ExportLaunchConfigurationsWizard_0);
				String file = dd.open();
				if(file != null) {
					IPath path = new Path(file);
					if (path != null) {
						fFilePath.setText(path.toString());
						setPageComplete(isComplete());
					}
				}
			}
		});
	}

	/**
	 * Returns if the page is complete
	 * @return true if the page is complete and can be 'finished', false otherwise
	 */
	protected boolean isComplete() {
		Object[] elements = fViewer.getCheckedElements();
		boolean oneconfig = false;
		for(int i = 0; i < elements.length; i++) {
			if(elements[i] instanceof ILaunchConfiguration) {
				oneconfig = true;
				break;
			}
		}
		if(elements.length < 1 || !oneconfig) {
			setErrorMessage(WizardMessages.ExportLaunchConfigurationsWizardPage_5);
			return false;
		}
		String path = fFilePath.getText().trim();
		if(path.equals(IInternalDebugCoreConstants.EMPTY_STRING)) {
			setErrorMessage(WizardMessages.ExportLaunchConfigurationsWizardPage_6);
			return false;
		}
		if ((new File(path)).isFile()) {
			setErrorMessage(WizardMessages.ExportLaunchConfigurationsWizardPage_2);
			return false;
		}
		setErrorMessage(null);
		setMessage(WizardMessages.ExportLaunchConfigurationsWizardPage_7);
		return true;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.wizard.WizardPage#getImage()
	 */
	public Image getImage() {
		return DebugUITools.getImage(IInternalDebugUIConstants.IMG_WIZBAN_EXPORT_CONFIGS);
	}
	
	/**
	 * This method performs the work of the page
	 * @return if the export job was successful or not
	 */
	public boolean finish() {
		final String dpath = fFilePath.getText().trim();
		IDialogSettings settings = getDialogSettings();
		settings.put(OVERWRITE, fOverwrite.getSelection());
		settings.put(OLD_PATH, dpath);
		final Object[] configs = fViewer.getCheckedElements();
		final boolean overwrite = fOverwrite.getSelection();
		UIJob exportjob = new UIJob(getContainer().getShell().getDisplay(), WizardMessages.ExportLaunchConfigurationsWizard_0) {
			public IStatus runInUIThread(IProgressMonitor monitor) {
				if(monitor == null) {
					monitor = new NullProgressMonitor();
				}
				IPath destpath = new Path(dpath);
				File destfolder = destpath.toFile();
				if(!destfolder.exists()) {
					destfolder.mkdirs();
				}
				monitor.beginTask(WizardMessages.ExportLaunchConfigurationsWizardPage_10, configs.length);
				try {
					List errors = null;
					IFileStore file = null;
					File newfile = null;
					boolean owall = false, nowall = false;
					MessageDialog dialog = null;
					for(int i = 0; i < configs.length; i++) {
						if(monitor.isCanceled()) {
							return Status.CANCEL_STATUS;
						}
						if(configs[i] instanceof ILaunchConfiguration) {
							try {
								LaunchConfiguration launchConfig = (LaunchConfiguration) configs[i];
								file = launchConfig.getFileStore();
								if (file == null) {
									if (errors == null) {
										errors = new ArrayList(configs.length);
									}
									errors.add(new Status(IStatus.ERROR, DebugUIPlugin.getUniqueIdentifier(),
											MessageFormat.format(WizardMessages.ExportLaunchConfigurationsWizardPage_19, new String[]{launchConfig.getName()}), null));
								} else {
									newfile = new File(destpath.append(file.getName()).toOSString());
									if(newfile.exists() & !overwrite) {
										if(nowall) {
											continue;
										}
										dialog = new MessageDialog(DebugUIPlugin.getShell(), 
												WizardMessages.ExportLaunchConfigurationsWizardPage_11, 
												null, 
												MessageFormat.format(WizardMessages.ExportLaunchConfigurationsWizardPage_12, new String[] {file.getName()}), 
												MessageDialog.QUESTION, new String[] {WizardMessages.ExportLaunchConfigurationsWizardPage_13, WizardMessages.ExportLaunchConfigurationsWizardPage_14, WizardMessages.ExportLaunchConfigurationsWizardPage_15, WizardMessages.ExportLaunchConfigurationsWizardPage_16, WizardMessages.ExportLaunchConfigurationsWizardPage_17}, 0);
										if(!owall) {
											int ret = dialog.open();
											switch(ret) {
												case 0: {
													copyFile(file, newfile);
													break;
												}
												case 1: {
													owall = true;
													copyFile(file, newfile);
													break;
												}
												case 3: {
													nowall = true;
													break;
												}
												case 4: {
													monitor.setCanceled(true);
													break;
												}
											}
										}
										else if(!nowall) {
											copyFile(file, newfile);
										}
									}
									else {
										copyFile(file, newfile);
									}
								}
							}
							catch (IOException e ) {
								if (errors == null) {
									errors = new ArrayList(configs.length);
								}
								errors.add(new Status(IStatus.ERROR, DebugUIPlugin.getUniqueIdentifier(),
										e.getMessage(), e));
							}
							catch (CoreException e) {
								if (errors == null) {
									errors = new ArrayList(configs.length);
								}
								errors.add(e.getStatus());
							}
						}
						if(!monitor.isCanceled()) {
							monitor.worked(1);
						}
					}
					if (errors == null || errors.isEmpty()) {
						return Status.OK_STATUS;
					} else {
						if (errors.size() == 1) {
							return (IStatus)errors.get(0);
						} else {
							return new MultiStatus(DebugUIPlugin.getUniqueIdentifier(), 0,
									(IStatus[])errors.toArray(new IStatus[errors.size()]),
									WizardMessages.ExportLaunchConfigurationsWizardPage_18, null);
						}
					}
				}
				finally {
					monitor.done();
				}
			}
		};
		exportjob.schedule();
		return true;
	}
	
	/**
	 * Copies a file from one location to another
	 * @param in the file to copy
	 * @param out the file to be copied out to
	 * @throws Exception
	 * @since 3.5
	 */
	protected void copyFile(IFileStore in, File out) throws CoreException, IOException {
	    BufferedInputStream is  = new BufferedInputStream(in.openInputStream(EFS.NONE, null));
	    BufferedOutputStream os = new BufferedOutputStream(new FileOutputStream(out));
	    byte[] buf = new byte[1024];
	    int i = 0;
	    while((i = is.read(buf)) != -1) {
	    	os.write(buf, 0, i);
	    }
	    is.close();
	    os.close();
	}
}
