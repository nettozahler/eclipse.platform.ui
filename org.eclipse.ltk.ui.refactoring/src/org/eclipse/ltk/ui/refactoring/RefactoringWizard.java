/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ltk.ui.refactoring;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.Wizard;

import org.eclipse.ui.PlatformUI;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CheckConditionsOperation;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringCore;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.internal.ui.refactoring.Assert;
import org.eclipse.ltk.internal.ui.refactoring.ChangeExceptionHandler;
import org.eclipse.ltk.internal.ui.refactoring.ErrorWizardPage;
import org.eclipse.ltk.internal.ui.refactoring.ExceptionHandler;
import org.eclipse.ltk.internal.ui.refactoring.IPreviewWizardPage;
import org.eclipse.ltk.internal.ui.refactoring.InternalAPI;
import org.eclipse.ltk.internal.ui.refactoring.PreviewWizardPage;
import org.eclipse.ltk.internal.ui.refactoring.RefactoringPluginImages;
import org.eclipse.ltk.internal.ui.refactoring.RefactoringUIMessages;
import org.eclipse.ltk.internal.ui.refactoring.RefactoringUIPlugin;
import org.eclipse.ltk.internal.ui.refactoring.WorkbenchRunnableAdapter;

/**
 * An abstract base implementation of a refactoring wizard. A refactoring
 * wizard differs from a normal wizard in the following characteristics:
 * <ul>
 *   <li>only pages of type {@link org.eclipse.ltk.ui.refactoring.RefactoringWizardPage
 *       RefactoringWizardPage} can be added to a refactoring wizard. Trying to
 *       add a different kind of page results in an exception.</li>
 *   <li>a refactoring wizard consists of 0 .. n user input pages, one error page
 *       to present the outcome of the refactoring's condition checking and one
 *       preview page to present a preview of the workspace changes.</li> 
 * </ul> 
 * 
 * @see org.eclipse.ltk.core.refactoring.Refactoring
 * 
 * @since 3.0
 */
public abstract class RefactoringWizard extends Wizard {

	/** 
	 * Flag indicating that no special flags are provided.
	 */
	public static final int NONE= 0;
	
	/**
	 * Flag indicating that the initial condition checking of the refactoring is done when 
	 * the wizard opens. If not specified it is assumed that the initial condition checking
	 * has been done by the client before opening the wizard dialog. 
	 */
	public static final int CHECK_INITIAL_CONDITIONS_ON_OPEN= 1 << 0;
	
	/**
	 * Flag indicating that a normal wizard based user interface consisting
	 * of a back, next, finish and cancel button should be used to present
	 * this refactoring wizard.
	 */
	public static final int WIZARD_BASED_USER_INTERFACE= 1 << 1;
	
	/**
	 * Flag indicating that a lightweight dialog based user interface should
	 * be used to present this refactoring wizard. This user interface consists
	 * of a preview, finish and cancel button and the initial size of dialog
	 * is based on the first user input page. This flag is only valid if only
	 * one user input page is present. Specifying this flag together with more
	 * than one input page will result in an exception when adding the user input
	 * pages.
	 */
	public static final int DIALOG_BASED_UESR_INTERFACE= 1 << 2;
	
	/**
	 * Flag indicating that the finish and cancel button should be named
	 * yes and no. The flag is ignored if the flag {@link #WIZARD_BASED_USER_INTERFACE}
	 * is specified.
	 */
	public static final int YES_NO_BUTTON_STYLE= 1 << 3;
	/**
	 * Flag indicating that the wizard should not show a preview page.
	 * The flag is ignored if the flag {@link #WIZARD_BASED_USER_INTERFACE}
	 * is specified.
	 * */
	public static final int NO_PREVIEW_PAGE= 1 << 4;
	
	/**
	 * Flag indicating that the first change node presented in the
	 * preview page should be fully expanded.
	 */
	public static final int PREVIEW_EXPAND_FIRST_NODE= 1 << 5;
	
	private static final int LAST= 1 << 6;
	
	private int fFlags;
	private Refactoring fRefactoring;
	private String fDefaultPageTitle;
	
	private Change fChange;
	private RefactoringStatus fInitialConditionCheckingStatus= new RefactoringStatus();
	private RefactoringStatus fConditionCheckingStatus;
	
	private int fUserInputPages;
	private boolean fInAddPages;
	
	private boolean fIsChangeCreationCancelable;
	private boolean fForcePreviewReview;
	private boolean fPreviewShown;
	
	/**
	 * Creates a new refactoring wizard for the given refactoring. 
	 * 
	 * @param refactoring the refactoring the wizard is presenting
	 * @param flags flags specifying the bahaviour of the wizard. If neither 
	 *  <code>WIZARD_BASED_USER_INTERFACE</code> nor <code>DIALOG_BASED_UESR_INTERFACE</code> 
	 *  is specified then <code>WIZARD_BASED_USER_INTERFACE</code> will be
	 *  taken as a default.
	 */
	public RefactoringWizard(Refactoring refactoring, int flags) {
		Assert.isNotNull(refactoring);
		Assert.isTrue(flags < LAST);
		if ((flags & DIALOG_BASED_UESR_INTERFACE) == 0) 
			flags |= WIZARD_BASED_USER_INTERFACE;
		Assert.isTrue((flags & DIALOG_BASED_UESR_INTERFACE) != 0 || (flags & WIZARD_BASED_USER_INTERFACE) != 0);
		fRefactoring= refactoring;
		fFlags= flags;
		setNeedsProgressMonitor(true);
		setChangeCreationCancelable(true);
		setWindowTitle(RefactoringUIMessages.getString("RefactoringWizard.title")); //$NON-NLS-1$
		setDefaultPageImageDescriptor(RefactoringPluginImages.DESC_WIZBAN_REFACTOR);
	} 
	
	//---- Setter and Getters ------------------------------------------------------------
	
	/**
	 * Returns the refactoring this wizard is associated with.
	 * 
	 * @return the wizard's refactoring
	 */	
	public final Refactoring getRefactoring(){
		return fRefactoring;
	}
	
	/**
	 * Sets the default page title to the given value. This value is used
	 * as a page title for wizard pages which don't provide their own
	 * page title. Setting this value has only an effect as long as the
	 * user interface hasn't been created yet. 
	 * 
	 * @param defaultPageTitle the default page title.
	 * @see Wizard#setDefaultPageImageDescriptor(org.eclipse.jface.resource.ImageDescriptor)
	 */
	public final void setDefaultPageTitle(String defaultPageTitle) {
		Assert.isNotNull(defaultPageTitle);
		fDefaultPageTitle= defaultPageTitle;
	}
	
	/**
	 * Returns the default page title used for pages that don't provide their 
	 * own page title.
	 * 
	 * @return the default page title
	 * 
	 * @see #setDefaultPageTitle(String)
	 */
	public final String getDefaultPageTitle() {
		return fDefaultPageTitle;
	}
	
	/**
	 * If set to <code>true</code> the Finish or OK button, respectively will
	 * be disabled until the user has visited the preview page. If set to
	 * <code>false</code> the refactoring can be performed before the preview
	 * page has been visited.
	 * 
	 * @param forcePreviewReview if <code>true</code> to user must confirm the
	 *  preview
	 */
	public final void setForcePreviewReview(boolean forcePreviewReview) {
		fForcePreviewReview= forcePreviewReview;
		getContainer().updateButtons();	
	}
	
	/**
	 * Returns the width in characters to be used for the message line embedded into
	 * the refactoring wizard dialog used to present this wizard.
	 * <p>
	 * Subclasses may override this method and return a different value.
	 * </p>
	 * 
	 * @return the message lines width in characters
	 */
	public int getMessageLineWidthInChars() {
		return 80;
	}
	
	/**
	 * If set to <code>true</code> the change creation is cancelable by the user.
	 * <p>
	 * By default, change creation is cancelable.
	 * </p>
	 * @param isChangeCreationCancelable determines whether the change creation
	 *  is cancelable by the user or not.
	 * 
	 * @see Refactoring#createChange(IProgressMonitor)
	 */
	public final void setChangeCreationCancelable(boolean isChangeCreationCancelable){
		fIsChangeCreationCancelable= isChangeCreationCancelable;
	}
	
	/**
	 * Sets the initial condition checking status compute by the refactoring.
	 * Clients should uses this method if the initial condition checking
	 * status has been computed outside of this refactoring wizard.
	 * 
	 * @param status the initial condition checking status.
	 * 
	 * @see Refactoring#checkInitialConditions(IProgressMonitor)
	 * @see #CHECK_INITIAL_CONDITIONS_ON_OPEN
	 */
	public final void setInitialConditionCheckingStatus(RefactoringStatus status) {
		Assert.isNotNull(status);
		fInitialConditionCheckingStatus= status;
		setConditionCheckingStatus(status);
	}
		
	/**
	 * Returns the refactoring's change object or <code>null</code> if no change
	 * object has been created yet.
	 * 
	 * @return the refactoring's change object or <code>null</code>
	 * 
	 * @see Refactoring#createChange(IProgressMonitor)
	 */
	public final Change getChange() {
		return fChange;
	}
	
	/**
	 * Returns the status of the initial condition checking or <code>null</code>
	 * if the initial condition checking hasn't been performed yet.
	 * 
	 * @return the status of the initial condition checking or <code>null</code>
	 * 
	 * @see Refactoring#checkInitialConditions(IProgressMonitor)
	 */
	/* package */ final RefactoringStatus getInitialConditionCheckingStatus() {
		return fInitialConditionCheckingStatus;
	}
	
	/**
	 * Returns <code>true</code> if the wizard needs a wizard based user interface.
	 * Otherwise <code>false</code> is returned.
	 * 
	 * @return whether the wizard needs a wizard based user interface or not
	 */
	/* package */ boolean needsWizardBasedUserInterface() {
		return (fFlags & WIZARD_BASED_USER_INTERFACE) != 0;
	}
	
	//---- Page management ------------------------------------------------------------

	/**
	 * {@inheritDoc}
	 * 
	 * This method calls the hook method {@link #addUserInputPages()} to allow
	 * subclasses to add specific user input pages.
	 */
	public final void addPages() {
		Assert.isNotNull(fRefactoring);
		try {
			fInAddPages= true;
			if (checkActivationOnOpen()) {
				internalCheckCondition(CheckConditionsOperation.INITIAL_CONDITONS);
			}
			if (fInitialConditionCheckingStatus.hasFatalError()) {
				addErrorPage();
				// Set the status since we added the error page
				setConditionCheckingStatus(getConditionCheckingStatus());	
			} else { 
				Assert.isTrue(getPageCount() == 0);
				addUserInputPages();
				fUserInputPages= getPageCount();
				if (fUserInputPages > 0) {
					IWizardPage[] pages= getPages();
					((UserInputWizardPage)pages[fUserInputPages - 1]).markAsLastUserInputPage();
				}
				if (fUserInputPages > 1) {
					Assert.isTrue((fFlags & WIZARD_BASED_USER_INTERFACE) != 0);
				}
				addErrorPage();
				addPreviewPage();	
			}
			initializeDefaultPageTitles();
		} finally {
			fInAddPages= false;
		}
	}
	
	/**
	 * {@inheritDoc}
	 * 
	 * This method ensures that the pages added to the refactoring wizard
	 * are instances of type {@link RefactoringWizardPage}.
	 */
	public final void addPage(IWizardPage page) {
		Assert.isTrue(page instanceof RefactoringWizardPage && fInAddPages);
		super.addPage(page);
	}
	
	/**
	 * Hook method to add user input pages to this refactoring wizard. Pages
	 * added via this call have to be instances of the type {@link UserInputWizardPage}.
	 * Adding pages of a different kind is not permitted and will result
	 * in unexpected behavior.
	 */
	protected abstract void addUserInputPages();
	
	private void addErrorPage(){
		addPage(new ErrorWizardPage());
	}
	
	private void addPreviewPage(){
		addPage(new PreviewWizardPage());
	}
	
	private boolean hasUserInput() {
		return fUserInputPages > 0;		
	}
	
	private void initializeDefaultPageTitles() {
		if (fDefaultPageTitle == null)
			return;
			
		IWizardPage[] pages= getPages();
		for (int i= 0; i < pages.length; i++) {
			IWizardPage page= pages[i];
			if (page.getTitle() == null)
				page.setTitle(fDefaultPageTitle);
		}
	}
	
	//---- Page computation -----------------------------------------------------------
	
	/**
	 * {@inheritDoc}
	 */
	public IWizardPage getStartingPage() {
		if (hasUserInput())
			return super.getStartingPage();
		return computeUserInputSuccessorPage(null, PlatformUI.getWorkbench().getActiveWorkbenchWindow());
	}
	
	/**
	 * {@inheritDoc}
	 */
	public IWizardPage getPreviousPage(IWizardPage page) {
		if (hasUserInput())
			return super.getPreviousPage(page);
		if (! page.getName().equals(ErrorWizardPage.PAGE_NAME)){
			if (fConditionCheckingStatus.isOK())
				return null;
		}		
		return super.getPreviousPage(page);		
	}

	/* package */ IWizardPage computeUserInputSuccessorPage(IWizardPage caller, IRunnableContext context) {
		Change change= createChange(new CreateChangeOperation(
			new CheckConditionsOperation(fRefactoring, CheckConditionsOperation.FINAL_CONDITIONS),
			RefactoringStatus.OK), true, context);
		// Status has been updated since we have passed true
		RefactoringStatus status= getConditionCheckingStatus();
		
		// Creating the change has been canceled
		if (change == null && status == null) {		
			setChange(InternalAPI.INSTANCE, change);
			return caller;
		}
				
		// Set change if we don't have fatal errors.
		if (!status.hasFatalError())
			setChange(InternalAPI.INSTANCE, change);
		
		if (status.isOK()) {
			return getPage(IPreviewWizardPage.PAGE_NAME);
		} else {
			return getPage(ErrorWizardPage.PAGE_NAME);
		}
	} 
	
	public boolean canFinish() {
		if (fForcePreviewReview && !fPreviewShown)
			return false;
		return super.canFinish();
	}

	//---- Condition checking ------------------------------------------------------------

	/* package */ final RefactoringStatus checkFinalConditions() {
		return internalCheckCondition(CheckConditionsOperation.FINAL_CONDITIONS);
	}
	
	private RefactoringStatus internalCheckCondition(int style) {
		
		CheckConditionsOperation op= new CheckConditionsOperation(fRefactoring, style); 

		Exception exception= null;
		try {
			PlatformUI.getWorkbench().getProgressService().busyCursorWhile(
				new WorkbenchRunnableAdapter(op, ResourcesPlugin.getWorkspace().getRoot()));
		} catch (InterruptedException e) {
			exception= e;
		} catch (InvocationTargetException e) {
			exception= e;
		}
		RefactoringStatus status= null;
		if (exception != null) {
			RefactoringUIPlugin.log(exception);
			status= new RefactoringStatus();
			status.addFatalError(RefactoringUIMessages.getString("RefactoringWizard.internal_error_1")); //$NON-NLS-1$
		} else {
			status= op.getStatus();
		}
		setConditionCheckingStatus(status, style);
		return status;	
	}
	
	private void setConditionCheckingStatus(RefactoringStatus status, int style) {
		if ((style & CheckConditionsOperation.ALL_CONDITIONS) == CheckConditionsOperation.ALL_CONDITIONS)
			setConditionCheckingStatus(status);
		else if ((style & CheckConditionsOperation.INITIAL_CONDITONS) == CheckConditionsOperation.INITIAL_CONDITONS)
			setInitialConditionCheckingStatus(status);
		else if ((style & CheckConditionsOperation.FINAL_CONDITIONS) == CheckConditionsOperation.FINAL_CONDITIONS)
			setFinalConditionCheckingStatus(status);
	}

	private RefactoringStatus getConditionCheckingStatus() {
		return fConditionCheckingStatus;
	} 
		
	/**
	 * Sets the refactoring status.
	 * 
	 * @param status the refactoring status to set.
	 */
	/* package */ final void setConditionCheckingStatus(RefactoringStatus status) {
		ErrorWizardPage page= (ErrorWizardPage)getPage(ErrorWizardPage.PAGE_NAME);
		if (page != null)
			page.setStatus(status);
		fConditionCheckingStatus= status;
	}
	
	/**
	 * Sets the refactoring status returned from final condition checking. Any previously 
	 * computed initial status is merged into the given status before it is set to the 
	 * error page.
	 * 
	 * @param status the final condition checking status to set
	 */
	private void setFinalConditionCheckingStatus(RefactoringStatus status) {
		RefactoringStatus newStatus= new RefactoringStatus();
		if (fInitialConditionCheckingStatus != null)
			newStatus.merge(fInitialConditionCheckingStatus);
		newStatus.merge(status);	
		setConditionCheckingStatus(newStatus);			
	}
	
	//---- Change management -------------------------------------------------------------

	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 */
	public final Change createChange(InternalAPI api, CreateChangeOperation operation, boolean updateStatus) {
		Assert.isNotNull(api);
		return createChange(operation, updateStatus, getContainer());
	}

	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 */
	public final boolean performFinish(InternalAPI api, PerformChangeOperation op) {
		return performRefactoring(op, fRefactoring, getContainer(), getContainer().getShell());
	}
	
	private Change createChange(CreateChangeOperation operation, boolean updateStatus, IRunnableContext context){
		InvocationTargetException exception= null;
		try {
			context.run(true, fIsChangeCreationCancelable, new WorkbenchRunnableAdapter(
				operation, ResourcesPlugin.getWorkspace().getRoot()));
		} catch (InterruptedException e) {
			setConditionCheckingStatus(null);
			return null;
		} catch (InvocationTargetException e) {
			exception= e;
		}
		
		if (updateStatus) {
			RefactoringStatus status= null;
			if (exception != null) {
				status= new RefactoringStatus();
				String msg= exception.getMessage();
				if (msg != null) {
					status.addFatalError(RefactoringUIMessages.getFormattedString("RefactoringWizard.see_log", msg)); //$NON-NLS-1$
				} else {
					status.addFatalError(RefactoringUIMessages.getString("RefactoringWizard.Internal_error")); //$NON-NLS-1$
				}
				RefactoringUIPlugin.log(exception);
			} else {
				status= operation.getConditionCheckingStatus();
			}
			setConditionCheckingStatus(status, operation.getConditionCheckingStyle());
		} else {
			if (exception != null)
				ExceptionHandler.handle(exception, getContainer().getShell(), 
					RefactoringUIMessages.getString("RefactoringWizard.refactoring"),  //$NON-NLS-1$
					RefactoringUIMessages.getString("RefactoringWizard.unexpected_exception")); //$NON-NLS-1$
		}
		Change change= operation.getChange();	
		return change;
	}

	//---- Reimplementation of Wizard methods --------------------------------------------

	public boolean performFinish() {
		Assert.isNotNull(fRefactoring);
		
		RefactoringWizardPage page= (RefactoringWizardPage)getContainer().getCurrentPage();
		return page.performFinish();
	}
	
	//---- Internal API, but public due to Java constraints ------------------------------
	
	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 */
	public final boolean hasPreviewPage(InternalAPI api) {
		Assert.isNotNull(api);
		return (fFlags & NO_PREVIEW_PAGE) == 0;
	}
	
	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 */
	public final boolean yesNoStyle(InternalAPI api) {
		Assert.isNotNull(api);
		return (fFlags & YES_NO_BUTTON_STYLE) != 0;
	}
	
	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 */
	public final boolean getExpandFirstNode(InternalAPI api) {
		Assert.isNotNull(api);
		return (fFlags & PREVIEW_EXPAND_FIRST_NODE) != 0;
	}
	
	/**
	 * Note: This method is for internal use only. Clients should not call this method.
	 */
	public final void setChange(InternalAPI api, Change change){
		Assert.isNotNull(api);
		IPreviewWizardPage page= (IPreviewWizardPage)getPage(IPreviewWizardPage.PAGE_NAME);
		if (page != null)
			page.setChange(change);
		fChange= change;
	}

	public final void setPreviewShown(InternalAPI api, boolean shown) {
		Assert.isNotNull(api);
		fPreviewShown= shown;
		getContainer().updateButtons();
	}
	
	//---- Helper methods to check style bits --------------------------------------------
	
	private boolean checkActivationOnOpen() {
		return (fFlags & CHECK_INITIAL_CONDITIONS_ON_OPEN) != 0;
	}
	
	//---- Private helper methods --------------------------------------------------------
	
	private static boolean performRefactoring(PerformChangeOperation op, Refactoring refactoring, IRunnableContext execContext, Shell parent) {
		op.setUndoManager(RefactoringCore.getUndoManager(), refactoring.getName());
		try{
			execContext.run(false, false, new WorkbenchRunnableAdapter(op, ResourcesPlugin.getWorkspace().getRoot()));
		} catch (InvocationTargetException e) {
			Throwable inner= e.getTargetException();
			if (op.changeExecutionFailed()) {
				ChangeExceptionHandler handler= new ChangeExceptionHandler(parent, refactoring);
				if (inner instanceof RuntimeException) {
					handler.handle(op.getChange(), (RuntimeException)inner);
					return false;
				} else if (inner instanceof CoreException) {
					handler.handle(op.getChange(), (CoreException)inner);
					return false;
				}
			}
			ExceptionHandler.handle(e, parent, 
				RefactoringUIMessages.getString("RefactoringWizard.refactoring"), //$NON-NLS-1$
				RefactoringUIMessages.getString("RefactoringWizard.unexpected_exception_1")); //$NON-NLS-1$
			return false;
		} catch (InterruptedException e) {
			return false;
		} 
		return true;
	}	
	
}
