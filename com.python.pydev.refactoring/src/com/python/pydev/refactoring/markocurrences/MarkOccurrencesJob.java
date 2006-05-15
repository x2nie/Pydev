/*
 * Created on Apr 29, 2006
 */
package com.python.pydev.refactoring.markocurrences;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ISynchronizable;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.Annotation;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IAnnotationModelExtension;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.python.pydev.core.Tuple3;
import org.python.pydev.core.docutils.PySelection;
import org.python.pydev.core.log.Log;
import org.python.pydev.editor.PyEdit;
import org.python.pydev.editor.actions.refactoring.PyRefactorAction;
import org.python.pydev.editor.refactoring.AbstractPyRefactoring;
import org.python.pydev.editor.refactoring.IPyRefactoring;
import org.python.pydev.editor.refactoring.RefactoringRequest;
import org.python.pydev.parser.visitors.scope.ASTEntry;

import com.python.pydev.refactoring.ui.MarkOccurrencesPreferencesPage;
import com.python.pydev.refactoring.wizards.PyRenameProcessor;


/**
 * This is a 'low-priority' thread. It acts as a singleton. Requests to mark the ocurrences
 * will be forwarded to it, so, it should sleep for a while and then check for a request.
 * 
 * If the request actually happened, it will go on to process it, otherwise it will sleep some more.
 * 
 * @author Fabio
 */
public class MarkOccurrencesJob extends Thread{

    private static final String OCCURRENCE_ANNOTATION_TYPE = "org.eclipse.jdt.ui.occurrences";
	private static final String ANNOTATIONS_CACHE_KEY = "MarkOccurrencesJob Annotations";
	private static final boolean DEBUG = false;
    private static MarkOccurrencesJob singleton;
    
    public synchronized static MarkOccurrencesJob get() {
        if(singleton == null){
            singleton = new MarkOccurrencesJob();
            singleton.start();
        }
        return singleton;
    }
    
    private MarkOccurrencesJob() {
        super("MarkOccurrencesJob");
        setPriority(Thread.MIN_PRIORITY);
    }
    
    private WeakReference<PyEdit> editor;
    private long lastRequestTime = -1;
    private long currRequestTime = -1;
    boolean waitOnNext = true;
    private IProgressMonitor monitor = new NullProgressMonitor();

    @SuppressWarnings("unchecked")
    public void run() {
    	
        while(true){
            //synchronization code
            try {
                synchronized(this){
                    if(waitOnNext){
                        wait();
                    }
                    waitOnNext = true;
                }
                long wokenUpAt = currRequestTime;
                
                try {
					sleep(750);
				} catch (Exception e) {
					//ignore
				}
            
                //there was another request in the meantime (so, let's restart the process)
                if(wokenUpAt != currRequestTime){
                    waitOnNext = false;
                    continue;
                }
            } catch (Throwable e) {
                Log.log(e);
            }
            //end sync code
            
            if(currRequestTime == -1){
                continue;
            }
            if(currRequestTime == lastRequestTime){
                continue;
            }
            lastRequestTime = currRequestTime;
            
            try {
	            final PyEdit pyEdit = editor.get();
	            
	            if(pyEdit == null){
	                continue;
	            }
            
	            IDocumentProvider documentProvider = pyEdit.getDocumentProvider();
	            if(documentProvider == null){
	            	continue;
	            }
	            
	            IAnnotationModel annotationModel= documentProvider.getAnnotationModel(pyEdit.getEditorInput());
	            if(annotationModel == null){
	            	continue;
	            }
	            

	            Tuple3<RefactoringRequest,PyRenameProcessor,Boolean> ret = checkAnnotations(pyEdit, documentProvider);
	            if(ret.o3){
	            	if(!addAnnotations(pyEdit, annotationModel, ret.o1, ret.o2)){
	            		//something went wrong, so, let's remove the occurrences
	            		removeOccurenceAnnotations(annotationModel, pyEdit);
	            	}
	            }else{
	            	removeOccurenceAnnotations(annotationModel, pyEdit);
	            }
	            
            } catch (Throwable e) {
                Log.log(e);
            }
        }        
    }

    /**
     * @return a tuple with the refactoring request, the processor and a boolean indicating if all pre-conditions succedded.
     */
    private Tuple3<RefactoringRequest,PyRenameProcessor,Boolean> checkAnnotations(PyEdit pyEdit, IDocumentProvider documentProvider) throws BadLocationException, OperationCanceledException, CoreException {
        if(!MarkOccurrencesPreferencesPage.useMarkOccurrences()){
        	return new Tuple3<RefactoringRequest,PyRenameProcessor,Boolean>(null,null,false);
        }

        //now, let's see if the editor still has a document (so that we still can add stuff to it)
        IEditorInput editorInput = pyEdit.getEditorInput();
        if(editorInput == null){
        	return new Tuple3<RefactoringRequest,PyRenameProcessor,Boolean>(null,null,false);
        }
        
        if(documentProvider.getDocument(editorInput) == null){
        	return new Tuple3<RefactoringRequest,PyRenameProcessor,Boolean>(null,null,false);
        }
        
        if(pyEdit.getSelectionProvider() == null){
        	return new Tuple3<RefactoringRequest,PyRenameProcessor,Boolean>(null,null,false);
        }
        
        //ok, the editor is still there wit ha document... move on
        PyRefactorAction pyRefactorAction = getRefactorAction();
        pyRefactorAction.setEditor(pyEdit);
        
        final RefactoringRequest req = getRefactoringRequest(pyEdit, pyRefactorAction);
        
        if(req == null){
        	return new Tuple3<RefactoringRequest,PyRenameProcessor,Boolean>(null,null,false);
        }
        
        PyRenameProcessor processor = new PyRenameProcessor(req);
        //to see if a new request was not created in the meantime (in which case this one will be cancelled)
        if (currRequestTime != lastRequestTime) {
        	return new Tuple3<RefactoringRequest,PyRenameProcessor,Boolean>(null,null,false);
        }
        
        monitor.setCanceled(false);
        processor.checkInitialConditions(monitor);
        if (currRequestTime != lastRequestTime) {
        	return new Tuple3<RefactoringRequest,PyRenameProcessor,Boolean>(null,null,false);
        }
        
        processor.checkFinalConditions(monitor, null);
        if (currRequestTime != lastRequestTime) {
        	return new Tuple3<RefactoringRequest,PyRenameProcessor,Boolean>(null,null,false);
        }
        
        //ok, pre-conditions suceeded
		return new Tuple3<RefactoringRequest,PyRenameProcessor,Boolean>(req,processor,true);
	}

	/**
	 * @return true if the annotations were removed and added without any problems and false otherwise
     */
    private boolean addAnnotations(final PyEdit pyEdit, IAnnotationModel annotationModel, final RefactoringRequest req, PyRenameProcessor processor) throws BadLocationException {
        //add the annotations
        synchronized (getLockObject(annotationModel)) {
            List<ASTEntry> ocurrences = processor.getOcurrences();
            if(ocurrences != null){
                IDocument doc = pyEdit.getDocument();
                ArrayList<Annotation> annotations = new ArrayList<Annotation>();
                Map<Annotation, Position> toAddAsMap = new HashMap<Annotation, Position>();                
                
                for (ASTEntry entry : ocurrences) {
                    IRegion lineInformation = doc.getLineInformation(entry.node.beginLine-1);
                    
                    try {
                        Annotation annotation = new Annotation(OCCURRENCE_ANNOTATION_TYPE, false, "occurrence");
                        Position position = new Position(lineInformation.getOffset() + entry.node.beginColumn - 1, req.duringProcessInfo.initialName.length());
                        toAddAsMap.put(annotation, position);
                        annotations.add(annotation);
						
                    } catch (Exception e) {
                        Log.log(e);
                    }
                }
                
                //get the ones to remove
                List<Annotation> toRemove = getCurrentAnnotationsInPyEdit(pyEdit);
                
                //replace them
                IAnnotationModelExtension ext = (IAnnotationModelExtension) annotationModel;
                ext.replaceAnnotations(toRemove.toArray(new Annotation[0]), toAddAsMap);

                //put them in the pyEdit
                pyEdit.cache.put(ANNOTATIONS_CACHE_KEY, annotations);
            }else{
                if(DEBUG){
                    System.out.println("Occurrences == null");
                }
                return false;
            }
        }
        return true;
    }

    /**
     * @return the list of occurrence annotations in the pyedit
     */
	private List<Annotation> getCurrentAnnotationsInPyEdit(final PyEdit pyEdit) {
		List<Annotation> inEdit = (List<Annotation>) pyEdit.cache.get(ANNOTATIONS_CACHE_KEY);
		List<Annotation> toRemove = new ArrayList<Annotation>();
		if(inEdit != null){
		    Iterator<Annotation> annotationIterator = inEdit.iterator();
		    while(annotationIterator.hasNext()){
		        Annotation annotation = annotationIterator.next();
		        if(annotation.getType().equals(OCCURRENCE_ANNOTATION_TYPE)){
		            toRemove.add(annotation);
		        }
		    }
		}
		return toRemove;
	}

    /**
     * @param pyEdit
     * @param pyRefactorAction
     * @return
     * @throws BadLocationException
     */
    private RefactoringRequest getRefactoringRequest(final PyEdit pyEdit, PyRefactorAction pyRefactorAction) throws BadLocationException {
        final RefactoringRequest req = pyRefactorAction.getRefactoringRequest();
        Display.getDefault().syncExec(new Runnable(){
            public void run() {
                try {
					req.ps = new PySelection(pyEdit);
				} catch (NullPointerException e) {
					// this can happen if the selection was still not set up
				}
            }});
        if(req.ps == null){
        	return null;
        }
        
        req.fillInitialNameAndOffset();
        req.duringProcessInfo.name = "foo";
        req.findDefinitionInAdditionalInfo = false;
        req.findReferencesOnlyOnLocalScope = true;
        return req;
    }

    /**
     * @return
     */
    private PyRefactorAction getRefactorAction() {
        PyRefactorAction pyRefactorAction = new PyRefactorAction(){
   
            @Override
            protected IPyRefactoring getPyRefactoring() {
                return AbstractPyRefactoring.getPyRefactoring();
            }
   
            @Override
            protected String perform(IAction action, String name, Operation operation) throws Exception {
                throw new RuntimeException("Perform should not be called in this case.");
            }
   
            @Override
            protected String getInputMessage() {
                return null;
            }
            
        };
        return pyRefactorAction;
    }

    /**
     * @param annotationModel
     */
    @SuppressWarnings("unchecked")
	private void removeOccurenceAnnotations(IAnnotationModel annotationModel, PyEdit pyEdit) {
        //remove the annotations
        synchronized(getLockObject(annotationModel)){
            Iterator<Annotation> annotationIterator = getCurrentAnnotationsInPyEdit(pyEdit).iterator();
            while(annotationIterator.hasNext()){
                annotationModel.removeAnnotation(annotationIterator.next());
            }
            pyEdit.cache.put(ANNOTATIONS_CACHE_KEY, null);
        }
        //end remove the annotations
    }

    
    /**
     * Gotten from JavaEditor#getLockObject
     */
    private Object getLockObject(IAnnotationModel annotationModel) {
        if (annotationModel instanceof ISynchronizable)
            return ((ISynchronizable)annotationModel).getLockObject();
        else
            return annotationModel;
    }

    public synchronized void scheduleRequest(WeakReference<PyEdit> editor2) {
        synchronized (this) {
            currRequestTime = System.currentTimeMillis();
            monitor.setCanceled(true);
            this.editor = editor2;
            notify();
        }
    }


}
