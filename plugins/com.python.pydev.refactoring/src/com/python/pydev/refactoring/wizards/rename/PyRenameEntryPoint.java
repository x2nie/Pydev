/**
 * Copyright (c) 2005-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Eclipse Public License (EPL).
 * Please see the license.txt included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
/*
 * Created on Mar 1, 2006
 */
package com.python.pydev.refactoring.wizards.rename;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RefactoringParticipant;
import org.eclipse.ltk.core.refactoring.participants.RenameProcessor;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;
import org.eclipse.text.edits.MultiTextEdit;
import org.python.pydev.core.IModule;
import org.python.pydev.core.docutils.PyStringUtils;
import org.python.pydev.core.log.Log;
import org.python.pydev.editor.codecompletion.revisited.visitors.Definition;
import org.python.pydev.editor.model.ItemPointer;
import org.python.pydev.editor.refactoring.AbstractPyRefactoring;
import org.python.pydev.editor.refactoring.IPyRefactoring;
import org.python.pydev.editor.refactoring.IPyRefactoringRequest;
import org.python.pydev.editor.refactoring.ModuleRenameRefactoringRequest;
import org.python.pydev.editor.refactoring.PyRefactoringRequest;
import org.python.pydev.editor.refactoring.RefactoringRequest;
import org.python.pydev.parser.jython.SimpleNode;
import org.python.pydev.parser.visitors.scope.ASTEntry;
import org.python.pydev.refactoring.core.base.PyDocumentChange;
import org.python.pydev.refactoring.core.base.PyTextFileChange;
import org.python.pydev.shared_core.string.StringUtils;
import org.python.pydev.shared_core.structure.Location;
import org.python.pydev.shared_core.structure.OrderedMap;
import org.python.pydev.shared_core.structure.Tuple;

import com.python.pydev.refactoring.actions.PyFindAllOccurrences;
import com.python.pydev.refactoring.changes.PyCompositeChange;
import com.python.pydev.refactoring.changes.PyRenameResourceChange;
import com.python.pydev.refactoring.wizards.IRefactorRenameProcess;
import com.python.pydev.refactoring.wizards.RefactorProcessFactory;

/**
 * Rename to a local variable...
 * 
 * Straightforward 'way': - find the definition and assert it is not a global - rename all occurences within that scope
 * 
 * 'Blurred things': - if we have something as:
 * 
 * case 1: 
 * 
 * def m1(): 
 *     a = 1 
 *     def m2(): 
 *         a = 3 
 *         print a 
 *     print a
 * 
 * case 2: 
 * 
 * def m1(): 
 *     a = 1 
 *     def m2(): 
 *         print a 
 *         a = 3 
 *         print a 
 *     print a
 * 
 * case 3: 
 * 
 * def m1(): 
 *     a = 1 
 *     def m2(): 
 *         if foo: 
 *             a = 3 
 *         print a 
 *     print a
 * 
 * if we rename it inside of m2, do we have to rename it in scope m1 too? what about renaming it in m1?
 * 
 * The solution that will be implemented will be:
 * 
 *  - if we rename it inside of m2, it will only rename inside of its scope in any case 
 *  (the problem is when the rename request commes from an 'upper' scope).
 *  
 *  - if we rename it inside of m1, it will rename it in m1 and m2 only if it is used inside 
 *  that scope before an assign this means that it will rename in m2 in case 2 and 3, but not in case 1.
 */
public class PyRenameEntryPoint extends RenameProcessor {

    public static final Set<String> WORDS_THAT_CANNOT_BE_RENAMED = new HashSet<String>();
    static {
        String[] wordsThatCannotbeRenamed = { "and", "assert", "break", "class", "continue", "def", "del", "elif",
                "else", "except", "exec", "finally", "for", "from", "global", "if", "import", "in", "is", "lambda",
                "not", "or", "pass", "print", "raise", "return", "try", "while", "with", "yield", "as" };
        for (String string : wordsThatCannotbeRenamed) {
            WORDS_THAT_CANNOT_BE_RENAMED.add(string);
        }

    }

    /**
     * This is the request that triggered this processor
     */
    private final IPyRefactoringRequest fRequest;

    private List<Change> allChanges = new ArrayList<>();

    private static class RefactoringRequestInfo {

        /**
         * A list of processes that were activated for doing the rename
         */
        public List<IRefactorRenameProcess> process = new ArrayList<>();

    }

    private final Map<RefactoringRequest, RefactoringRequestInfo> fRequestToInfo = new OrderedMap<>();

    public PyRenameEntryPoint(RefactoringRequest request) {
        this(new PyRefactoringRequest(request));
    }

    public PyRenameEntryPoint(IPyRefactoringRequest request) {
        this.fRequest = request;
        List<RefactoringRequest> requests = request.getRequests();
        for (RefactoringRequest refactoringRequest : requests) {
            fRequestToInfo.put(refactoringRequest, new RefactoringRequestInfo());
        }
    }

    @Override
    public Object[] getElements() {
        return new Object[] { this.fRequest };
    }

    public static final String IDENTIFIER = "org.python.pydev.pyRename";

    public static final boolean DEBUG = false || PyFindAllOccurrences.DEBUG_FIND_REFERENCES;

    @Override
    public String getIdentifier() {
        return IDENTIFIER;
    }

    @Override
    public String getProcessorName() {
        return "PyDev PyRenameProcessor";
    }

    @Override
    public boolean isApplicable() throws CoreException {
        return true;
    }

    /**
     * In this method we have to check the conditions for doing the refactorings
     * and finding the definition / references that will be affected in the
     * refactoring.
     * 
     * @see org.eclipse.ltk.core.refactoring.participants.RefactoringProcessor#checkInitialConditions(org.eclipse.core.runtime.IProgressMonitor)
     */
    @Override
    public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException,
            OperationCanceledException {
        fRequest.pushMonitor(pm);
        fRequest.getMonitor().beginTask("Checking refactoring pre-conditions...", 100);

        RefactoringStatus status = new RefactoringStatus();
        try {

            Set<Entry<RefactoringRequest, RefactoringRequestInfo>> entrySet = this.fRequestToInfo.entrySet();
            for (Entry<RefactoringRequest, RefactoringRequestInfo> entry : entrySet) {
                RefactoringRequest request = entry.getKey();
                if (!PyStringUtils
                        .isValidIdentifier(request.initialName, request.isModuleRenameRefactoringRequest())) {
                    status.addFatalError("The initial name is not valid:" + request.initialName);
                    return status;
                }

                if (WORDS_THAT_CANNOT_BE_RENAMED.contains(request.initialName)) {
                    status.addFatalError("The token: " + request.initialName + " cannot be renamed.");
                    return status;
                }

                if (request.inputName != null
                        && !PyStringUtils.isValidIdentifier(request.inputName,
                                request.isModuleRenameRefactoringRequest())) {
                    status.addFatalError("The new name is not valid:" + request.inputName);
                    return status;
                }

                ItemPointer[] pointers;
                if (request.isModuleRenameRefactoringRequest()) {
                    IModule module = request.getModule();
                    pointers = new ItemPointer[] { new ItemPointer(request.file, new Location(0, 0),
                            new Location(0, 0),
                            new Definition(1, 1, "", null, null, module, false), null) };
                } else {
                    SimpleNode ast = request.getAST();
                    if (ast == null) {
                        status.addFatalError("AST not generated (syntax error).");
                        return status;
                    }
                    IPyRefactoring pyRefactoring = AbstractPyRefactoring.getPyRefactoring();
                    request.communicateWork("Finding definition");
                    pointers = pyRefactoring.findDefinition(request);
                }

                if (pointers.length == 0) {
                    // no definition found
                    IRefactorRenameProcess p = RefactorProcessFactory.getRenameAnyProcess();
                    entry.getValue().process.add(p);

                } else {
                    for (ItemPointer pointer : pointers) {
                        if (pointer.definition == null) {
                            status.addFatalError("The definition found is not valid. " + pointer);
                        }
                        if (DEBUG) {
                            System.out.println("Found definition:" + pointer.definition);
                        }

                        IRefactorRenameProcess p = RefactorProcessFactory.getProcess(pointer.definition, request);
                        if (p == null) {
                            status.addFatalError("Refactoring Process not defined: the definition found is not valid:"
                                    + pointer.definition);
                            return status;
                        }
                        entry.getValue().process.add(p);
                    }
                }

                if (entry.getValue().process.size() == 0) {
                    status.addFatalError("Refactoring Process not defined: the pre-conditions were not satisfied.");
                    return status;
                }
            }

        } catch (OperationCanceledException e) {
            // OK
        } catch (Exception e) {
            Log.log(e);
            status.addFatalError("An exception occurred. Please see error log for more details.");

        } finally {
            fRequest.popMonitor().done();
        }
        return status;
    }

    @Override
    public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context)
            throws CoreException, OperationCanceledException {
        return checkFinalConditions(pm, context, true);
    }

    /**
     * Find the references and create the change object
     * 
     * @param fillChangeObject
     *            determines if we should fill the change object (we'll not do
     *            it on tests)
     */
    public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context,
            boolean fillChangeObject) throws CoreException, OperationCanceledException {
        allChanges.clear(); //Clear (will be filled now).
        fRequest.pushMonitor(pm);
        RefactoringStatus status = new RefactoringStatus();

        try {
            final Map<IPath, Tuple<TextChange, MultiTextEdit>> fileToChangeInfo = new HashMap<IPath, Tuple<TextChange, MultiTextEdit>>();

            Set<Entry<RefactoringRequest, RefactoringRequestInfo>> entrySet = this.fRequestToInfo.entrySet();
            for (Entry<RefactoringRequest, RefactoringRequestInfo> entry : entrySet) {
                RefactoringRequest request = entry.getKey();

                if (request.isModuleRenameRefactoringRequest()) {
                    boolean searchInit = true;
                    IModule module = request.getTargetNature().getAstManager()
                            .getModule(request.inputName, request.getTargetNature(),
                                    !searchInit); //i.e.: the parameter is dontSearchInit (so, pass in negative form to search)
                    if (module != null) {
                        String partName = module.getName().endsWith(".__init__") ? "package" : "module";
                        status.addFatalError("Unable to perform module rename because a " + partName + " named: "
                                + request.inputName + " already exists.");
                        return status;
                    }
                }
                if (entry.getValue().process == null || entry.getValue().process.size() == 0) {
                    request.getMonitor().beginTask("Finding references", 1);
                    status.addFatalError("Refactoring Process not defined: the refactoring cycle did not complete correctly.");
                    return status;
                }
                request.getMonitor().beginTask("Finding references", entry.getValue().process.size());

                //Finding references and creating change object...
                //now, check the initial and final conditions
                for (IRefactorRenameProcess p : entry.getValue().process) {
                    request.checkCancelled();
                    p.clear(); //Clear from a previous invocation

                    request.pushMonitor(new SubProgressMonitor(request.getMonitor(), 1));
                    try {
                        p.findReferencesToRename(request, status);
                    } finally {
                        request.popMonitor().done();
                    }

                    if (status.hasFatalError() || request.getMonitor().isCanceled()) {
                        return status;
                    }
                }
                if (fillChangeObject) {

                    TextEditCreation textEditCreation = new TextEditCreation(request.initialName, request.inputName,
                            request.getModule().getName(), request.getDoc(), entry.getValue().process, status,
                            request.getIFile()) {
                        @Override
                        protected Tuple<TextChange, MultiTextEdit> getTextFileChange(IFile workspaceFile, IDocument doc) {

                            if (workspaceFile == null) {
                                //used for tests
                                TextChange docChange = PyDocumentChange
                                        .create("Current module: " + moduleName, doc);
                                MultiTextEdit rootEdit = new MultiTextEdit();
                                docChange.setEdit(rootEdit);
                                docChange.setKeepPreviewEdits(true);
                                allChanges.add(docChange);
                                return new Tuple<TextChange, MultiTextEdit>(docChange, rootEdit);
                            }

                            IPath fullPath = workspaceFile.getFullPath();
                            Tuple<TextChange, MultiTextEdit> tuple = fileToChangeInfo.get(fullPath);
                            if (tuple == null) {
                                TextFileChange docChange = new PyTextFileChange("RenameChange: " + inputName,
                                        workspaceFile);

                                MultiTextEdit rootEdit = new MultiTextEdit();
                                docChange.setEdit(rootEdit);
                                docChange.setKeepPreviewEdits(true);
                                allChanges.add(docChange);
                                tuple = new Tuple<TextChange, MultiTextEdit>(docChange, rootEdit);
                                fileToChangeInfo.put(fullPath, tuple);
                            }
                            return tuple;
                        }

                        @Override
                        protected PyRenameResourceChange createResourceChange(IResource resourceToRename,
                                String newName, RefactoringRequest request) {
                            IContainer target = null;
                            if (request instanceof ModuleRenameRefactoringRequest) {
                                target = ((ModuleRenameRefactoringRequest) request).getTarget();
                            }
                            PyRenameResourceChange change = new PyRenameResourceChange(resourceToRename, initialName,
                                    newName,
                                    StringUtils.format("Changing %s to %s",
                                            initialName, inputName), target);
                            allChanges.add(change);
                            return change;
                        }

                    };

                    textEditCreation.fillRefactoringChangeObject(request, context);
                    if (status.hasFatalError() || request.getMonitor().isCanceled()) {
                        return status;
                    }

                }
            }
        } catch (OperationCanceledException e) {
            // OK
        } finally {
            fRequest.popMonitor().done();
        }
        return status;
    }

    /**
     * Change is actually already created in this stage.
     */
    @Override
    public Change createChange(IProgressMonitor pm) throws CoreException, OperationCanceledException {
        PyCompositeChange finalChange;
        List<RefactoringRequest> requests = fRequest.getRequests();
        if (requests.size() == 1) {
            RefactoringRequest request = requests.get(0);
            boolean makeUndo = !(request.isModuleRenameRefactoringRequest());
            finalChange = new PyCompositeChange("RenameChange: '" + request.initialName + "' to '"
                    + request.inputName
                    + "'", makeUndo);

        } else {
            boolean makeUndo = false;
            finalChange = new PyCompositeChange("Move: " + requests.size() + " resources to '"
                    + fRequest.getInputName()
                    + "'", makeUndo);
        }

        Collections.sort(allChanges, new Comparator<Change>() {

            @Override
            public int compare(Change o1, Change o2) {
                if (o1.getClass() != o2.getClass()) {
                    if (o1 instanceof PyRenameResourceChange) {
                        //The rename changes must be the last ones (all the text-related changes must be done already).
                        return 1;
                    }
                    if (o2 instanceof PyRenameResourceChange) {
                        return -1;
                    }
                }
                return o1.getName().compareTo(o2.getName());
            }
        });

        finalChange.addAll(allChanges.toArray(new Change[allChanges.size()]));
        return finalChange;
    }

    static RefactoringParticipant[] EMPTY_REFACTORING_PARTICIPANTS = new RefactoringParticipant[0];

    @Override
    public RefactoringParticipant[] loadParticipants(RefactoringStatus status, SharableParticipants sharedParticipants)
            throws CoreException {
        return EMPTY_REFACTORING_PARTICIPANTS; // no participants are loaded
    }

    /**
     * @return the list of occurrences that are found in the current document.
     *         Does not get the occurrences if they are in other files
     */
    public HashSet<ASTEntry> getOccurrences() {
        HashSet<ASTEntry> occurrences = new HashSet<ASTEntry>();
        Set<Entry<RefactoringRequest, RefactoringRequestInfo>> entrySet = this.fRequestToInfo.entrySet();
        for (Entry<RefactoringRequest, RefactoringRequestInfo> entry : entrySet) {
            if (entry.getValue().process.size() == 0) {
                continue;
            }
            for (IRefactorRenameProcess p : entry.getValue().process) {
                HashSet<ASTEntry> o = p.getOccurrences();
                if (o != null) {
                    occurrences.addAll(o);
                }
            }
        }
        return occurrences;
    }

    /**
     * @return a map that points the references found in other files Note that
     *         this will exclude the references found in this buffer.
     */
    public Map<Tuple<String, File>, HashSet<ASTEntry>> getOccurrencesInOtherFiles() {
        HashMap<Tuple<String, File>, HashSet<ASTEntry>> m = new HashMap<Tuple<String, File>, HashSet<ASTEntry>>();

        Set<Entry<RefactoringRequest, RefactoringRequestInfo>> entrySet = this.fRequestToInfo.entrySet();
        for (Entry<RefactoringRequest, RefactoringRequestInfo> entry0 : entrySet) {

            if (entry0.getValue().process.size() == 0) {
                return null;
            }

            for (IRefactorRenameProcess p : entry0.getValue().process) {
                Map<Tuple<String, File>, HashSet<ASTEntry>> o = p.getOccurrencesInOtherFiles();
                if (o != null) {

                    for (Map.Entry<Tuple<String, File>, HashSet<ASTEntry>> entry : o.entrySet()) {
                        Tuple<String, File> key = entry.getKey();

                        HashSet<ASTEntry> existingOccurrences = m.get(key);
                        if (existingOccurrences == null) {
                            existingOccurrences = new HashSet<ASTEntry>();
                            m.put(key, existingOccurrences);
                        }

                        existingOccurrences.addAll(entry.getValue());
                    }
                }
            }
        }
        return m;
    }

    public List<IRefactorRenameProcess> getAllProcesses() {
        List<IRefactorRenameProcess> allProcesses = new ArrayList<IRefactorRenameProcess>();
        Set<Entry<RefactoringRequest, RefactoringRequestInfo>> entrySet = this.fRequestToInfo.entrySet();
        for (Entry<RefactoringRequest, RefactoringRequestInfo> entry0 : entrySet) {
            allProcesses.addAll(entry0.getValue().process);
        }
        return allProcesses;
    }

}
