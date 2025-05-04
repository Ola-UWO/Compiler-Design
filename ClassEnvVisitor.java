package semant;

import ast.*;
import util.*;
import java.util.*;

/** Visitor class for building the symbol tables */
public class ClassEnvVisitor extends SemanticVisitor {

    /**
     * Maximum number of inherited and non-inherited fields that can
     * be defined for any one class
     */
    private final int MAX_NUM_FIELDS = 1500;

    private Hashtable<String, ClassTreeNode> classMap;

    private SymbolTable varSymbolTable;

    private SymbolTable methodSymbolTable;

    /** Object for error handling */
    private ErrorHandler errorHandler;

    private String fileName;

    private String className;

    public ClassEnvVisitor(ErrorHandler errorHandler,
            Hashtable<String, ClassTreeNode> classMap) {
        this.errorHandler = errorHandler;
        this.classMap = classMap;

    }

    /**
     * visit AST node
     * 
     * @param node AST node
     * @return null (returns value to satisfy compiler)
     */
    public Object visit(Class_ node) {
        ClassTreeNode ctn = classMap.get(node.getName());
        varSymbolTable = ctn.getVarSymbolTable();
        methodSymbolTable = ctn.getMethodSymbolTable();
        fileName = node.getFilename();
        className = node.getName();
        varSymbolTable.enterScope();
        methodSymbolTable.enterScope();
        node.getMemberList().accept(this);
        return null;
    }

    /**
     * Visit a list node of members
     * 
     * @param node the member list node
     * @return result of the visit
     */
    public Object visit(MemberList node) {
        for (Iterator it = node.getIterator(); it.hasNext();)
            ((Member) it.next()).accept(this);
        return null;
    }

    /**
     * visit AST node
     * 
     * @param node AST node
     * @return null (returns value to satisfy compiler)
     */
    public Object visit(Field node) {
        boolean validField = true;
        switch (node.getName()) {
            case "null":
                errorHandler.register(2, fileName, node.getLineNum(),
                        String.format("fields cannot be named 'null'"));
                validField = false;
                break;

            case "this":
                errorHandler.register(2, fileName, node.getLineNum(),
                        String.format("fields cannot be named 'this'"));
                validField = false;
                break;

            case "super":
                errorHandler.register(2, fileName, node.getLineNum(),
                        String.format("fields cannot be named 'super'"));
                validField = false;
                break;
        }

        if (varSymbolTable.peek(node.getName()) != null) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("field '%s' is already defined in class '%s'",
                            node.getName(), className));
            validField = false;
        }

        if (!(node.getType().equals("int") || node.getType().equals("boolean") ||
                node.getType().equals("int[]") || node.getType().equals("boolean[]") ||
                classMap.get(node.getType()) != null)) {

            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("type '%s' of field '%s' is undefined",
                            node.getType(), node.getName()));
            validField = false;
        }
        if (validField) {
            varSymbolTable.add(node.getName(), node.getType());
            varSymbolTable.add("this."+node.getName(), node.getType());
            // System.out.println(node.getLineNum()+" "+varSymbolTable);
        }
        return null;
    }

    /**
     * visit AST node
     * 
     * @param node AST node
     * @return null (returns value to satisfy compiler)
     */
    public Object visit(Method node) {
        boolean validMethod = true;

        if (!(node.getReturnType().equals("void") || node.getReturnType().equals("int")
                || node.getReturnType().equals("boolean") ||
                node.getReturnType().equals("int[]") ||
                node.getReturnType().equals("boolean[]") ||
                classMap.get(node.getReturnType()) != null)) {

            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("return type '%s' of method '%s' is undefined",
                            node.getReturnType(), node.getName()));
            validMethod = false;
        }
        switch (node.getName()) {
            case "null":
                errorHandler.register(2, fileName, node.getLineNum(),
                        String.format("methods cannot be named 'null'"));
                validMethod = false;
                break;

            case "this":
                errorHandler.register(2, fileName, node.getLineNum(),
                        String.format("methods cannot be named 'this'"));
                validMethod = false;
                break;

            case "super":
                errorHandler.register(2, fileName, node.getLineNum(),
                        String.format("methods cannot be named 'super'"));
                validMethod = false;
                break;
        }

        if (methodSymbolTable.peek(node.getName()) != null) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("method '%s' is already defined in class '%s'",
                            node.getName(), className));
            validMethod = false;
        }
        var stmtIter = node.getStmtList().getIterator();
        while (stmtIter.hasNext()) {
            var stmt = stmtIter.next();
            if (!classMap.get(className).isBuiltIn()) {
                if (stmt instanceof ReturnStmt) {
                    var returnStmt = (ReturnStmt) stmt;
                    if (returnStmt.getExpr() == null && !node.getReturnType().equals("void")
                            && !node.getName().equals("main") && !validMethod) {
                        errorHandler.register(2, fileName, node.getLineNum(),
                                String.format(
                                        "declared return type of method '%s' is '%s' but method body"
                                                + " is not returning any expression",
                                        node.getName(), node.getReturnType()));
                    }
                }
            }
        }
        // System.out.println(validMethod + " " + node.getName() + " " + node.getReturnType());
        // System.out.println("Before getCurrScopeSize: " + className + " " +
        // methodSymbolTable.toString());
        if (methodSymbolTable.getCurrScopeSize() > 0) {
            // System.out.println("Before getCurrScopeLevel: " + className + " " + methodSymbolTable.toString());
            if (methodSymbolTable.lookup(node.getName()) != null &&
                    methodSymbolTable.getCurrScopeLevel() != methodSymbolTable.getScopeLevel(node.getName())) {
                var inheritedNode = (Method) methodSymbolTable.lookup(node.getName());
                if (!(inheritedNode).getReturnType().equals(node.getReturnType())) {
                    errorHandler.register(2, fileName, node.getLineNum(),
                            String.format(
                                    "overriding method '%s' has return type '%s', which differs"
                                            + " from the inherited method's return type '%s'",
                                    node.getName(), node.getReturnType(),
                                    inheritedNode.getReturnType()));
                    validMethod = false;

                } else if (node.getFormalList().getSize() != inheritedNode.getFormalList().getSize()) {
                    errorHandler.register(2, fileName, node.getLineNum(),
                            String.format(
                                    "overriding method '%s' has %d formals, which differs from"
                                            + " the inherited method (%d)",
                                    node.getName(), node.getFormalList().getSize(),
                                    inheritedNode.getFormalList().getSize()));
                    validMethod = false;
                } else {
                    var iter = node.getFormalList().getIterator();
                    var iterr = inheritedNode.getFormalList().getIterator();
                    int counter = 1;
                    while (iter.hasNext()) {
                        var curr = (Formal) iter.next();
                        var superCurr = (Formal) iterr.next();
                        if (!curr.getType().equals(superCurr.getType())) {
                            errorHandler.register(2, fileName, node.getLineNum(),
                                    String.format(
                                            "overriding method '%s' has formal type '%s' for"
                                                    + " formal %d, which differs from the inherited"
                                                    + " method's formal type '%s'",
                                            node.getName(), curr.getType(), counter,
                                            superCurr.getType()));
                            counter++;
                            validMethod = false;
                        }
                    }
                }
            }
        }

        if (validMethod) {
            methodSymbolTable.add(node.getName(), node);
        }

        return null;
    }
}
