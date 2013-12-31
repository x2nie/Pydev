/**
 * Copyright (c) 2005-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Eclipse Public License (EPL).
 * Please see the license.txt included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
/*
 * Created on Apr 8, 2006
 */
package com.python.pydev.refactoring.wizards.rename;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.python.pydev.core.docutils.PyStringUtils;
import org.python.pydev.editor.refactoring.IPyRefactoringRequest;
import org.python.pydev.editor.refactoring.MultiModuleMoveRefactoringRequest;
import org.python.pydev.plugin.preferences.PydevPrefs;

import com.python.pydev.refactoring.wizards.TextInputWizardPage;

public class PyRenameRefactoringWizard extends RefactoringWizard {

    private static final String UPDATE_REFERENCES = "UPDATE_REFERENCES";
    private final String fInputPageDescription;
    private IPyRefactoringRequest fRequest;
    private TextInputWizardPage inputPage;
    private String fInitialSetting;

    public PyRenameRefactoringWizard(Refactoring refactoring, String defaultPageTitle, String inputPageDescription,
            IPyRefactoringRequest request) {
        super(refactoring, DIALOG_BASED_USER_INTERFACE);
        setDefaultPageTitle(defaultPageTitle);
        fInputPageDescription = inputPageDescription;
        this.fRequest = request;
        this.fInitialSetting = request.getInitialName();
        Assert.isNotNull(this.fInitialSetting);
    }

    /* non java-doc
     * @see RefactoringWizard#addUserInputPages
     */
    @Override
    protected void addUserInputPages() {
        inputPage = createInputPage(fInputPageDescription, fInitialSetting);
        addPage(inputPage);
    }

    protected TextInputWizardPage createInputPage(String message, String initialSetting) {
        return new TextInputWizardPage(message, true, initialSetting) {
            @Override
            protected RefactoringStatus validateTextField(String text) {
                RefactoringStatus status = new RefactoringStatus();
                if (PyStringUtils.isValidIdentifier(text, fRequest.isModuleRenameRefactoringRequest())) {
                    fRequest.setInputName(text);
                } else {
                    status.addFatalError("The name: " + text + " is not a valid identifier.");
                }
                return status;
            }

            @Override
            protected Text createTextInputField(Composite parent, int style) {
                Text ret = super.createTextInputField(parent, style);
                String text = ret.getText();
                int i = text.lastIndexOf('.');
                if (i >= 0) {
                    ret.setSelection(i + 1, text.length());
                } else {
                    ret.selectAll();
                }
                return ret;
            }

            @Override
            protected void textModified(String text) {
                if (fRequest instanceof MultiModuleMoveRefactoringRequest) {
                    RefactoringStatus status;
                    if (text.length() == 0) {
                        //Accept empty for move!
                        status = new RefactoringStatus();
                        status.addInfo("Empty text: move to source foder");
                    } else {
                        status = validateTextField(text);
                    }

                    if (!status.hasFatalError()) {
                        fRequest.setInputName(text);
                    }
                    setPageComplete(status);
                } else {
                    super.textModified(text);
                }
            }

            public void createControl(Composite parent) {
                Composite superComposite = new Composite(parent, SWT.NONE);
                setControl(superComposite);
                initializeDialogUnits(superComposite);

                superComposite.setLayout(new GridLayout());
                Composite composite = new Composite(superComposite, SWT.NONE);
                composite.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

                GridLayout layout = new GridLayout();
                layout.numColumns = 2;
                layout.verticalSpacing = 8;
                composite.setLayout(layout);

                Label label = new Label(composite, SWT.NONE);
                label.setText("New &value:");

                Text text = createTextInputField(composite);
                GridData gd = new GridData(GridData.FILL_HORIZONTAL);
                gd.widthHint = convertWidthInCharsToPixels(25);
                text.setLayoutData(gd);

                //                layouter.perform(label, text, 1);
                //                
                if (fRequest.isModuleRenameRefactoringRequest()) {
                    addOptionalUpdateReferencesCheckbox(composite);
                }
                //                addOptionalUpdateTextualMatches(composite, layouter);
                //                addOptionalUpdateQualifiedNameComponent(composite, layouter, layout.marginWidth);

                Dialog.applyDialogFont(superComposite);
            }
        };
    }

    protected void addOptionalUpdateReferencesCheckbox(Composite result) {
        final Button updateReferences = new Button(result, SWT.CHECK);
        updateReferences.setText("&Update References?");

        IPreferenceStore preferences = PydevPrefs.getPreferences();
        preferences.setDefault(UPDATE_REFERENCES, true);//Default is always true to update references.
        boolean updateRefs = preferences.getBoolean(UPDATE_REFERENCES);
        updateReferences.setSelection(updateRefs);
        fRequest.setUpdateReferences(updateRefs);
        updateReferences.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                IPreferenceStore preferences = PydevPrefs.getPreferences();
                boolean updateRefs = updateReferences.getSelection();
                preferences.setValue(UPDATE_REFERENCES, updateRefs);
                fRequest.setUpdateReferences(updateRefs);
            }

        });
        GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
        gridData.horizontalSpan = 2;
        updateReferences.setLayoutData(gridData);

    }
}
