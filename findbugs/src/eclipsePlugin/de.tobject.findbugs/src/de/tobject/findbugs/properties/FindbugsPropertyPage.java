/*
 * FindBugs Eclipse Plug-in.
 * Copyright (C) 2003, Peter Friese
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

package de.tobject.findbugs.properties;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.dialogs.PropertyPage;

import de.tobject.findbugs.FindbugsPlugin;
import de.tobject.findbugs.marker.FindBugsMarker;
import de.tobject.findbugs.util.ProjectUtilities;
import edu.umd.cs.findbugs.BugPattern;
import edu.umd.cs.findbugs.DetectorFactory;
import edu.umd.cs.findbugs.DetectorFactoryCollection;

public class FindbugsPropertyPage extends PropertyPage {

	private static final String COLUMN_PROPS_DESCRIPTION = "description"; //$NON-NLS-1$
	private static final String COLUMN_PROPS_PRIORITY = "priority"; //$NON-NLS-1$
	private static final String COLUMN_PROPS_NAME = "name"; //$NON-NLS-1$
	private boolean initialEnabled;
	private Button chkEnableFindBugs;
	private IProject project;
	protected TableViewer availableRulesTableViewer;

	/**
	 * Constructor for SamplePropertyPage.
	 */
	public FindbugsPropertyPage() {
		super();
	}

	/**
	 * @see PreferencePage#createContents(Composite)
	 */
	protected Control createContents(Composite parent) {

		noDefaultAndApplyButton();

		// getElement returns the element this page has been opened for,
		// in our case this is a Java Project (IJavaProject).
		IAdaptable resource = getElement();
		IJavaProject javaProject =
			(IJavaProject) resource.getAdapter(IJavaProject.class);
		if (javaProject != null) {
			// get the IProject underlying the IJavaProject
			this.project = javaProject.getProject();
		}

		Composite composite = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		composite.setLayout(layout);

		chkEnableFindBugs = new Button(composite, SWT.CHECK);
		chkEnableFindBugs.setText("Enable FindBugs");
		initialEnabled = isEnabled();
		chkEnableFindBugs.setSelection(initialEnabled);

		Label separator =
			new Label(composite, SWT.SEPARATOR | SWT.SHADOW_IN | SWT.HORIZONTAL);
		GridData data = new GridData();
		data.horizontalAlignment = GridData.FILL;
		data.grabExcessHorizontalSpace = true;
		separator.setLayoutData(data);

		buildLabel(composite, "Select bug patterns to check for:");
		Table availableRulesTable =
			buildAvailableRulesTableViewer(composite, project);
		data = new GridData();
		data.grabExcessHorizontalSpace = true;
		data.grabExcessVerticalSpace = true;
		data.horizontalAlignment = GridData.FILL;
		data.verticalAlignment = GridData.FILL;
		data.heightHint = 50;
		availableRulesTable.setLayoutData(data);

		return composite;
	}

	/**
	 * Build rule table viewer
	 */
	private Table buildAvailableRulesTableViewer(
		Composite parent,
		IProject project) {
		final BugPatternTableSorter sorter = new BugPatternTableSorter();

		int tableStyle =
			SWT.BORDER
				| SWT.H_SCROLL
				| SWT.V_SCROLL
				| SWT.SINGLE
				| SWT.FULL_SELECTION
				| SWT.CHECK;
		availableRulesTableViewer =
			CheckboxTableViewer.newCheckList(parent, tableStyle);

		Table ruleTable = availableRulesTableViewer.getTable();
		TableColumn ruleNameColumn = new TableColumn(ruleTable, SWT.LEFT);
		ruleNameColumn.setResizable(true);
		ruleNameColumn.setText(getMessage("Factory name"));
		ruleNameColumn.setWidth(200);
		ruleNameColumn.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				// TODO hardcoded column idx
				sorter.setSortColumnIndex(0);
				availableRulesTableViewer.refresh();
			}
		});

		TableColumn rulePriorityColumn = new TableColumn(ruleTable, SWT.LEFT);
		rulePriorityColumn.setResizable(true);
		rulePriorityColumn.setText(getMessage("Bug detector speed"));
		rulePriorityColumn.setWidth(110);
		rulePriorityColumn.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				// TODO hardcoded column idx
				sorter.setSortColumnIndex(1);
				availableRulesTableViewer.refresh();
			}
		});

		TableColumn ruleDescriptionColumn = new TableColumn(ruleTable, SWT.LEFT);
		ruleDescriptionColumn.setResizable(true);
		ruleDescriptionColumn.setText(getMessage("Detector description"));
		ruleDescriptionColumn.setWidth(200);

		ruleTable.setLinesVisible(true);
		ruleTable.setHeaderVisible(true);

		availableRulesTableViewer.setContentProvider(
			new DetectorFactoriesContentProvider());
		availableRulesTableViewer.setLabelProvider(
			new DetectorFactoryLabelProvider());
		availableRulesTableViewer.setColumnProperties(
			new String[] {
				COLUMN_PROPS_NAME,
				COLUMN_PROPS_PRIORITY,
				COLUMN_PROPS_DESCRIPTION });

		availableRulesTableViewer.setSorter(sorter);

		populateAvailableRulesTable(project);
		ruleTable.setEnabled(true);

		return ruleTable;
	}

	/**
	 * Populate the rule table
	 */
	private void populateAvailableRulesTable(IProject project) {
		List selectedFactoryList;
		try {
			selectedFactoryList = FindbugsPlugin.readDetectorFactories(project);
		}
		catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}

		List allAvailableList = new ArrayList();
		Iterator iterator =
			DetectorFactoryCollection.instance().factoryIterator();
		while (iterator.hasNext()) {
			DetectorFactory factory = (DetectorFactory) iterator.next();
			allAvailableList.add(factory);

			// XXX factory list from FindBugs is singleton - we share!!!
			// same factories between multiple projects!!!
			factory.setEnabled(selectedFactoryList.contains(factory));
		}

		availableRulesTableViewer.setInput(allAvailableList);
		TableItem[] itemList = availableRulesTableViewer.getTable().getItems();
		for (int i = 0; i < itemList.length; i++) {
			Object rule = itemList[i].getData();
			//set enabled if defined in configuration
			if (selectedFactoryList.contains(rule)) {
				itemList[i].setChecked(true);
			}
		}
	}

	/**
	 * Build a label
	 */
	private Label buildLabel(Composite parent, String msgKey) {
		Label label = new Label(parent, SWT.NONE);
		String message = getMessage(msgKey);
		label.setText(message == null ? msgKey : message);
		return label;
	}

	/**
	 * Will be called when the user presses the OK button.
	 * @see IPreferencePage#performOk()
	 */
	public boolean performOk() {
		boolean selection = this.chkEnableFindBugs.getSelection();
		boolean result = true;
		storeDetectorFactories(project, getSelectedDetectorFactories());
		if (!this.initialEnabled && selection == true) {
			result = addNature();
		}
		else if (this.initialEnabled && selection == false) {
			result = removeNature();
		}
		return result;
	}

	/**
	 * Using the natures name, check whether the current
	 * project has the given nature.
	 *
	 * @return boolean <code>true</code>, if the nature is
	 *   assigned to the project, <code>false</code> otherwise.
	 */
	private boolean isEnabled() {
		boolean result = false;

		try {
			if (this.project.hasNature(FindbugsPlugin.NATURE_ID))
				result = true;
		}
		catch (CoreException e) {
			System.err.println("Exception: " + e); //$NON-NLS-1$
		}
		return result;
	}

	/**
	 * Add the nature to the current project. The real work is
	 * done by the inner class NatureWorker
	 * @return boolean <code>true</code> if the nature could
	 *   be added successfully, <code>false</code> otherwise.
	 */
	private boolean addNature() {
		boolean result = true;
		try {
			NatureWorker worker = new NatureWorker(true);
			ProgressMonitorDialog monitor = new ProgressMonitorDialog(getShell());
			monitor.run(true, true, worker);
		}
		catch (InvocationTargetException e) {
			System.err.println("Exception: " + e); //$NON-NLS-1$
		}
		catch (InterruptedException e) {
			System.err.println("Exception: " + e); //$NON-NLS-1$
		}
		return result;
	}

	/**
	 * Remove the nature from the project.
	 * @return boolean <code>true</code> if the nature could
	 *   be added successfully, <code>false</code> otherwise.
	 */
	private boolean removeNature() {
		boolean result = true;
		try {
			// remove any markers added by our builder
			this.project.deleteMarkers(
				FindBugsMarker.NAME,
				true,
				IResource.DEPTH_INFINITE);

			NatureWorker worker = new NatureWorker(false);
			ProgressMonitorDialog monitor = new ProgressMonitorDialog(getShell());
			monitor.run(true, true, worker);
		}
		catch (InvocationTargetException e) {
			System.err.println("Exception: " + e); //$NON-NLS-1$
		}
		catch (InterruptedException e) {
			System.err.println("Exception: " + e); //$NON-NLS-1$
		}
		catch (CoreException e) {
			System.err.println("Exception: " + e); //$NON-NLS-1$
		}
		return result;
	}

	private final class NatureWorker implements IRunnableWithProgress {
		private boolean add = true;

		public NatureWorker(boolean add) {
			this.add = add;
		}

		/**
		 * @see IRunnableWithProgress#run(IProgressMonitor)
		 */
		public void run(IProgressMonitor monitor) {
			try {
				if (add) {
					ProjectUtilities.addFindBugsNature(project, monitor);
				}
				else {
					ProjectUtilities.removeFindBugsNature(project, monitor);
				}
			}
			catch (CoreException e) {
				e.printStackTrace();
				System.err.println("Exception: " + e); //$NON-NLS-1$
			}
		}
	}

	/**
	 * Helper method to shorten message access
	 * @param key a message key
	 * @return requested message
	 */
	protected String getMessage(String key) {
		return FindbugsPlugin.getDefault().getMessage(key);
	}

	/**
	 * Get user selected bug factories from view
	 * @return list with elements instanceof DetectorFactory
	 */
	protected List getSelectedDetectorFactories() {
		TableItem[] itemList = availableRulesTableViewer.getTable().getItems();
		List detectorFactoriesList = new ArrayList();
		for (int i = 0; i < itemList.length; i++) {
			Object factory = itemList[i].getData();
			//set enabled if defined in configuration
			if (itemList[i].getChecked()) {
				detectorFactoriesList.add(factory);
			}
		}
		return detectorFactoriesList;
	}

	/**
	 * Store the detectors selection in project property
	 */
	protected void storeDetectorFactories(IProject project, List factoryList) {
		try {
			StringBuffer selectionList = new StringBuffer();
			Iterator i = factoryList.iterator();
			while (i.hasNext()) {
				DetectorFactory rule = (DetectorFactory) i.next();
				selectionList.append(rule.getShortName()).append(
					FindbugsPlugin.LIST_DELIMITER);
			}

			project.setPersistentProperty(
				FindbugsPlugin.PERSISTENT_PROPERTY_ACTIVE_DETECTORS,
				selectionList.toString());
			project.setSessionProperty(
				FindbugsPlugin.PERSISTENT_PROPERTY_ACTIVE_DETECTORS,
				factoryList);
		}
		catch (CoreException e) {
			// TODO exception handling
			e.printStackTrace();
		}
	}

	/**
	 * @author Andrei
	 */
	private static final class BugPatternTableSorter
		extends ViewerSorter
		implements Comparator {
		private int sortColumnIndex;
		private int lastSortColumnIdx;
		boolean revertOrder;

		public int compare(Viewer viewer, Object e1, Object e2) {
			return compare(e1, e2);
		}

		/**
		 * @param e1
		 * @param e2
		 * @return
		 */
		public int compare(Object e1, Object e2) {
			int result = 0;
			DetectorFactory factory1 = (DetectorFactory) e1;
			DetectorFactory factory2 = (DetectorFactory) e2;
			String s1, s2;
			switch (getSortColumnIndex()) {
				case 0 :
					s1 = "" + factory1.getShortName(); //$NON-NLS-1$
					s2 = factory2.getShortName();
					break;
				case 1 :
					s1 = "" + factory1.getSpeed(); //$NON-NLS-1$
					s2 = factory2.getSpeed();
					break;
				default :
					s1 = "" + factory1.getSpeed(); //$NON-NLS-1$
					s2 = factory2.getSpeed();
					break;
			}

			result = s1.compareTo(s2);

			// second sort if elements are equals - on opposite criteria
			if (result == 0) {
				switch (getSortColumnIndex()) {
					case 0 :
						s1 = "" + factory1.getSpeed(); //$NON-NLS-1$
						s2 = factory2.getSpeed();
						break;
					case 1 :
						s1 = "" + factory1.getShortName(); //$NON-NLS-1$
						s2 = factory2.getShortName();
						break;
				}
				result = s1.compareTo(s2);
			}
			else if (revertOrder) {
				// same column selected twice - revert first order
				result = -result;
			}
			return result;
		}

		public boolean isSorterProperty(Object element, String property) {
			return property.equals(COLUMN_PROPS_NAME)
				|| property.equals(COLUMN_PROPS_PRIORITY);
		}

		/**
		 * @param sortColumnIndex The sortColumnIndex to set.
		 */
		public void setSortColumnIndex(int sortColumnIndex) {
			this.lastSortColumnIdx = this.sortColumnIndex;
			this.sortColumnIndex = sortColumnIndex;
			revertOrder = !revertOrder && lastSortColumnIdx == sortColumnIndex;
		}

		/**
		 * @return Returns the sortColumnIndex.
		 */
		public int getSortColumnIndex() {
			return sortColumnIndex;
		}
	}

	/**
	 * @author Andrei
	 */
	private static final class DetectorFactoryLabelProvider
		implements ITableLabelProvider {

		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.IBaseLabelProvider#addListener(org.eclipse.jface.viewers.ILabelProviderListener)
		 */
		public void addListener(ILabelProviderListener listener) {
			// ignored
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.IBaseLabelProvider#dispose()
		 */
		public void dispose() {
			// ignored
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.IBaseLabelProvider#isLabelProperty(java.lang.Object, java.lang.String)
		 */
		public boolean isLabelProperty(Object element, String property) {
			return false;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.IBaseLabelProvider#removeListener(org.eclipse.jface.viewers.ILabelProviderListener)
		 */
		public void removeListener(ILabelProviderListener listener) {
			// ignored
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnImage(java.lang.Object, int)
		 */
		public Image getColumnImage(Object element, int columnIndex) {
			// TODO ignored - but if we have images for different detectors ...
			return null;
		}

		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.ITableLabelProvider#getColumnText(java.lang.Object, int)
		 */
		public String getColumnText(Object element, int columnIndex) {

			if (!(element instanceof DetectorFactory)) {
				return null;
			}
			DetectorFactory factory = (DetectorFactory) element;

			switch (columnIndex) {
				case 0 :
					return factory.getShortName();
				case 1 :
					return factory.getSpeed();
				case 2 :
					StringBuffer sb = new StringBuffer();
					Collection patterns = factory.getReportedBugPatterns();
					for (Iterator iter = patterns.iterator(); iter.hasNext();) {
						BugPattern pattern = (BugPattern) iter.next();
						sb.append(pattern.getShortDescription());
						if (iter.hasNext()) {
							sb.append(" | "); //$NON-NLS-1$
						}
					}
					return sb.toString();
				default :
					return null;
			}
		}

	}
	/**
	 * @author Andrei
	 */
	private static final class DetectorFactoriesContentProvider
		implements IStructuredContentProvider {
		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.IContentProvider#dispose()
		 */
		public void dispose() {
			// ignored
		}
		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
		 */
		public void inputChanged(
			Viewer viewer,
			Object oldInput,
			Object newInput) {
			// ignored
		}
		/* (non-Javadoc)
		 * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(java.lang.Object)
		 */
		public Object[] getElements(Object inputElement) {
			if (inputElement instanceof List) {
				List list = (List) inputElement;
				return list.toArray();
			}
			return null;
		}
	}

}