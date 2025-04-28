package semant;

import java.util.*;
import ast.*;
import util.*;

public class TypeCheckVisitor extends SemanticVisitor {

    ErrorHandler errorHandler;
    Hashtable<String, ClassTreeNode> classMap;
    private SymbolTable varSymbolTable;
    private SymbolTable methodSymbolTable;
    String fileName;
    String className;
    Method currentMethod = null;
    Field currentField = null;
    // String currentMethodName;

    TypeCheckVisitor(ErrorHandler errorHandler,
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
        node.getMemberList().accept(this);
        return null;
    }

    /**
     * visit AST node for fields
     *
     * @param node AST node
     * @return null
     */
    public Object visit(Field node) {
        currentField = node;
        boolean validField = true;
        if (node.getInit() != null) {
            node.getInit().accept(this);
            String initType = node.getInit().getExprType();

            if (initType != null) {
                if (initType.equals("void")) {
                    errorHandler.register(
                            2, fileName, node.getLineNum(),
                            String.format(
                                    "expression type 'void' of field '%s' cannot be void",
                                    node.getName()));
                                    validField = false;
                } else if (!initType.equals(node.getType())
                        && (node.getType().equals("int")
                                || node.getType().equals("boolean"))) {
                    errorHandler.register(
                            2, fileName, node.getLineNum(),
                            String.format(
                                    "expression type '%s' of field '%s' does not match declared type '%s'",
                                    initType, node.getName(), node.getType()));
                                    validField = false;
                } else if (!initType.equals(node.getType())) {
                    errorHandler.register(
                            2, fileName, node.getLineNum(),
                            String.format(
                                    "expression type '%s' of field '%s' does not conform to declared type '%s'",
                                    initType, node.getName(), node.getType()));
                                    validField = false;
                }
            } else {
                if (node.getType().equals("int")
                        || node.getType().equals("boolean")) {
                    errorHandler.register(
                            2, fileName, node.getLineNum(),
                            String.format(
                                    "expression type '%s' of field '%s' does not match declared type '%s'",
                                    initType, node.getName(), node.getType()));
                                    validField = false;
                }
            }
        }
        if (validField) {
            varSymbolTable.add(node.getName(), node.getType());
        }
        return null;
    }

    /**
     * Visit a method node
     *
     * @param node the method node
     * @return result of the visit
     */
    public Object visit(Method node) {
        currentMethod = node;
        // currentMethodName = node.getName();
        node.getFormalList().accept(this);
        node.getStmtList().accept(this);
        return null;
    }

    /**
     * Visit a list node of formals
     *
     * @param node the formal list node
     * @return result of the visit
     */
    public Object visit(FormalList node) {
        varSymbolTable.enterScope();
        methodSymbolTable.enterScope();
        for (Iterator it = node.getIterator(); it.hasNext();) {
            ((Formal) it.next()).accept(this);
        }
        return null;
    }

    /**
     * Visit a formal node
     *
     * @param node the formal node
     * @return result of the visit
     */
    public Object visit(Formal node) {
        boolean validFormal = true;
        switch (node.getName()) {
            case "null":
                errorHandler.register(
                        2, fileName, node.getLineNum(),
                        "formals cannot be named 'null'");
                validFormal = false;
                break;
            case "this":
                errorHandler.register(
                        2, fileName, node.getLineNum(),
                        "formals cannot be named 'this'");
                validFormal = false;
                break;
            case "super":
                errorHandler.register(
                        2, fileName, node.getLineNum(),
                        "formals cannot be named 'super'");
                validFormal = false;
                break;
        }
        if (varSymbolTable.peek(node.getName()) == null && validFormal) {
            boolean undefinedType = !node.getType().equals("int")
                    && !node.getType().equals("boolean")
                    && !node.getType().equals("int[]")
                    && !node.getType().equals("boolean[]")
                    && classMap.get(node.getType()) == null;

            if (undefinedType) {
                errorHandler.register(
                        2, fileName, node.getLineNum(),
                        String.format(
                                "type '%s' of formal '%s' is undefined",
                                node.getType(), node.getName()));
                varSymbolTable.add(node.getName(), "Object");
            } else {
                varSymbolTable.add(node.getName(), node.getType());
            }
        }
        return null;
    }

    /**
     * Visit a list node of statements
     *
     * @param node the statement list node
     * @return result of the visit
     */
    public Object visit(StmtList node) {
        String returnType = "";
        for (Iterator it = node.getIterator(); it.hasNext();) {
            returnType = (String) ((Stmt) it.next()).accept(this);
        }
        varSymbolTable.exitScope();
        methodSymbolTable.exitScope();
        String currentMethodName = currentMethod.getName();
        // if (methodSymbolTable.peek(currentMethodName) != null) {
            // methodSymbolTable.exitScope();
            currentMethodName = currentMethod.getName();
            // Method currentMethod = (Method) methodSymbolTable.peek(currentMethodName);
            // System.out.println(returnType + " " + currentMethod.getReturnType());
            // if (!typesCompatible(returnType,
            //         currentMethod.getReturnType())) {
            //     errorHandler.register(
            //             2, fileName, node.getLineNum(),
            //             String.format(
            //                     "return type '%s' is not compatible with declared return type '%s'"
            //                             + " in method '%s'",
            //                     returnType, currentMethod.getReturnType(),
            //                     currentMethodName));
            // }
        // }
        return null;
    }

    /**
     * Visit a declaration statement node
     *
     * @param node the declaration statement node
     * @return result of the visit
     */
    public Object visit(DeclStmt node) {
        var returnedType = node.getInit().accept(this);
        // System.out.println(returnedType);
        boolean validVar = true;
        switch (node.getName()) {
            case "null":
                errorHandler.register(
                        2, fileName, node.getLineNum(),
                        "variables cannot be named 'null'");
                validVar = false;
                break;
            case "this":
                errorHandler.register(
                        2, fileName, node.getLineNum(),
                        "variables cannot be named 'this'");
                validVar = false;
                break;
            case "super":
                errorHandler.register(
                        2, fileName, node.getLineNum(),
                        "variables cannot be named 'super'");
                validVar = false;
                break;
        }
        // System.out.println(className + " " + varSymbolTable);
        if (varSymbolTable.peek(node.getName()) != null) {
            errorHandler.register(
                    2, fileName, node.getLineNum(),
                    String.format(
                            "variable '%s' is already defined in method '%s'",
                            node.getName(), currentMethod.getName()));
            validVar = false;
        }
        boolean undefined = !node.getType().equals("int")
                && !node.getType().equals("boolean")
                && !node.getType().equals("int[]")
                && !node.getType().equals("boolean[]")
                && classMap.get(node.getType()) == null;
        if (undefined) {
            errorHandler.register(
                    2, fileName, node.getLineNum(),
                    String.format(
                            "type '%s' of variable '%s' is undefined",
                            node.getType(), node.getName()));
            validVar = false;
        }
        // if (!node.getType().equals(node.getInit().getExprType())) {
        //     errorHandler.register(
        //             2, fileName, node.getLineNum(),
        //             String.format(
        //                     "expression type '%s' of declaration '%s' does not match"
        //                             + " declared type '%s'",
        //                     node.getInit().getExprType(), node.getName(), node.getType()));
        //     validVar = false;
        // }
        // if (validVar) {
        //     System.out.println(returnedType);
        // }
        // System.out.println(returnedType);
        // System.out.println("This is a decl");
        if (validVar) {
            varSymbolTable.add(node.getName(),
                    node.getType());
        }
        return null;
    }

    /**
     * Visit an expression statement node
     */
    public Object visit(ExprStmt node) {
        
        node.getExpr().accept(this);
        if (!(node.getExpr() instanceof AssignExpr
                || node.getExpr() instanceof ArrayAssignExpr
                || node.getExpr() instanceof NewExpr
                || node.getExpr() instanceof DispatchExpr
                || node.getExpr() instanceof UnaryIncrExpr
                || node.getExpr() instanceof UnaryDecrExpr)) {
            errorHandler.register(
                    2, fileName, node.getLineNum(), "not a statement");
        }
        return null;
    }

    /**
     * Visit an if statement node
     * 
     * @param node the if statement node
     * @return result of the visit
     */
    public Object visit(IfStmt node) {
        node.getPredExpr().accept(this);
        if (node.getPredExpr().getExprType().equals("boolean")) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("predicate in if-statement does not have type boolean"));
        }
        node.getThenStmt().accept(this);
        node.getElseStmt().accept(this);
        return null;
    }

    /**
     * Visit a while statement node
     * 
     * @param node the while statement node
     * @return result of the visit
     */
    public Object visit(WhileStmt node) {
        node.getPredExpr().accept(this);
        if (node.getPredExpr().getExprType().equals("boolean")) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("predicate in while-statement does not have type boolean"));
        }
        node.getBodyStmt().accept(this);
        return null;
    }

    /**
     * Visit a for statement node
     * 
     * @param node the for statement node
     * @return result of the visit
     */
    public Object visit(ForStmt node) {
        if (node.getInitExpr() != null)
            node.getInitExpr().accept(this);

        if (node.getPredExpr() != null) {
            node.getPredExpr().accept(this);
            if (node.getPredExpr().getExprType().equals("boolean")) {
                errorHandler.register(2, fileName, node.getLineNum(),
                        String.format("predicate in for-statement does not have type boolean"));
            }
        }
        if (node.getUpdateExpr() != null)
            node.getUpdateExpr().accept(this);
        node.getBodyStmt().accept(this);
        return null;
    }

    /**
     * Visit a break statement node
     * 
     * @param node the break statement node
     * @return result of the visit
     */
    public Object visit(BreakStmt node) {
        return null;
    }

    /**
     * Visit a block statement node
     * 
     * @param node the block statement node
     * @return result of the visit
     */
    public Object visit(BlockStmt node) {
        varSymbolTable.enterScope();
        methodSymbolTable.enterScope();
        node.getStmtList().accept(this);
        varSymbolTable.exitScope();
        methodSymbolTable.exitScope();
        return null;
    }

    /**
     * Visit a return stmt node
     * 
     * @param node the return stmt node
     * @return result of the visit
     */
    public Object visit(ReturnStmt node) {

        if (node.getExpr() != null) {
            var returned = node.getExpr().accept(this);
            // System.out.println(returned);
            // System.out.println(node.getExpr());
            String returnedType = node.getExpr().getExprType();
            
            // System.out.println(className + " " + returnedType);
            if (returnedType != null) {
                varSymbolTable.peek(returnedType);
                node.getExpr().setExprType("Object");
                return "Object";
            }

            // Method currentMethod = (Method)
            // methodSymbolTable.lookup(currentMethod.getName());
            String declaredType = currentMethod.getReturnType();
                System.out.println("current mtd" + currentMethod.getName() + " Decl:"+ declaredType + " return:"+ returnedType);
            if (!typesCompatible(returnedType, declaredType)) {
                errorHandler.register(2, fileName, node.getLineNum(),
                        String.format(
                                "return type '%s' is not compatible with declared return type "
                                        + "'%s' in method '%s'",
                                returnedType, declaredType, currentMethod.getName()));
            }

            return returnedType;
        } else {
            return "void";
        }
    }

    /**
     * Visit a dispatch expression node
     * 
     * @param node the dispatch expression node
     * @return result of the visit
     */
    public Object visit(DispatchExpr node) {
        node.getRefExpr().accept(this);
        node.getMethodName();
        var refExpr = ((VarExpr) node.getRefExpr()).getName();
        String refExprType = null;
        if (refExpr.equals("this")) {
            var method = (Method) methodSymbolTable.lookup(currentMethod.getName());
            refExprType = method.getReturnType();
            // if (refExprType != null) {
            if (isPrimitive(refExprType) || isVoid(refExprType)) {
                errorHandler.register(2, fileName, node.getLineNum(),
                        String.format("expression type '%s' of field '%s' cannot be %s", refExprType,
                                currentField.getName(), refExprType));
                // node.setExprType("Object");
                return "Object";
            }
            // }
        }

        // String dispatchClassName;
        // if (refExprType.endsWith("[]")) {
        // dispatchClassName = "Object";
        // } else {
        // dispatchClassName = refExprType;
        // }
        // var dispatchClassSymbol = classMap.get(dispatchClassName);

        // if (dispatchClassSymbol == null) {
        // errorHandler.register(2, fileName, node.getLineNum(),
        // String.format("Dispatch on undefined class '%s'", dispatchClassName));
        // node.setExprType("Object");
        // return "Object";
        // }

        // String methodName = node.getMethodName();
        // var dispatchedMethod = (Method) methodSymbolTable.lookup(dispatchClassName);
        // if (dispatchedMethod == null) {
        // errorHandler.register(2, fileName, node.getLineNum(),
        // String.format("Method '%s' not found in class '%s'",
        // methodName, dispatchClassName));
        // node.setExprType("Object");
        // return "Object";
        // }

        // node.getActualList().accept(this);
        // var actualArgs = node.getActualList();

        // var formalParams = dispatchedMethod.getFormalList();

        // if (actualArgs.getSize() != formalParams.getSize()) {
        // errorHandler.register(2, fileName, node.getLineNum(),
        // String.format("Method '%s' in class '%s' requires %d arguments, "
        // + "but %d provided",
        // methodName, dispatchClassName, formalParams.getSize(),
        // actualArgs.getSize()));
        // node.setExprType("Object");
        // return "Object";
        // }

        // boolean argsTypesMatch = true;
        // var actualIter = actualArgs.getIterator();
        // var formalIter = formalParams.getIterator();
        // int counter = 0;
        // while (actualIter.hasNext()) {
        // counter++;
        // String actualType = ((Expr) actualIter.next()).getExprType();
        // String formalType = ((Formal) formalIter.next()).getType();

        // if (actualType.equals("void")) {
        // errorHandler.register(2, fileName, node.getLineNum(),
        // String.format("Argument %d for method '%s' in class '%s' has "
        // + "invalid type 'void'",
        // counter, methodName, dispatchClassName));
        // argsTypesMatch = false;
        // } else if (!actualType.equals(formalType)) {
        // errorHandler.register(2, fileName, node.getLineNum(),
        // String.format("Argument %d for method '%s' in class '%s' has "
        // + "type '%s', but expected '%s'",
        // counter, methodName, dispatchClassName,
        // actualType, formalType));
        // argsTypesMatch = false;
        // }
        // }

        // if (!argsTypesMatch) {
        // node.setExprType("Object");
        // return "Object";
        // }

        // String returnType = dispatchedMethod.getReturnType();
        // node.setExprType(returnType);

        // return returnType;
        // }
        // return "";
        return refExprType;
    }

    /**
     * Visit a new expression node
     * 
     * @param node the new expression node
     * @return result of the visit
     */
    public Object visit(NewExpr node) {
        return null;
    }

    /**
     * Visit a new array expression node
     * 
     * @param node the new array expression node
     * @return result of the visit
     */
    public Object visit(NewArrayExpr node) {
        node.getSize().accept(this);
        return null;
    }

    /**
     * Visit an instanceof expression node
     * 
     * @param node the instanceof expression node
     * @return result of the visit
     */
    public Object visit(InstanceofExpr node) {
        node.getExpr().accept(this);
        return null;
    }

    /**
     * Visit a cast expression node
     * 
     * @param node the cast expression node
     * @return result of the visit
     */
    public Object visit(CastExpr node) {
        node.getExpr().accept(this);
        return null;
    }

    /**
     * Visit an assignment expression node
     * 
     * @param node the assignment expression node
     * @return result of the visit
     */
    public Object visit(AssignExpr node) {
        var rhsType = node.getExpr().accept(this).toString();
        var refName = node.getRefName();
        var name = node.getName();
        var nameType = node.getExprType();
        if (refName != null) { // im guessing null if a. is absent
            if (refName.equals("this")) {
                if (varSymbolTable.peek(name) == null) { // check that b is defined
                    
                }
            } else { // for the super
                if (varSymbolTable.lookup(name) == null && varSymbolTable.getCurrScopeLevel() != varSymbolTable.getScopeLevel(name)) {

                }
            }
        } 
        // if (isVoid(rhsType)) { // should record an

        // }
        // Thread.dumpStack();
        // System.out.println("rhs: "+rhsType + " lhs:" + nameType);
        if (typesCompatible(rhsType, nameType)) {
            node.setExprType(rhsType);
            return rhsType;
        }
        return null;
    }

    /**
     * Visit an array assignment expression node
     * 
     * @param node the array assignment expression node
     * @return result of the visit
     */
    public Object visit(ArrayAssignExpr node) {
        node.getIndex().accept(this);
        node.getExpr().accept(this);
        return null;
    }

    /**
     * Visit a binary comparison equals expression node
     * 
     * @param node the binary comparison equals expression node
     * @return result of the visit
     */
    public Object visit(BinaryCompEqExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        return null;
    }

    /**
     * Visit a binary comparison not equals expression node
     * 
     * @param node the binary comparison not equals expression node
     * @return result of the visit
     */
    public Object visit(BinaryCompNeExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        return null;
    }

    /**
     * Visit a binary comparison less than expression node
     * 
     * @param node the binary comparison less than expression node
     * @return result of the visit
     */
    public Object visit(BinaryCompLtExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        return null;
    }

    /**
     * Visit a binary comparison less than or equal to expression node
     * 
     * @param node the binary comparison less than or equal to expression node
     * @return result of the visit
     */
    public Object visit(BinaryCompLeqExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        return null;
    }

    /**
     * Visit a binary comparison greater than expression node
     * 
     * @param node the binary comparison greater than expression node
     * @return result of the visit
     */
    public Object visit(BinaryCompGtExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        return null;
    }

    /**
     * Visit a binary comparison greater than or equal to expression node
     * 
     * @param node the binary comparison greater to or equal to expression node
     * @return result of the visit
     */
    public Object visit(BinaryCompGeqExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        return null;
    }

    /**
     * Visit a binary arithmetic plus expression node
     * 
     * @param node the binary arithmetic plus expression node
     * @return result of the visit
     */
    public Object visit(BinaryArithPlusExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        return null;
    }

    /**
     * Visit a binary arithmetic minus expression node
     * 
     * @param node the binary arithmetic minus expression node
     * @return result of the visit
     */
    public Object visit(BinaryArithMinusExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        return null;
    }

    /**
     * Visit a binary arithmetic times expression node
     * 
     * @param node the binary arithmetic times expression node
     * @return result of the visit
     */
    public Object visit(BinaryArithTimesExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        return null;
    }

    /**
     * Visit a binary arithmetic divide expression node
     * 
     * @param node the binary arithmetic divide expression node
     * @return result of the visit
     */
    public Object visit(BinaryArithDivideExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        return null;
    }

    /**
     * Visit a binary arithmetic modulus expression node
     * 
     * @param node the binary arithmetic modulus expression node
     * @return result of the visit
     */
    public Object visit(BinaryArithModulusExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        return null;
    }

    /**
     * Visit a binary logical AND expression node
     * 
     * @param node the binary logical AND expression node
     * @return result of the visit
     */
    public Object visit(BinaryLogicAndExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        return null;
    }

    /**
     * Visit a binary logical OR expression node
     * 
     * @param node the binary logical OR expression node
     * @return result of the visit
     */
    public Object visit(BinaryLogicOrExpr node) {
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        return null;
    }

    /**
     * Visit a unary negation expression node
     * 
     * @param node the unary negation expression node
     * @return result of the visit
     */
    public Object visit(UnaryNegExpr node) {
        Object value = node.getExpr().accept(this);
        String exprType = value.toString();
        if (exprType.equals(node.getOperandType())) {
            node.setExprType(exprType);
            return exprType;
        } else {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format(
                            "the expression type '%s' in the unary operation ('%s') is "
                                    + "incorrect; should have been: %s",
                            exprType, node.getOpName(), node.getOpType()));
            return null;
        }
    }

    /**
     * Visit a unary NOT expression node
     * 
     * @param node the unary NOT expression node
     * @return result of the visit
     */
    public Object visit(UnaryNotExpr node) {
        Object value = node.getExpr().accept(this);
        String exprType = value.toString();
        if (exprType.equals(node.getOperandType())) {
            node.setExprType(exprType);
            return exprType;
        } else {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format(
                            "the expression type '%s' in the unary operation ('%s') is "
                                    + "incorrect; should have been: %s",
                            exprType, node.getOpName(), node.getOpType()));
            return null;
        }
    }

    /**
     * Visit a unary increment expression node
     * 
     * @param node the unary increment expression node
     * @return result of the visit
     */
    public Object visit(UnaryIncrExpr node) {
        node.getExpr().accept(this);
        return null;
    }

    /**
     * Visit a unary decrement expression node
     * 
     * @param node the unary decrement expression node
     * @return result of the visit
     */
    public Object visit(UnaryDecrExpr node) {
        node.getExpr().accept(this);
        return null;
    }

    /**
     * Visit a variable expression node (b or a.b)
     *
     * @param node the variable expression node
     * @return result of the visit (the type of the variable expression)
     */
    public Object visit(VarExpr node) {
        
        var varName = node.getName();
        var ref = node.getRef();
        // System.out.println(varSymbolTable.toString());
        // System.out.println(ref);
        if (ref != null) { // a. is present
            String exprType = ref.getExprType();
            // System.out.println(exprType);
            if (exprType.equals("this")) {
                Object type = varSymbolTable.peek(varName);
                // System.out.println(type);
                if (type != null) {
                    return type;
                } else {
                    return "Object";
                }
            } else { // a. is super
                return null;
            }
        } else { // a. is not present
            if (varName.equals("this")) {
                return null;
            } else if (varName.equals("super")) {
                return null;
            } else if (varName.equals("null")) {
                return null;
            } else {
                // System.out.println("Name: " +node.getName()+" node exprType: "+node.getExprType());
                var type = varSymbolTable.peek(varName).toString();
                
                // System.out.println(node.getName());
                // System.out.println(type);
                // System.out.println("This should be boolean at last: "+type);
                if (typeExists(type)) {
                    System.out.println("Im here " + type);
                    node.setExprType(type);
                    return type;
                } else {
                    node.setExprType("Object");
                    return "Object";
                }

            }
        }
    }

    /**
     * Visit an array expression node (a[b])
     *
     * @param node the array expression node
     * @return result of the visit (the base type of the array element)
     */
    public Object visit(ArrayExpr node) {
        node.getRef().accept(this);
        String refType = node.getRef().getExprType();

        node.getIndex().accept(this);
        String indexType = node.getIndex().getExprType();

        String exprType;

        if (!refType.endsWith("[]")) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("Cannot index non-array type '%s'", refType));
            exprType = "Object";
        } else if (!indexType.equals("int")) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("Array index must be of type 'int', but found '%s'",
                            indexType));
            exprType = "Object";
        } else {
            exprType = refType.substring(0, refType.length() - 2);
        }

        node.setExprType(exprType);
        return exprType;
    }

    /**
     * Visit an int constant expression node
     * 
     * @param node the int constant expression node
     * @return result of the visit
     */
    public Object visit(ConstIntExpr node) {
        node.setExprType("int");
        return "int";
    }

    /**
     * Visit a boolean constant expression node
     * 
     * @param node the boolean constant expression node
     * @return result of the visit
     */
    public Object visit(ConstBooleanExpr node) {
        // System.out.println(className +" "+ currentMethod.getName() + ": " + node.getConstant());
        node.setExprType("boolean");
        // System.out.println(className+" "+node.getConstant()+" "+node.getExprType());
        return "boolean";
    }

    /**
     * Visit a string constant expression node
     * 
     * @param node the string constant expression node
     * @return result of the visit
     */
    public Object visit(ConstStringExpr node) {
        node.setExprType("String");
        return "String";
    }

    /**
     * Placeholder: Checks if actualType conforms to expectedType according to
     * language rules.
     * This method needs to handle:
     * - Equality (int conforms to int)
     * - Subclassing (SubClass conforms to SuperClass)
     * info
     */
    private boolean typesCompatible(String actualType, String expectedType) {
        // System.out.println("Actual:" + actualType + " expect: " + expectedType);
        if (actualType.equals(expectedType)) {
            return true;
        }
        return false;
    }

    private boolean isPrimitive(String type) {
        // System.out.println(type);
        if (type.equals("int") || type.equals("boolean")) {
            return true;
        }
        return false;
    }

    private boolean isVoid(String type) {
        if (type.equals("void")) {
            return true;
        }
        return false;
    }

    private boolean typeExists(String type) {
        if (type != null) {
            if (isPrimitive(type)) {
                return true;
            }
    
            if (type.equals("int[]") || type.equals("boolean[]")) {
                return true;
            }
    
            if (classMap.get(type) != null) {
                return true;
            }
        }
        // if (isPrimitive(type)) {
        //     return true;
        // }

        // if (type.equals("int[]") || type.equals("boolean[]")) {
        //     return true;
        // }

        // if (classMap.get(type) != null) {
        //     return true;
        // }

        return false;
    }

}

    