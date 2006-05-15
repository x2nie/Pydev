/*
 * Created on May 1, 2006
 */
package com.python.pydev.refactoring.wizards;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.util.Assert;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.python.pydev.core.Tuple;
import org.python.pydev.editor.codecompletion.revisited.visitors.Definition;
import org.python.pydev.editor.refactoring.RefactoringRequest;
import org.python.pydev.parser.jython.SimpleNode;
import org.python.pydev.parser.jython.ast.Attribute;
import org.python.pydev.parser.jython.ast.ClassDef;
import org.python.pydev.parser.jython.ast.FunctionDef;
import org.python.pydev.parser.visitors.NodeUtils;
import org.python.pydev.parser.visitors.scope.ASTEntry;
import org.python.pydev.parser.visitors.scope.SequencialASTIteratorVisitor;

import com.python.pydev.analysis.scopeanalysis.ScopeAnalysis;

/**
 * This process takes care of renaming instances of some method either:
 * 
 * - on a class
 * - on a global scope
 * - in an inner scope (inside of another method)
 */
public class PyRenameFunctionProcess extends AbstractRenameRefactorProcess{

    public PyRenameFunctionProcess(Definition definition) {
        super(definition);
        Assert.isTrue(this.definition.ast instanceof FunctionDef);
    }

    private List<ASTEntry> getLocalOcurrences(String occurencesFor, SimpleNode simpleNode, RefactoringStatus status) {
        List<ASTEntry> ret = new ArrayList<ASTEntry>();
        
        //get the entry for the function itself
        SequencialASTIteratorVisitor visitor = SequencialASTIteratorVisitor.create(simpleNode);
        Iterator<ASTEntry> it = visitor.getIterator(FunctionDef.class);
        ASTEntry functionDefEntry = null;
        while(it.hasNext()){
            functionDefEntry = it.next();
            
            if(functionDefEntry.node.beginLine == this.definition.ast.beginLine && 
                    functionDefEntry.node.beginColumn == this.definition.ast.beginColumn){
                
                break;
            }
        }
        
        if(functionDefEntry == null){
            status.addFatalError("Unable to find the original definition for the function definition.");
            return ret;
        }
        
        if(functionDefEntry.parent != null){
        	//it has some parent
        	
	        final SimpleNode parentNode = functionDefEntry.parent.node;
	        if(parentNode instanceof ClassDef){
	        	//ok, we're in a class, the first thing is to add the reference to the function just gotten
	        	ret.add(new ASTEntry(functionDefEntry, ((FunctionDef)functionDefEntry.node).name));
	        	
	        	//get the entry for the self.xxx that access that attribute in the class
				SequencialASTIteratorVisitor classVisitor = SequencialASTIteratorVisitor.create(parentNode);
		        it = classVisitor.getIterator(Attribute.class);
		        while(it.hasNext()){
		            ASTEntry entry = it.next();
		            List<SimpleNode> parts = NodeUtils.getAttributeParts((Attribute) entry.node);
		            
		            final String rep0 = NodeUtils.getRepresentationString(parts.get(0));
					final String rep1 = NodeUtils.getRepresentationString(parts.get(1));
					if(rep0 != null && rep1 != null && rep0.equals("self") && rep1.equals(occurencesFor)){
		                ret.add(entry);
		            }
		        }
		        
		        final List<ASTEntry> attributeReferences = ScopeAnalysis.getAttributeReferences(occurencesFor, simpleNode);
				ret.addAll(attributeReferences);
		        
	        }else if(parentNode instanceof FunctionDef){
		    	//get the references inside of the parent (this will include the function itself)
	    		ret.addAll(ScopeAnalysis.getLocalOcurrences(occurencesFor, parentNode));
	    	}
	        
        } else {
        	ret.addAll(ScopeAnalysis.getLocalOcurrences(occurencesFor, simpleNode));
        }
        
        
        //get the references to Names that access that method in the same scope
        return ret;
    }
    

    protected void checkInitialOnLocalScope(RefactoringStatus status, RefactoringRequest request) {
        Tuple<String, IDocument> key = new Tuple<String, IDocument>(request.moduleName, request.doc);
        SimpleNode root = request.getAST();
        List<ASTEntry> ocurrences;
        
        if(!definition.module.getName().equals(request.moduleName)){
        	//it was found in another module
        	ocurrences = ScopeAnalysis.getLocalOcurrences(request.duringProcessInfo.initialName, root, false);
        	
        }else{
            ocurrences = getLocalOcurrences(request.duringProcessInfo.initialName, root, status);
        }
        
        occurrences.put(key, ocurrences);
    }

}
