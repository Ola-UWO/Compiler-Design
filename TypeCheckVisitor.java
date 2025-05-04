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
    boolean withinLoop = false;

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
        // System.out.println(node.getName());
        currentField = node;
        boolean validField = true;
        if (node.getInit() != null) {
            var initType = node.getInit().accept(this);
            // String initType = node.getInit().getExprType();

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
                // else if (node.getType().equals("void")) {

                // }
            }
            if (validField) {
                varSymbolTable.add(node.getName(), node.getType());
            }
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
        // System.out.println(node.getName());
        currentMethod = node;
        // currentMethodName = node.getName();
        node.getFormalList().accept(this);
        node.getStmtList().accept(this);
        varSymbolTable.exitScope();
        methodSymbolTable.exitScope();
        return null;
    }

    /**
     * Visit a list node of formals
     *
     * @param node the formal list node
     * @return result of the visit
     */
    public Object visit(FormalList node) {
        // System.out.println(node);
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
        // System.out.println(node);
        // boolean validFormal = true;
        switch (node.getName()) {
            case "null":
                errorHandler.register(
                        2, fileName, node.getLineNum(),
                        "formals cannot be named 'null'");
                // validFormal = false;
                break;
            case "this":
                errorHandler.register(
                        2, fileName, node.getLineNum(),
                        "formals cannot be named 'this'");
                // validFormal = false;
                break;
            case "super":
                errorHandler.register(
                        2, fileName, node.getLineNum(),
                        "formals cannot be named 'super'");
                // validFormal = false;
                break;
        }
        if (varSymbolTable.peek(node.getName()) == null) {
            // boolean undefinedType = !node.getType().equals("int")
            // && !node.getType().equals("boolean")
            // && !node.getType().equals("int[]")
            // && !node.getType().equals("boolean[]")
            // && classMap.get(node.getType()) == null;

            if (!typeExists(node.getType())) {
                errorHandler.register(
                        2, fileName, node.getLineNum(),
                        String.format(
                                "type '%s' of formal '%s' is undefined",
                                node.getType(), node.getName()));
                varSymbolTable.add(node.getName(), "Object");
            } else {
                varSymbolTable.add(node.getName(), node.getType());
            }
        } else {
            errorHandler.register(
                    2, fileName, node.getLineNum(),
                    String.format(
                            "formal '%s' is multiply defined",
                            node.getName()));
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
        // System.out.println(node);
        String returnType = "";
        for (Iterator it = node.getIterator(); it.hasNext();) {
            returnType = (String) ((Stmt) it.next()).accept(this);
        }
        // System.out.println(node.getLineNum()+" "+varSymbolTable);
        // varSymbolTable.exitScope();
        // methodSymbolTable.exitScope();
        // String currentMethodName = currentMethod.getName();
        // System.out.println(node.getLineNum()+" "+varSymbolTable);
        // System.out.println(currentMethodName + " " + node.getLineNum() + " "
        // +methodSymbolTable);
        // if (returnType != null && returnType.equals("void") &&
        // methodSymbolTable.peek(currentMethodName) != null) {
        // // methodSymbolTable.exitScope();
        // currentMethodName = currentMethod.getName();
        // // Method currentMethod = (Method) methodSymbolTable.peek(currentMethodName);
        // // System.out.println(returnType + " " + currentMethod.getReturnType() + " "
        // + node.getLineNum());
        // if (!currentMethodName.equals("main") && !typesCompatible(returnType,
        // currentMethod.getReturnType())) {
        // errorHandler.register(
        // 2, fileName, node.getLineNum(),
        // String.format(
        // "return type '%s' is not compatible with declared return type '%s'"
        // + " in method '%s'",
        // returnType, currentMethod.getReturnType(),
        // currentMethodName));
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
        // System.out.println(node);
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
        if (methodSymbolTable.lookup(node.getName()) != null || varSymbolTable.peek(node.getName()) != null) {
            errorHandler.register(
                    2, fileName, node.getLineNum(),
                    String.format(
                            "variable '%s' is already defined in method %s",
                            node.getName(), currentMethod.getName()));
            validVar = false;
        }
        var type = node.getType();
        if (!typeExists(type)) {
            errorHandler.register(
                    2, fileName, node.getLineNum(),
                    String.format(
                            "type '%s' of declaration '%s' is undefined",
                            node.getType(), node.getName()));
            type = "Object";
            // validVar = false;
        }
        // System.out.println(className+" "+type+" "+returnedType+ "
        // "+node.getLineNum()+" "+(!isPrimitive(type) &&
        // !typesCompatible(returnedType.toString(), type)));
        if (!isPrimitive(type) && !isPrimitive(returnedType.toString())
                && !typesCompatible(returnedType.toString(), type)) {
            errorHandler.register(
                    2, fileName, node.getLineNum(),
                    String.format(
                            "expression type '%s' of declaration '%s' does not conform"
                                    + " to declared type '%s'",
                            returnedType, node.getName(), type));
            // validVar = false;
        } else {
            if (!type.equals(returnedType)) {
                errorHandler.register(
                        2, fileName, node.getLineNum(),
                        String.format(
                                "expression type '%s' of declaration '%s' does not match"
                                        + " declared type '%s'",
                                returnedType, node.getName(), type));
                // validVar = false;
            }

        }
        // if (validVar) {
        // System.out.println(returnedType);
        // }
        // System.out.println("This is a decl");
        if (validVar) {
            methodSymbolTable.add(node.getName(),
                    type);
        }
        return null;

    }

    /**
     * Visit an expression statement node
     */
    public Object visit(ExprStmt node) {
        // System.out.println("In exprStmt:" + node.getLineNum() +
        // node.getExpr().getExprType());
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
        // varSymbolTable.enterScope();
        var type = node.getPredExpr().accept(this);
        if (!type.equals("boolean")) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("predicate in if-statement does not have type boolean"));
        }
        node.getThenStmt().accept(this);
        node.getElseStmt().accept(this);
        // varSymbolTable.exitScope();
        return null;
    }

    /**
     * Visit a while statement node
     * 
     * @param node the while statement node
     * @return result of the visit
     */
    public Object visit(WhileStmt node) {
        withinLoop = true;
        var type = node.getPredExpr().accept(this);
        // System.out.println(node.getLineNum()+" "+type);
        if (!type.equals("boolean")) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("predicate in while-statement does not have type boolean"));
        }
        // System.out.println(node.getBodyStmt());
        node.getBodyStmt().accept(this);
        withinLoop = false;
        // varSymbolTable.exitScope();
        return null;
    }

    /**
     * Visit a for statement node
     * 
     * @param node the for statement node
     * @return result of the visit
     */
    public Object visit(ForStmt node) {
        withinLoop = true;
        // varSymbolTable.enterScope();
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
        withinLoop = false;
        // varSymbolTable.exitScope();
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
        // varSymbolTable.enterScope();
        // System.out.println(node.getLineNum() +" "+methodSymbolTable);
        methodSymbolTable.enterScope();
        // System.out.println(node.getLineNum() +" "+methodSymbolTable);
        node.getStmtList().accept(this);
        // varSymbolTable.exitScope();
        // System.out.println(node.getLineNum() +" "+methodSymbolTable);
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
        // System.out.println(node);
        Object returnedType = null;
        String declaredType = currentMethod.getReturnType();
        if (node.getExpr() != null) {
            returnedType = node.getExpr().accept(this);
            
            if (returnedType.equals("void")) {
                errorHandler.register(2, fileName, node.getLineNum(),
                            String.format(
                                    "cannot return an expression of type 'void' from a method"));
                node.getExpr().setExprType("Object");
                returnedType = "Object";
            }
            //  System.out.println(node.getLineNum()+ " "+returnedType);
            if (isRefType(declaredType)) {
                // System.out.println("helloref " + node.getLineNum());
                if (!typesConform(returnedType.toString(), declaredType)) {
                    errorHandler.register(2, fileName, node.getLineNum(),
                            String.format(
                                    "return type '%s' does not conform to declared return type "
                                            + "'%s' in method '%s'",
                                    returnedType, declaredType, currentMethod.getName()));
                }
            } else if (!typesCompatible(returnedType.toString(), declaredType)) {
                // System.out.println(node.getLineNum() +" "+ returnedType + " " +
                // declaredType);
                errorHandler.register(2, fileName, node.getLineNum(),
                        String.format(
                                "return type '%s' is not compatible with declared return type "
                                        + "'%s' in method '%s'",
                                returnedType, declaredType, currentMethod.getName()));
            }
            return returnedType;
        } else {
            returnedType = "void";
            // System.out.println(node.getLineNum()+" "+returnedType);
            if (typeExists(declaredType) && !typesCompatible(returnedType.toString(), declaredType)) {
                errorHandler.register(2, fileName, node.getLineNum(),
                        String.format(
                                "return type '%s' is not compatible with declared return type "
                                        + "'%s' in method '%s'",
                                returnedType, declaredType, currentMethod.getName()));
            }
            return returnedType;
        }
    }

    /**
     * Visit a dispatch expression node
     * 
     * @param node the dispatch expression node
     * @return result of the visit
     */
    public Object visit(DispatchExpr node) {
        var refExprType = node.getRefExpr().accept(this);
        node.getActualList().accept(this);
        var refExpr = node.getRefExpr();
        var name = "";

        // System.out.println(node.getLineNum());
        if (refExpr instanceof VarExpr) {
            name = ((VarExpr) refExpr).getName();
        }
        // var refExpr = node.getRefExpr().getName();
        String type = null;
        // if (isPrimitive(refExprType.toString()) || isVoid(refExprType.toString())) {
        // errorHandler.register(2, fileName, node.getLineNum(), "can't dispatch on a
        // primitive or void type");
        // return "Object";
        // }

        if (name.equals("this")) {
            // System.out.println(node.getLineNum()+" this");
            // System.out.println(node.getLineNum() + " " + node.getMethodName());
            var method = methodSymbolTable.lookup(node.getMethodName());
            if (method != null) {
                var methodNode = (Method) method;
                type = methodNode.getReturnType();

                var formalListSize = methodNode.getFormalList().getSize();
                var actualListSize = node.getActualList().getSize();
                // System.out.println(node.getLineNum() + " " + formalListSize + " " +
                // actualListSize);

                if (formalListSize != actualListSize) {
                    errorHandler.register(2, fileName, node.getLineNum(),
                            String.format(
                                    "number of actual parameters (%d) differs from number of formal parameters (%d) in dispatch to method '%s'",
                                    actualListSize,
                                    formalListSize, node.getMethodName()));
                }

                String[] formalTypes = new String[actualListSize + 1];
                int count = 0;
                for (Iterator it = methodNode.getFormalList().getIterator(); it.hasNext();) {
                    var formalType = ((Formal) it.next()).getType();
                    // System.out.println(formalType);
                    formalTypes[count] = formalType;
                    count++;
                }
                if (isVoid(type)) {
                    // System.out.println(node.getLineNum());
                    node.setExprType("void");
                }
                int counter = 0;
                for (Iterator it = node.getActualList().getIterator(); it.hasNext();) {
                    counter++;
                    var actualtype = ((Expr) it.next()).getExprType();
                    if (actualtype.equals("void")) {
                        errorHandler.register(2, fileName, node.getLineNum(), String.format(
                                "actual parameter %d in the call to method %s is void and cannot be used within an expression",
                                counter, node.getMethodName()));
                    } else if (counter <= formalListSize && !actualtype.equals(formalTypes[counter - 1])) {
                        // System.out.println(node.getLineNum());
                        errorHandler.register(2, fileName, node.getLineNum(), String.format(
                                "actual parameter %d with type '%s' does not match formal parameter %d with declared type '%s' in dispatch to method '%s'",
                                counter, actualtype.toString(), counter, formalTypes[counter - 1],
                                node.getMethodName()));
                    }

                }

            } else {
                errorHandler.register(2, fileName, node.getLineNum(),
                        String.format("dispatch to unknown method '%s'", node.getMethodName()));
            }
        } else if (name.equals("super")) {
            // System.out.println(node.getLineNum());
        } else {
            if (refExprType != null && (isPrimitive(refExprType.toString()) || isVoid(refExprType.toString()))) {
                errorHandler.register(2, fileName, node.getLineNum(), "can't dispatch on a primitive or void type");
                node.setExprType("Object");
                // return "Object";
            }
            if (classMap.get(refExprType) != null) {
                var classCTN = classMap.get(refExprType);
                var classMST = classCTN.getMethodSymbolTable();
                // System.out.println(classMST);
                var method = classMST.lookup(node.getMethodName());
                if (method != null) {
                    var methodNode = (Method) method;
                    type = methodNode.getReturnType();

                    var formalListSize = methodNode.getFormalList().getSize();
                    var actualListSize = node.getActualList().getSize();
                    // System.out.println(node.getLineNum() + " " + formalListSize + " " +
                    // actualListSize);

                    if (formalListSize != actualListSize) {
                        errorHandler.register(2, fileName, node.getLineNum(),
                                String.format(
                                        "number of actual parameters (%d) differs from number of formal parameters (%d) in dispatch to method '%s'",
                                        actualListSize,
                                        formalListSize, node.getMethodName()));
                    }

                    String[] formalTypes = new String[actualListSize + 1];
                    int count = 0;
                    for (Iterator it = methodNode.getFormalList().getIterator(); it.hasNext();) {
                        var formalType = ((Formal) it.next()).getType();
                        // System.out.println(formalType);
                        formalTypes[count] = formalType;
                        count++;
                    }
                    if (isVoid(type)) {
                        node.setExprType("void");
                    }
                    int counter = 0;
                    for (Iterator it = node.getActualList().getIterator(); it.hasNext();) {
                        counter++;
                        var actualtype = ((Expr) it.next()).getExprType();
                        if (actualtype.equals("void")) {
                            errorHandler.register(2, fileName, node.getLineNum(), String.format(
                                    "actual parameter %d in the call to method %s is void and cannot be used within an expression",
                                    counter, node.getMethodName()));
                        } else if (counter <= formalListSize && !actualtype.equals("Object")
                                && !typesCompatible(actualtype, formalTypes[counter - 1])) {
                            // System.out.println(node.getLineNum());
                            errorHandler.register(2, fileName, node.getLineNum(), String.format(
                                    "actual parameter %d with type '%s' does not match formal parameter %d with declared type '%s' in dispatch to method '%s'",
                                    counter, actualtype.toString(), counter, formalTypes[counter - 1],
                                    node.getMethodName()));
                        } else if (counter <= formalListSize && !typesConform(actualtype, formalTypes[counter - 1])) {
                            // System.out.println(node.getLineNum());
                            errorHandler.register(2, fileName, node.getLineNum(), String.format(
                                    "actual parameter %d with type '%s' does not conform to formal parameter %d with declared type '%s' in dispatch to method '%s'",
                                    counter, actualtype.toString(), counter, formalTypes[counter - 1],
                                    node.getMethodName()));
                        }

                    }

                } else {
                    errorHandler.register(2, fileName, node.getLineNum(),
                            String.format("dispatch to unknown method '%s'", node.getMethodName()));
                }
            }

        }

        // System.out.println(node.getLineNum()+" Super");
        // } else {
        // errorHandler.register(2, fileName, node.getLineNum(),
        // String.format("bad reference '%s': fields are 'protected' and can only be
        // accessed within the class or subclass via 'this' or 'super'", name));
        // }

        return type;
    }

    /**
     * Visit a new expression node
     * 
     * @param node the new expression node
     * @return result of the visit
     */
    public Object visit(NewExpr node) {
        var type = node.getType();
        if (!typeExists(type)) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("type '%s' of new construction is undefined", type));
            node.setExprType("Object");
            return "Object";
        }
        if (isPrimitive(type)) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("type '%s' of new construction is primitive and cannot be constructed", type));
            node.setExprType("Object");
            return "Object";
        }
        // if (classMap.get(type) != null) {
        // node.setExprType(type);
        // return type;
        // }
        return type;
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
        var rhsType = node.getType();
        var lhsType = node.getExpr().accept(this);
        boolean valid = true;
        if (lhsType != null && isPrimitive(lhsType.toString())) {
            errorHandler.register(2, fileName, node.getLineNum(), String.format(
                    "the instanceof lefthand expression has type 'int', which is primitive and not an object type",
                    lhsType));
        }
        if (isPrimitive(rhsType)) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("the instanceof righthand type '%s' is primitive and not an object type", rhsType));
        }
        if (!typeExists(rhsType)) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("the instanceof righthand type '%s' is undefined", rhsType));

        }
        if (lhsType != null && isVoid(lhsType.toString())) {
            errorHandler.register(2, fileName, node.getLineNum(), String.format(
                    "the instanceof lefthand expression has type 'int', which is primitive and not an object type",
                    lhsType));
            valid = false;
        }

        if (valid) {
            node.setExprType("boolean");
            return "boolean";
        }
        return null;
    }

    /**
     * Visit a cast expression node
     * 
     * @param node the cast expression node
     * @return result of the visit
     */
    public Object visit(CastExpr node) {
        boolean valid = true;
        var exprType = node.getExpr().accept(this);
        // figure out logic for up/downcast
        var targetType = node.getType();
        // System.out.println(node.getLineNum()+" "+targetType);
        if (isPrimitive(targetType)) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("the target type '%s' is primitive and not an object type", targetType));
            targetType = "Object";
        } else if (!isRefType(targetType)) { // this condition is not strong enough
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("the target type '%s' is undefined", targetType));
            targetType = "Object";
        } else if (exprType != null && !typesConform(exprType.toString(), targetType)) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format("inconvertible types ('%s'=>'%s')", exprType, targetType));
        }
        if (exprType != null && isPrimitive(exprType.toString())) {
            errorHandler.register(2, fileName, node.getLineNum(), String
                    .format("expression in cast has type '%s', which is primitive and can't be casted", exprType));
            valid = false;
        }
        if (valid) {
            node.setExprType(targetType);
            // set boolean flag
            // node.setUpCast(...);
        }
        return targetType;
    }

    /**
     * Visit an assignment expression node
     * 
     * @param node the assignment expression node
     * @return result of the visit
     */
    public Object visit(AssignExpr node) {
        // System.out.println(node);
        var rhsType = node.getExpr().accept(this);
        var a = node.getRefName();
        var b = node.getName();
        var lhsType = "";
        if (a != null) { // im guessing null if a. is absent
            // System.out.println(node.getLineNum() + "In here");
            if (a.equals("this")) {
                // System.out.println(node.getLineNum() + " " + varSymbolTable.toString());
                var type = varSymbolTable.peek(b);
                if (type != null) {
                    lhsType = type.toString();
                    if (rhsType != null && typesCompatible(rhsType.toString(), lhsType)) {
                        node.setExprType(rhsType.toString());
                        return rhsType;
                    }
                    if (rhsType != null && !isPrimitive(lhsType) && !isPrimitive(rhsType.toString())
                            && !typesConform(rhsType.toString(), lhsType)) {
                        errorHandler.register(2, fileName, node.getLineNum(),
                                String.format(
                                        "the righthand type '%s' does not conform to the lefthand type '%s' in assignment",
                                        rhsType, lhsType));
                    } else if (rhsType != null && !typesCompatible(rhsType.toString(), lhsType)) {
                        errorHandler.register(2, fileName, node.getLineNum(),
                                String.format(
                                        "the lefthand type '%s' and righthand type '%s' are not compatible in assignment",
                                        lhsType, rhsType));
                    }
                }
            } else if (a.equals("super")) { // for the super
                var currentClass = classMap.get(className);
                var currentClassParent = currentClass.getParent();
                var superSymbolTable = currentClassParent.getVarSymbolTable();
                var type = superSymbolTable.peek(b);
                if (type != null) {
                    lhsType = type.toString();
                    if (rhsType != null && typesCompatible(rhsType.toString(), lhsType)) {
                        node.setExprType(rhsType.toString());
                        return rhsType;
                    }
                    if (rhsType != null && !isPrimitive(lhsType) && !isPrimitive(rhsType.toString())
                            && !typesConform(rhsType.toString(), lhsType)) {
                        errorHandler.register(2, fileName, node.getLineNum(),
                                String.format(
                                        "the righthand type '%s' does not conform to the lefthand type '%s' in assignment",
                                        rhsType, lhsType));
                    } else if (rhsType != null && !typesCompatible(rhsType.toString(), lhsType)) {
                        errorHandler.register(2, fileName, node.getLineNum(),
                                String.format(
                                        "the lefthand type '%s' and righthand type '%s' are not compatible in assignment",
                                        lhsType, rhsType));
                    }
                }
            } else {
                errorHandler.register(2, fileName, node.getLineNum(), String.format(
                        "bad reference '%s': fields are 'protected' and can only be accessed within the class or subclass via 'this' or 'super'",
                        a));
            }
        }
        // System.out.println(node.getLineNum() + " " + varSymbolTable.toString());
        var type = methodSymbolTable.peek(b);
        if (type != null && rhsType != null) {
            lhsType = type.toString();
            if (typesCompatible(rhsType.toString(), lhsType)) {
                node.setExprType(rhsType.toString());
                return rhsType;
            }
            if (!isPrimitive(lhsType) && !isPrimitive(rhsType.toString())
                    && !typesConform(rhsType.toString(), lhsType)) {
                errorHandler.register(2, fileName, node.getLineNum(),
                        String.format(
                                "the righthand type '%s' does not conform to the lefthand type '%s' in assignment",
                                rhsType, lhsType));
            } else if (!typesCompatible(rhsType.toString(), lhsType)) {
                errorHandler.register(2, fileName, node.getLineNum(),
                        String.format("the lefthand type '%s' and righthand type '%s' are not compatible in assignment",
                                lhsType, rhsType));
            }
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
        // System.out.println(node.getLineNum());
        var lhsType = node.getLeftExpr().accept(this);
        var rhsType = node.getRightExpr().accept(this);
        if (lhsType != null && rhsType != null) {
            if (isPrimitive(rhsType.toString()) || isPrimitive(lhsType.toString())) {
                if (!typesCompatible(rhsType.toString(), lhsType.toString())) {
                    errorHandler.register(2, fileName, node.getLineNum(),
                            String.format(
                                    "the lefthand type '%s' in the binary operation ('%s') "
                                            + "does not match the righthand type '%s'",
                                    lhsType, node.getOpName(), rhsType));
                }
            }
        }
        node.setExprType("boolean");
        return "boolean";
    }

    /**
     * Visit a binary comparison not equals expression node
     * 
     * @param node the binary comparison not equals expression node
     * @return result of the visit
     */
    public Object visit(BinaryCompNeExpr node) {
        // System.out.println(node.getLineNum());
        var lhsType = node.getLeftExpr().accept(this);
        var rhsType = node.getRightExpr().accept(this);
        if (lhsType != null && rhsType != null) {
            if (isPrimitive(rhsType.toString()) || isPrimitive(lhsType.toString())) {
                if (!typesCompatible(rhsType.toString(), lhsType.toString())) {
                    errorHandler.register(2, fileName, node.getLineNum(),
                            String.format(
                                    "the lefthand type '%s' in the binary operation ('%s') "
                                            + "does not match the righthand type '%s'",
                                    lhsType, node.getOpName(), rhsType));
                }
            }
        }
        node.setExprType("boolean");
        return "boolean";
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
        // System.out.println(node.getLineNum());
        var lhsType = node.getLeftExpr().accept(this);
        var rhsType = node.getRightExpr().accept(this);
        var operandType = node.getOperandType();
        if (lhsType != null && !lhsType.equals(operandType)) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format(
                            "the lefthand type '%s' in the binary operation ('%s') is "
                                    + "incorrect; should have been: %s",
                            lhsType, node.getOpName(), node.getOpType()));
        }
        if (rhsType != null && !rhsType.equals(operandType)) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format(
                            "the righthand type '%s' in the binary operation ('%s') is "
                                    + "incorrect; should have been: %s",
                            rhsType, node.getOpName(), node.getOpType()));
        }
        var operatorType = node.getOpType();
        node.setExprType(operatorType);
        return operatorType;
    }

    /**
     * Visit a binary arithmetic minus expression node
     * 
     * @param node the binary arithmetic minus expression node
     * @return result of the visit
     */
    public Object visit(BinaryArithMinusExpr node) {
        // System.out.println(node.getLineNum());
        var lhsType = node.getLeftExpr().accept(this);
        var rhsType = node.getRightExpr().accept(this);
        var operandType = node.getOperandType();
        if (lhsType != null && !lhsType.equals(operandType)) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format(
                            "the lefthand type '%s' in the binary operation ('%s') is "
                                    + "incorrect; should have been: %s",
                            lhsType, node.getOpName(), node.getOpType()));
        }
        if (rhsType != null && !rhsType.equals(operandType)) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format(
                            "the righthand type '%s' in the binary operation ('%s') is "
                                    + "incorrect; should have been: %s",
                            rhsType, node.getOpName(), node.getOpType()));
        }
        var operatorType = node.getOpType();
        node.setExprType(operatorType);
        return operatorType;
    }

    /**
     * Visit a binary arithmetic times expression node
     * 
     * @param node the binary arithmetic times expression node
     * @return result of the visit
     */
    public Object visit(BinaryArithTimesExpr node) {
        // System.out.println(node.getLineNum());
        var lhsType = node.getLeftExpr().accept(this);
        var rhsType = node.getRightExpr().accept(this);
        var operandType = node.getOperandType();
        if (lhsType != null && !lhsType.equals(operandType)) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format(
                            "the lefthand type '%s' in the binary operation ('%s') is "
                                    + "incorrect; should have been: %s",
                            lhsType, node.getOpName(), node.getOpType()));
        }
        if (rhsType != null && !rhsType.equals(operandType)) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format(
                            "the righthand type '%s' in the binary operation ('%s') is "
                                    + "incorrect; should have been: %s",
                            rhsType, node.getOpName(), node.getOpType()));
        }
        var operatorType = node.getOpType();
        node.setExprType(operatorType);
        return operatorType;
    }

    /**
     * Visit a binary arithmetic divide expression node
     * 
     * @param node the binary arithmetic divide expression node
     * @return result of the visit
     */
    public Object visit(BinaryArithDivideExpr node) {
        // System.out.println(node.getLineNum());
        var lhsType = node.getLeftExpr().accept(this);
        var rhsType = node.getRightExpr().accept(this);
        var operandType = node.getOperandType();
        if (lhsType != null && !lhsType.equals(operandType)) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format(
                            "the lefthand type '%s' in the binary operation ('%s') is "
                                    + "incorrect; should have been: %s",
                            lhsType, node.getOpName(), node.getOpType()));
        }
        if (rhsType != null && !rhsType.equals(operandType)) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format(
                            "the righthand type '%s' in the binary operation ('%s') is "
                                    + "incorrect; should have been: %s",
                            rhsType, node.getOpName(), node.getOpType()));
        }
        var operatorType = node.getOpType();
        node.setExprType(operatorType);
        return operatorType;
    }

    /**
     * Visit a binary arithmetic modulus expression node
     * 
     * @param node the binary arithmetic modulus expression node
     * @return result of the visit
     */
    public Object visit(BinaryArithModulusExpr node) {
        // System.out.println(node.getLineNum());
        var lhsType = node.getLeftExpr().accept(this);
        var rhsType = node.getRightExpr().accept(this);
        var operandType = node.getOperandType();
        if (lhsType != null && !lhsType.equals(operandType)) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format(
                            "the lefthand type '%s' in the binary operation ('%s') is "
                                    + "incorrect; should have been: %s",
                            lhsType, node.getOpName(), node.getOpType()));
        }
        if (rhsType != null && !rhsType.equals(operandType)) {
            errorHandler.register(2, fileName, node.getLineNum(),
                    String.format(
                            "the righthand type '%s' in the binary operation ('%s') is "
                                    + "incorrect; should have been: %s",
                            rhsType, node.getOpName(), node.getOpType()));
        }
        var operatorType = node.getOpType();
        node.setExprType(operatorType);
        return operatorType;
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
        var lhsType = node.getLeftExpr().accept(this);
        var rhsType = node.getRightExpr().accept(this);
        var operandType = node.getOperandType();

        if (lhsType != null && !lhsType.equals(operandType)) {

        }
        if (rhsType != null && !rhsType.equals(operandType)) {

        }
        var operatorType = node.getOpType();
        node.setExprType(operatorType);
        return operatorType;
    }

    /**
     * Visit a unary negation expression node
     * 
     * @param node the unary negation expression node
     * @return result of the visit
     */
    public Object visit(UnaryNegExpr node) {
        Object exprType = node.getExpr().accept(this);
        // exprType = value.toString();
        if (exprType != null && exprType.equals(node.getOperandType())) {
            node.setExprType(exprType.toString());
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
        var exprType = node.getExpr().accept(this);
        if (exprType != null && exprType.equals(node.getOperandType())) {
            node.setExprType(exprType.toString());
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
        // System.out.println(node);
        // System.out.println(node.getLineNum()+" "+ node.getRef());
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
                // System.out.println("Name: " +node.getName()+" node exprType:
                // "+node.getExprType());
                // System.out.println(node.getLineNum() + " " + methodSymbolTable.toString());
                var type = methodSymbolTable.peek(varName);

                // System.out.println(node.getName());
                // System.out.println(type);
                // System.out.println("This should be boolean at last: "+type);
                if (type != null) {
                    if (typeExists(type.toString())) {
                        // System.out.println("Im here " + type);
                        node.setExprType(type.toString());
                        return type;
                    } else {
                        node.setExprType("Object");
                        return "Object";
                    }
                }
                return type;
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
        // System.out.println(className +" "+ currentMethod.getName() + ": " +
        // node.getConstant());
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

    private boolean typesConform(String actualType, String expectedType) {
        if (typesCompatible(actualType, expectedType)) {
            return true;
        }

        if (expectedType.equals("Object") && (isRefType(actualType) || actualType == null)) {
            return true;
        }

        if (isRefType(expectedType) && actualType == null) {
            return true;
        }
        return false;
    }

    private boolean isRefType(String type) {
        if (classMap.get(type) != null)
            return true;
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
        return false;
    }

}
