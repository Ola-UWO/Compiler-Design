package codegenjvm;

import java.util.*;
import ast.*;
import util.*;
import visitor.*;
import java.io.*;

public class CodeGenVisitor extends Visitor {

    StringBuffer sourceCode = new StringBuffer();
    StringBuffer fieldBuffer = new StringBuffer();
    StringBuffer methodBuffer = new StringBuffer();
    StringBuffer stmtBuffer = new StringBuffer();
    StringBuffer exprBuffer = new StringBuffer();
    ArrayList<String> args = new ArrayList<>();
    boolean hasConstructor = false;
    boolean hasReturn = false;
    int stackSize = 0;
    int locals = 1;
    int counter = 0;
    String fileName;
    String className;
    String parentName;
    ArrayList<Integer> stack = new ArrayList<>();


    /**
     * Visit a class node
     */
    public Object visit(Class_ node) {
        sourceCode.setLength(0); // reset for new class
        methodBuffer.setLength(0);
        fieldBuffer.setLength(0);
        fileName = node.getFilename();
        className = node.getName();
        parentName = node.getParent();
        // header
        sourceCode.append(
                String.format(".source %s%n.class protected %s%n.super %s%n" + 
                        ".implements java/lang/Cloneable%n%n",
                        fileName, className, builtInDescriptors(parentName)));
        node.getMemberList().accept(this);
        writeToFile(className + ".j");
        return null;
    }

    /**
     * Visit a list node of members
     */
    public Object visit(MemberList node) {
        for (Iterator it = node.getIterator(); it.hasNext();) {
            var member = (Member) it.next();
            if (member instanceof Method) {
                var method = (Method) member;
                hasConstructor = method.getName().equals(className);
            }
            member.accept(this);
        }
        return null;
    }

    /**
     * Visit a field node
     */
    public Object visit(Field node) {
        fieldBuffer.append(
                String.format(".field protected %s %s%n", node.getName(), 
                builtInDescriptors(node.getType())));
        if (node.getInit() != null)
            node.getInit().accept(this);
        return null;
    }

    /**
     * Visit a method node
     */
    public Object visit(Method node) {
        // header
        if (!hasConstructor) { // subclassing user def classes not implemented yet
            methodBuffer.append(defaultConstructor());
        }
        methodBuffer.append(boilerPlateMain());
        methodBuffer.append(
                String.format(".method protected %s(", node.getName()));
        node.getFormalList().accept(this);
        methodBuffer.append(
                String.format(")%s%n.throws java/lang/CloneNotSupportedException%n", 
                builtInDescriptors(node.getReturnType())));
        node.getStmtList().accept(this);
        methodBuffer.append(String.format(
                "    .limit stack %d%n    .limit locals %d%n", 
                calculateStackHeight(), locals)); 
        stack = new ArrayList<Integer>(); // reset counters
        stackSize = 0; 
        locals = 0;
        methodBuffer.append(stmtBuffer);
        if (!hasReturn) {
            methodBuffer.append(String.format("    return%n"));
            hasReturn = false;
        }
        methodBuffer.append(String.format(".end method%n%n"));
        counter = 0;
        return null;
    }

    /**
     * Visit a list node of formals
     */
    public Object visit(FormalList node) {
        for (Iterator it = node.getIterator(); it.hasNext(); ++locals) {
            var formal = (Formal) it.next();
            methodBuffer.append(formal.getType());
            formal.accept(this);
        }
        return null;
    }

    /**
     * Visit a list node of statements
     */
    public Object visit(StmtList node) {
        for (Iterator it = node.getIterator(); it.hasNext();) {
            var stmt = (Stmt) it.next();
            if (stmt instanceof ReturnStmt)
                hasReturn = true;
            stmt.accept(this);
        }
        return null;
    }

    /**
     * Visit a declaration statement node
     */
    public Object visit(DeclStmt node) {
        // Store local variable
        int localVarIndex = locals++;
        node.getInit().accept(this);
        String type = node.getType();
        
        // Generate appropriate store instruction based on type
        if (type.equals("int") || type.equals("boolean")) {
            stmtBuffer.append(String.format("    istore %d%n", localVarIndex));
        } else {
            stmtBuffer.append(String.format("    astore %d%n", localVarIndex));
        }
        
        return null;
    }

    /**
     * Visit an expression statement node
     */
    public Object visit(ExprStmt node) {
        exprBuffer.setLength(0); // Clear expression buffer
        node.getExpr().accept(this);
        stmtBuffer.append(exprBuffer);
        
        // Pop the value from stack if it's not used (expression statement)
        String type = node.getExpr().getExprType();
        if (!type.equals("void")) {
            if (type.equals("int") || type.equals("boolean")) {
                stmtBuffer.append("    pop\n");
            } else {
                stmtBuffer.append("    pop\n");
            }
            stackSize--;
        }
        
        return null;
    }

    /**
     * Visit an if statement node
     */
    public Object visit(IfStmt node) {
        // Generate unique labels for if statement
        String elseLabel = "L" + ++counter;
        String endLabel = "L" + ++counter;
        
        // Evaluate the predicate expression
        exprBuffer.setLength(0);
        node.getPredExpr().accept(this);
        stmtBuffer.append(exprBuffer);
        
        // If predicate is false, jump to else
        stmtBuffer.append(String.format("    ifeq %s%n", elseLabel));
        
        // Then branch
        node.getThenStmt().accept(this);
        stmtBuffer.append(String.format("    goto %s%n", endLabel));
        
        // Else branch
        stmtBuffer.append(String.format("%s:%n", elseLabel));
        node.getElseStmt().accept(this);
        
        // End of if statement
        stmtBuffer.append(String.format("%s:%n", endLabel));
        
        return null;
    }

    /**
     * Visit a while statement node
     */
    public Object visit(WhileStmt node) {
        // Generate unique labels for while loop
        String startLabel = "L" + ++counter;
        String endLabel = "L" + ++counter;
        
        // Start of while loop
        stmtBuffer.append(String.format("%s:%n", startLabel));
        
        // Evaluate the predicate expression
        exprBuffer.setLength(0);
        node.getPredExpr().accept(this);
        stmtBuffer.append(exprBuffer);
        
        // If predicate is false, exit loop
        stmtBuffer.append(String.format("    ifeq %s%n", endLabel));
        
        // Loop body
        node.getBodyStmt().accept(this);
        
        // Jump back to the beginning of the loop
        stmtBuffer.append(String.format("    goto %s%n", startLabel));
        
        // End of while loop
        stmtBuffer.append(String.format("%s:%n", endLabel));
        
        return null;
    }

    /**
     * Visit a for statement node
     */
    public Object visit(ForStmt node) {
        // Generate unique labels for for loop
        String startLabel = "L" + ++counter;
        String updateLabel = "L" + ++counter;
        String endLabel = "L" + ++counter;
        
        // Initialization
        if (node.getInitExpr() != null) {
            exprBuffer.setLength(0);
            node.getInitExpr().accept(this);
            stmtBuffer.append(exprBuffer);
            // Pop the value if it's not void (initialization might be an expression)
            if (!node.getInitExpr().getExprType().equals("void")) {
                stmtBuffer.append("    pop\n");
                stackSize--;
            }
        }
        
        // Start of for loop
        stmtBuffer.append(String.format("%s:%n", startLabel));
        
        // Predicate evaluation
        if (node.getPredExpr() != null) {
            exprBuffer.setLength(0);
            node.getPredExpr().accept(this);
            stmtBuffer.append(exprBuffer);
            stmtBuffer.append(String.format("    ifeq %s%n", endLabel));
        }
        
        // Loop body
        node.getBodyStmt().accept(this);
        
        // Update expression
        stmtBuffer.append(String.format("%s:%n", updateLabel));
        if (node.getUpdateExpr() != null) {
            exprBuffer.setLength(0);
            node.getUpdateExpr().accept(this);
            stmtBuffer.append(exprBuffer);
            // Pop the value if it's not void (update might be an expression)
            if (!node.getUpdateExpr().getExprType().equals("void")) {
                stmtBuffer.append("    pop\n");
                stackSize--;
            }
        }
        
        // Jump back to the predicate evaluation
        stmtBuffer.append(String.format("    goto %s%n", startLabel));
        
        // End of for loop
        stmtBuffer.append(String.format("%s:%n", endLabel));
        
        return null;
    }

    /**
     * Visit a break statement node
     */
    public Object visit(BreakStmt node) {
        // Find the enclosing loop...
        stmtBuffer.append("    goto ENDLOOP\n"); 
        return null;
    }

    /**
     * Visit a block statement node
     */
    public Object visit(BlockStmt node) {
        node.getStmtList().accept(this);
        return null;
    }

    /**
     * Visit a return statement node
     */
    public Object visit(ReturnStmt node) {
        if (node.getExpr() != null) {
            var expr = node.getExpr();
            expr.accept(this);
            var exprType = expr.getExprType();
            stmtBuffer.append(String.format("    %s%n", returnByteCodes(exprType)));
        }
        stmtBuffer.append(String.format("    return%n"));
        return null;
    }

    /**
     * Visit a list node of expressions
     */
    public Object visit(ExprList node) {
        args.clear(); // Reset argument list
        for (Iterator it = node.getIterator(); it.hasNext();) {
            var expr = (Expr) it.next();
            args.add(expr.getExprType());
            expr.accept(this);
        }
        return null;
    }

    /**
     * Visit a dispatch expression node
     */
    public Object visit(DispatchExpr node) {
        var refExpr = node.getRefExpr();
        refExpr.accept(this);
        var type = refExpr.getExprType();
        
        args.clear(); // Reset argument list before collecting new ones
        node.getActualList().accept(this);
        
        exprBuffer.append(
            String.format("    invokevirtual %s/%s(%s)%s%n", 
                type, 
                node.getMethodName(), 
                getArgTypes(), 
                builtInDescriptors(node.getExprType()))
        );
        
        // Update stack size based on method return type
        if (!node.getExprType().equals("void")) {
            stackSize++;
            stack.add(stackSize);
        } else {
            stackSize--; // Pop the object reference
            stack.add(stackSize);
        }
        
        return null;
    }

    /**
     * Visit a new expression node
     */
    public Object visit(NewExpr node) {
        stackSize+=2;
        var type = node.getType();
        stack.add(stackSize);
        exprBuffer.append(
            String.format("    new %s%n    dup%n    invokespecial %s/<init>()V%n", 
                type, type)
        );
        stackSize--;
        stack.add(stackSize);
        return null;
    }

    /**
     * Visit a new array expression node
     */
    public Object visit(NewArrayExpr node) {
        exprBuffer.setLength(0);
        node.getSize().accept(this);
        
        String arrayType = node.getType();
        // Remove [] suffix
        String elementType = arrayType.substring(0, arrayType.length() - 2);
        
        // Generate appropriate newarray instruction based on element type
        if (elementType.equals("int")) {
            exprBuffer.append("    newarray int\n");
        } else if (elementType.equals("boolean")) {
            exprBuffer.append("    newarray boolean\n");
        } else {
            exprBuffer.append(String.format("    anewarray %s\n", elementType));
        }
        
        // Update stack size (pop size, push array reference)
        stackSize--;
        stackSize++;
        stack.add(stackSize);
        
        return null;
    }

    /**
     * Visit an instanceof expression node
     */
    public Object visit(InstanceofExpr node) {
        exprBuffer.setLength(0);
        node.getExpr().accept(this);
        exprBuffer.append(String.format("    instanceof %s\n", node.getType()));
        
        // Update stack size (pop reference, push int result)
        stackSize--;
        stackSize++;
        stack.add(stackSize);
        
        return null;
    }

    /**
     * Visit a cast expression node
     */
    public Object visit(CastExpr node) {
        exprBuffer.setLength(0);
        node.getExpr().accept(this);
        String type = node.getType();
        
        // Generate appropriate checkcast instruction
        exprBuffer.append(String.format("    checkcast %s\n", type));
        
        // Stack size doesn't change for cast
        
        return null;
    }

    /**
     * Visit an assignment expression node
     */
    public Object visit(AssignExpr node) {
        exprBuffer.setLength(0);
        node.getExpr().accept(this);
        
        // Duplicate the value for expression result
        exprBuffer.append("    dup\n");
        stackSize++;
        stack.add(stackSize);
        
        // Store the value in the variable
        String type = node.getExpr().getExprType();
        var var = node.getName();
        
        // Check if it's a field or local variable
        if (var != null) { // var.getRef() != null
            // Field access
            exprBuffer.append("    aload_0\n"); // Load this reference
            stackSize++;
            stack.add(stackSize);
            
            exprBuffer.append(String.format("    swap\n"));
            exprBuffer.append(String.format("    putfield %s/%s %s\n", 
                className, var, builtInDescriptors(type)));
            stackSize -= 2; // Pop object ref and value
        } else {
            // Local variable
            int localIndex = 0; // getLocalIndex(var);
            if (type.equals("int") || type.equals("boolean")) {
                exprBuffer.append(String.format("    istore %d\n", localIndex));
            } else {
                exprBuffer.append(String.format("    astore %d\n", localIndex));
            }
            stackSize--; // Pop stored value, leave result on stack
        }
        
        stack.add(stackSize);
        return null;
    }

    /**
     * Visit an array assignment expression node
     */
    public Object visit(ArrayAssignExpr node) {
        exprBuffer.setLength(0);
        
        // Load array reference
        String var = node.getName();
        if (var != null) { // var.getRefExpr() != null
            // var.getRef().accept(this);
        } else {
            int localIndex = 0;
            // getLocalIndex(var.getName());
            exprBuffer.append(String.format("    aload %d\n", localIndex));
            stackSize++;
            stack.add(stackSize);
        }
        
        // Load index
        node.getIndex().accept(this);
        
        // Load and duplicate the value (for expression result)
        node.getExpr().accept(this);
        exprBuffer.append("    dup_x2\n");
        stackSize++;
        stack.add(stackSize);
        
        // Store the value in the array
        String type = node.getExpr().getExprType();
        if (type.equals("int") || type.equals("boolean")) {
            exprBuffer.append("    iastore\n");
        } else {
            exprBuffer.append("    aastore\n");
        }
        stackSize -= 3; // Pop array ref, index, and value
        stack.add(stackSize);
        
        return null;
    }

    /**
     * Visit a binary comparison equals expression node
     */
    public Object visit(BinaryCompEqExpr node) {
        exprBuffer.setLength(0);
        String type = node.getLeftExpr().getExprType();
        
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        
        String compareLabel = "L" + ++counter;
        String endLabel = "L" + ++counter;
        
        if (type.equals("int") || type.equals("boolean")) {
            exprBuffer.append(String.format("    if_icmpeq %s\n", compareLabel));
        } else {
            exprBuffer.append(String.format("    if_acmpeq %s\n", compareLabel));
        }
        
        // Push false (0)
        exprBuffer.append("    iconst_0\n");
        exprBuffer.append(String.format("    goto %s\n", endLabel));
        
        // Push true (1)
        exprBuffer.append(String.format("%s:\n", compareLabel));
        exprBuffer.append("    iconst_1\n");
        
        exprBuffer.append(String.format("%s:\n", endLabel));
        
        // Update stack size (pop 2 operands, push result)
        stackSize--;
        stack.add(stackSize);
        
        return null;
    }

    /**
     * Visit a binary comparison not equals expression node
     */
    public Object visit(BinaryCompNeExpr node) {
        exprBuffer.setLength(0);
        String type = node.getLeftExpr().getExprType();
        
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        
        String compareLabel = "L" + ++counter;
        String endLabel = "L" + ++counter;
        
        if (type.equals("int") || type.equals("boolean")) {
            exprBuffer.append(String.format("    if_icmpne %s\n", compareLabel));
        } else {
            exprBuffer.append(String.format("    if_acmpne %s\n", compareLabel));
        }
        
        // Push false (0)
        exprBuffer.append("    iconst_0\n");
        exprBuffer.append(String.format("    goto %s\n", endLabel));
        
        // Push true (1)
        exprBuffer.append(String.format("%s:\n", compareLabel));
        exprBuffer.append("    iconst_1\n");
        
        exprBuffer.append(String.format("%s:\n", endLabel));
        
        // Update stack size (pop 2 operands, push result)
        stackSize--;
        stack.add(stackSize);
        
        return null;
    }

    /**
     * Visit a binary comparison less than expression node
     */
    public Object visit(BinaryCompLtExpr node) {
        exprBuffer.setLength(0);
        
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        
        String compareLabel = "L" + ++counter;
        String endLabel = "L" + ++counter;
        
        exprBuffer.append(String.format("    if_icmplt %s\n", compareLabel));
        
        // Push false (0)
        exprBuffer.append("    iconst_0\n");
        exprBuffer.append(String.format("    goto %s\n", endLabel));
        
        // Push true (1)
        exprBuffer.append(String.format("%s:\n", compareLabel));
        exprBuffer.append("    iconst_1\n");
        
        exprBuffer.append(String.format("%s:\n", endLabel));
        
        // Update stack size (pop 2 operands, push result)
        stackSize--;
        stack.add(stackSize);
        
        return null;
    }

    /**
     * Visit a binary comparison less than or equal to expression node
     */
    public Object visit(BinaryCompLeqExpr node) {
        exprBuffer.setLength(0);
        
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        
        String compareLabel = "L" + ++counter;
        String endLabel = "L" + ++counter;
        
        exprBuffer.append(String.format("    if_icmple %s\n", compareLabel));
        
        // Push false (0)
        exprBuffer.append("    iconst_0\n");
        exprBuffer.append(String.format("    goto %s\n", endLabel));
        
        // Push true (1)
        exprBuffer.append(String.format("%s:\n", compareLabel));
        exprBuffer.append("    iconst_1\n");
        
        exprBuffer.append(String.format("%s:\n", endLabel));
        
        // Update stack size (pop 2 operands, push result)
        stackSize--;
        stack.add(stackSize);
        
        return null;
    }

    /**
     * Visit a binary comparison greater than expression node
     */
    public Object visit(BinaryCompGtExpr node) {
        exprBuffer.setLength(0);
        
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        
        String compareLabel = "L" + ++counter;
        String endLabel = "L" + ++counter;
        
        exprBuffer.append(String.format("    if_icmpgt %s\n", compareLabel));
        
        // Push false (0)
        exprBuffer.append("    iconst_0\n");
        exprBuffer.append(String.format("    goto %s\n", endLabel));
        
        // Push true (1)
        exprBuffer.append(String.format("%s:\n", compareLabel));
        exprBuffer.append("    iconst_1\n");
        
        exprBuffer.append(String.format("%s:\n", endLabel));
        
        // Update stack size (pop 2 operands, push result)
        stackSize--;
        stack.add(stackSize);
        
        return null;
    }

    /**
     * Visit a binary comparison greater than or equal to expression node
     */
    public Object visit(BinaryCompGeqExpr node) {
        exprBuffer.setLength(0);
        
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        
        String compareLabel = "L" + ++counter;
        String endLabel = "L" + ++counter;
        
        exprBuffer.append(String.format("    if_icmpge %s\n", compareLabel));
        
        // Push false (0)
        exprBuffer.append("    iconst_0\n");
        exprBuffer.append(String.format("    goto %s\n", endLabel));
        
        // Push true (1)
        exprBuffer.append(String.format("%s:\n", compareLabel));
        exprBuffer.append("    iconst_1\n");
        
        exprBuffer.append(String.format("%s:\n", endLabel));
        
        // Update stack size (pop 2 operands, push result)
        stackSize--;
        stack.add(stackSize);
        
        return null;
    }

    /**
     * Visit a binary arithmetic plus expression node
     */
    public Object visit(BinaryArithPlusExpr node) {
        exprBuffer.setLength(0);
        
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        
        exprBuffer.append("    iadd\n");
        
        // Update stack size (pop 2 operands, push result)
        stackSize--;
        stack.add(stackSize);
        
        return null;
    }

    /**
     * Visit a binary arithmetic minus expression node
     */
    public Object visit(BinaryArithMinusExpr node) {
        exprBuffer.setLength(0);
        
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        
        exprBuffer.append("    isub\n");
        
        // Update stack size (pop 2 operands, push result)
        stackSize--;
        stack.add(stackSize);
        
        return null;
    }

    /**
     * Visit a binary arithmetic times expression node
     */
    public Object visit(BinaryArithTimesExpr node) {
        exprBuffer.setLength(0);
        
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        
        exprBuffer.append("    imul\n");
        
        // Update stack size (pop 2 operands, push result)
        stackSize--;
        stack.add(stackSize);
        
        return null;
    }

    /**
     * Visit a binary arithmetic divide expression node
     */
    public Object visit(BinaryArithDivideExpr node) {
        exprBuffer.setLength(0);
        
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        
        exprBuffer.append("    idiv\n");
        
        // Update stack size (pop 2 operands, push result)
        stackSize--;
        stack.add(stackSize);
        
        return null;
    }

    /**
     * Visit a binary arithmetic modulus expression node
     */
    public Object visit(BinaryArithModulusExpr node) {
        exprBuffer.setLength(0);
        
        node.getLeftExpr().accept(this);
        node.getRightExpr().accept(this);
        
        exprBuffer.append("    irem\n");
        
        // Update stack size (pop 2 operands, push result)
        stackSize--;
        stack.add(stackSize);
        
        return null;
    }
    /**
     * Visit a binary logical AND expression node
     * 
     * @param node the binary logical AND expression node
     * @return result of the visit
     */
    public Object visit(BinaryLogicAndExpr node) {
        exprBuffer.setLength(0);
        
        String shortCircuitLabel = "L" + ++counter;
        String endLabel = "L" + ++counter;
        
        // Evaluate left side
        node.getLeftExpr().accept(this);
        
        // If left side is false, short-circuit
        exprBuffer.append("    dup\n");
        stackSize++;
        stack.add(stackSize);
        
        exprBuffer.append(String.format("    ifeq %s\n", shortCircuitLabel));
        
        // Pop duplicated left value
        exprBuffer.append("    pop\n");
        stackSize--;
        stack.add(stackSize);
        
        // Evaluate right side
        node.getRightExpr().accept(this);
        
        // End of AND expression
        exprBuffer.append(String.format("    goto %s\n", endLabel));
        
        // Short-circuit path (result is already false on stack)
        exprBuffer.append(String.format("%s:\n", shortCircuitLabel));
        
        exprBuffer.append(String.format("%s:\n", endLabel));
        
        return null;
    }
/**
     * Visit a binary logical OR expression node
     * 
     * @param node the binary logical OR expression node
     * @return result of the visit
     */
    public Object visit(BinaryLogicOrExpr node) {
        exprBuffer.setLength(0);
        
        String shortCircuitLabel = "L" + ++counter;
        String endLabel = "L" + ++counter;
        
        // Evaluate left side
        node.getLeftExpr().accept(this);
        
        // If left side is true, short-circuit
        exprBuffer.append("    dup\n");
        stackSize++;
        stack.add(stackSize);
        
        exprBuffer.append(String.format("    ifne %s\n", shortCircuitLabel));
        
        // Pop duplicated left value
        exprBuffer.append("    pop\n");
        stackSize--;
        stack.add(stackSize);
        
        // Evaluate right side
        node.getRightExpr().accept(this);
        
        // End of OR expression
        exprBuffer.append(String.format("    goto %s\n", endLabel));
        
        // Short-circuit path (result is already true on stack)
        exprBuffer.append(String.format("%s:\n", shortCircuitLabel));
        
        exprBuffer.append(String.format("%s:\n", endLabel));
        
        return null;
    }

    /**
     * Visit a unary negation expression node
     * 
     * @param node the unary negation expression node
     * @return result of the visit
     */
    public Object visit(UnaryNegExpr node) {
        exprBuffer.setLength(0);
        
        node.getExpr().accept(this);
        
        // Negate the value
        exprBuffer.append("    ineg\n");
        
        // Stack size doesn't change
        
        return null;
    }

    /**
     * Visit a unary NOT expression node
     * 
     * @param node the unary NOT expression node
     * @return result of the visit
     */
    public Object visit(UnaryNotExpr node) {
        exprBuffer.setLength(0);
        
        node.getExpr().accept(this);
        
        // NOT operation (flip 0 to 1, and anything else to 0)
        String trueLabel = "L" + ++counter;
        String endLabel = "L" + ++counter;
        
        exprBuffer.append(String.format("    ifeq %s\n", trueLabel));
        
        // If expression was not 0, result is 0 (false)
        exprBuffer.append("    iconst_0\n");
        exprBuffer.append(String.format("    goto %s\n", endLabel));
        
        // If expression was 0, result is 1 (true)
        exprBuffer.append(String.format("%s:\n", trueLabel));
        exprBuffer.append("    iconst_1\n");
        
        exprBuffer.append(String.format("%s:\n", endLabel));
        
        // Stack size doesn't change
        
        return null;
    }

    /**
     * Visit a unary increment expression node
     * 
     * @param node the unary increment expression node
     * @return result of the visit
     */
    public Object visit(UnaryIncrExpr node) {
        exprBuffer.setLength(0);
        
        VarExpr var = (VarExpr)node.getExpr();
        boolean isPrefix = !node.isPostfix();
        String varName = var.getName();
        
        // Check if it's a field or local variable
        if (var.getRef() != null) {
            // Field access
            exprBuffer.append("    aload_0\n"); // Load this reference
            stackSize++;
            stack.add(stackSize);
            
            exprBuffer.append("    dup\n"); // Duplicate this reference
            stackSize++;
            stack.add(stackSize);
            
            exprBuffer.append(String.format("    getfield %s/%s I\n", className, varName));
            stackSize--; // Pop object ref, push field value
            stack.add(stackSize);
            
            if (!isPrefix) {
                // Post-increment: duplicate value before incrementing
                exprBuffer.append("    dup\n");
                stackSize++;
                stack.add(stackSize);
            }
            
            // Increment value
            exprBuffer.append("    iconst_1\n");
            stackSize++;
            stack.add(stackSize);
            
            exprBuffer.append("    iadd\n");
            stackSize--; // Pop two values, push result
            stack.add(stackSize);
            
            if (isPrefix) {
                // Pre-increment: duplicate value after incrementing
                exprBuffer.append("    dup\n");
                stackSize++;
                stack.add(stackSize);
            }
            
            // Store back to field
            exprBuffer.append(String.format("    putfield %s/%s I\n", className, varName));
            stackSize -= 2; // Pop object ref and value
            stack.add(stackSize);
            
        } else {
            // Local variable
            int localIndex = getLocalIndex(varName); // Help me :(
            
            // Load current value
            exprBuffer.append(String.format("    iload %d\n", localIndex));
            stackSize++;
            stack.add(stackSize);
            
            if (!isPrefix) {
                // Post-increment: duplicate value before incrementing
                exprBuffer.append("    dup\n");
                stackSize++;
                stack.add(stackSize);
            }
            
            // Increment value
            exprBuffer.append("    iconst_1\n");
            stackSize++;
            stack.add(stackSize);
            
            exprBuffer.append("    iadd\n");
            stackSize--; // Pop two values, push result
            stack.add(stackSize);
            
            if (isPrefix) {
                // Pre-increment: duplicate value after incrementing
                exprBuffer.append("    dup\n");
                stackSize++;
                stack.add(stackSize);
            }
            
            // Store back to variable
            exprBuffer.append(String.format("    istore %d\n", localIndex));
            stackSize--; // Pop stored value
            stack.add(stackSize);
        }
        
        return null;
    }

    /**
     * Visit a unary decrement expression node
     * 
     * @param node the unary decrement expression node
     * @return result of the visit
     */
    public Object visit(UnaryDecrExpr node) {
        exprBuffer.setLength(0);
        
        VarExpr var = (VarExpr)node.getExpr();
        
        boolean isPrefix = !node.isPostfix();
        String varName = var.getName();
        
        // Check if it's a field or local variable
        if (var.getRef() != null) {
            // Field access
            exprBuffer.append("    aload_0\n"); // Load this reference
            stackSize++;
            stack.add(stackSize);
            
            exprBuffer.append("    dup\n"); // Duplicate this reference
            stackSize++;
            stack.add(stackSize);
            
            exprBuffer.append(String.format("    getfield %s/%s I\n", className, varName));
            stackSize--; // Pop object ref, push field value
            stack.add(stackSize);
            
            if (!isPrefix) {
                // Post-decrement: duplicate value before decrementing
                exprBuffer.append("    dup\n");
                stackSize++;
                stack.add(stackSize);
            }
            
            // Decrement value
            exprBuffer.append("    iconst_1\n");
            stackSize++;
            stack.add(stackSize);
            
            exprBuffer.append("    isub\n");
            stackSize--; // Pop two values, push result
            stack.add(stackSize);
            
            if (isPrefix) {
                // Pre-decrement: duplicate value after decrementing
                exprBuffer.append("    dup\n");
                stackSize++;
                stack.add(stackSize);
            }
            
            // Store back to field
            exprBuffer.append(String.format("    putfield %s/%s I\n", className, varName));
            stackSize -= 2; // Pop object ref and value
            stack.add(stackSize);
            
        } else {
            // Local variable
            int localIndex = getLocalIndex(varName); // Help me :(
            
            // Load current value
            exprBuffer.append(String.format("    iload %d\n", localIndex));
            stackSize++;
            stack.add(stackSize);
            
            if (!isPrefix) {
                // Post-decrement: duplicate value before decrementing
                exprBuffer.append("    dup\n");
                stackSize++;
                stack.add(stackSize);
            }
            
            // Decrement value
            exprBuffer.append("    iconst_1\n");
            stackSize++;
            stack.add(stackSize);
            
            exprBuffer.append("    isub\n");
            stackSize--; // Pop two values, push result
            stack.add(stackSize);
            
            if (isPrefix) {
                // Pre-decrement: duplicate value after decrementing
                exprBuffer.append("    dup\n");
                stackSize++;
                stack.add(stackSize);
            }
            
            // Store back to variable
            exprBuffer.append(String.format("    istore %d\n", localIndex));
            stackSize--; // Pop stored value
            stack.add(stackSize);
        }
        
        return null;
    }

    /**
     * Visit a variable expression node
     * 
     * @param node the variable expression node
     * @return result of the visit
     */
    public Object visit(VarExpr node) {
        String varName = node.getName();
        
        if (node.getRef() != null) {
            // Field access via reference
            node.getRef().accept(this);
            
            String refType = node.getRef().getExprType();
            String type = node.getExprType();
            
            exprBuffer.append(String.format("    getfield %s/%s %s\n", 
                refType, varName, builtInDescriptors(type)));
            
            // Stack size change: pop reference, push field value
            // No net change in stack size
            
        } else if (varName.equals("this")) {
            // Special case for 'this'
            exprBuffer.append("    aload_0\n");
            stackSize++;
            stack.add(stackSize);
            
        } else {
            // Check if it's a field or local variable
            Integer localIndex = getLocalIndex(varName);
            
            if (localIndex != null) {
                // Local variable
                String type = node.getExprType();
                
                if (type.equals("int") || type.equals("boolean")) {
                    exprBuffer.append(String.format("    iload %d\n", localIndex));
                } else {
                    exprBuffer.append(String.format("    aload %d\n", localIndex));
                }
                
                // Stack size increases by 1
                stackSize++;
                stack.add(stackSize);
                
            } else {
                // Field access (this.field)
                exprBuffer.append("    aload_0\n");
                stackSize++;
                stack.add(stackSize);
                
                String type = node.getExprType();
                exprBuffer.append(String.format("    getfield %s/%s %s\n", 
                    className, varName, builtInDescriptors(type)));
                
                // No net change in stack size (pop this, push field)
            }
        }
        
        return null;
    }

    /**
     * Visit an array expression node
     * 
     * @param node the array expression node
     * @return result of the visit
     */
    public Object visit(ArrayExpr node) {
        // Load array reference
        if (node.getRef() != null) {
            node.getRef().accept(this);
        } else {
            String arrayName = node.getName();
            Integer localIndex = getLocalIndex(arrayName);
            
            if (localIndex != null) {
                // Local variable array
                exprBuffer.append(String.format("    aload %d\n", localIndex));
            } else {
                // Field array
                exprBuffer.append("    aload_0\n");
                exprBuffer.append(String.format("    getfield %s/%s [%s\n", 
                    className, arrayName, builtInDescriptors(node.getExprType())));
            }
            
            stackSize++;
            stack.add(stackSize);
        }
        
        // Load index
        node.getIndex().accept(this);
        
        // Get array element
        String elementType = node.getExprType();
        if (elementType.equals("int") || elementType.equals("boolean")) {
            exprBuffer.append("    iaload\n");
        } else {
            exprBuffer.append("    aaload\n");
        }
        
        // Update stack size (pop array ref and index, push element)
        stackSize--;
        stack.add(stackSize);
        
        return null;
    }

    /**
     * Visit an int constant expression node
     * 
     * @param node the int constant expression node
     * @return result of the visit
     */
    public Object visit(ConstIntExpr node) {
        int value = Integer.parseInt(node.getConstant());
        
        // Use specialized bytecode for common constants
        if (value >= -1 && value <= 5) {
            exprBuffer.append(String.format("    iconst_%d\n", value));
        } else if (value >= -128 && value <= 127) {
            exprBuffer.append(String.format("    bipush %d\n", value));
        } else if (value >= -32768 && value <= 32767) {
            exprBuffer.append(String.format("    sipush %d\n", value));
        } else {
            exprBuffer.append(String.format("    ldc %d\n", value));
        }
        
        // Stack size increases by 1
        stackSize++;
        stack.add(stackSize);
        
        return null;
    }

    /**
     * Visit a boolean constant expression node
     * 
     * @param node the boolean constant expression node
     * @return result of the visit
     */
    public Object visit(ConstBooleanExpr node) {
        var value = node.getConstant();
        
        if (value.equals("true")) {
            exprBuffer.append("    iconst_1\n");
        } else {
            exprBuffer.append("    iconst_0\n");
        }
        
        // Stack size increases by 1
        stackSize++;
        stack.add(stackSize);
        
        return null;
    }

    /**
     * Visit a string constant expression node
     * 
     * @param node the string constant expression node
     * @return result of the visit
     */
    public Object visit(ConstStringExpr node) {
        stackSize++;
        stack.add(stackSize);
        exprBuffer.append(
            String.format("    ldc \"%s\"%n", node.getConstant())
        );
        return null;
    }
    /**
     * Helper method to get local variable index
     * 
     * @param varName the variable name
     * @return local variable index or null if not found
     */
    private Integer getLocalIndex(String varName) {
        // This should be implemented to look up variable names in a symbol table
        HashMap<String, Integer> localVars = new HashMap<>();
        
        // populate the map with variable names and their indices
        // From the formal parameters and local variable declarations
        
        return localVars.getOrDefault(varName, null);
    }

    public void writeToFile(String fileName) {
        sourceCode.append(fieldBuffer);
        sourceCode.append(methodBuffer);
        try (var pw = new PrintStream(new FileOutputStream(fileName), true)) {
            pw.print(sourceCode);
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        }
    }

    /**
     * Convert Java types to JVM descriptor format
     */
    public String descriptors(String type) {
        switch (type) {
            case "int":
                return "I";
            case "boolean":
                return "Z";
            case "void":
                return "V";
            default:
                String descriptor = type.endsWith("[]") ? 
                    String.format("[L%s;", type) : String.format("L%s;", type);
                return descriptor;
        }
    }

    /**
     * Handle built-in types for JVM descriptor format
     */
    public String builtInDescriptors(String type) {
        switch (type) {
            case "Object":
                return "java/lang/Object";
            case "String":
                return "Ljava/lang/String;";
            default:
                return descriptors(type);
        }
    }

    /**
     * Get full filename for built-in types
     */
    public String fullFileName(String type) {
        switch (type) {
            case "Object":
                return "java/lang/Object";
            // case "TextIO":
            //     return "TextIO";
            // case "Sys":
            //     return "Sys";
            case "String":
                return "java/lang/String";
            default: 
                return "";
        }
    }

    /**
     * Generate default constructor bytecode
     */
    public String defaultConstructor() {
        return String.format(
                ".method protected <init>()V%n" + 
                "    .limit stack 1%n" + 
                "    .limit locals 1%n" + 
                "    aload_0%n" + 
                "    invokespecial %s/<init>()V%n" + 
                "    return%n" + 
                ".end method%n%n", 
                builtInDescriptors(parentName));
    }

    /**
     * Generate boilerplate main method bytecode
     */
    public String boilerPlateMain() {
        return String.format(
            ".method static public main([Ljava/lang/String;)V%n" + 
            ".throws java/lang/CloneNotSupportedException%n" + 
            "    .limit stack 2%n" + 
            "    .limit locals 1%n" + 
            "    new %s%n" + 
            "    dup%n" + 
            "    invokespecial %s/<init>()V%n" + 
            "    invokevirtual %s/main()V%n" + 
            "    return%n" + 
            ".end method%n%n", 
            className, className, className);
    }

    /**
     * Get appropriate return bytecode prefix based on type
     */
    public String returnByteCodes(String type) {
        switch (type) {
            case "int":
            case "boolean":
                return "i";
            default:
                return "a";
        }
    }
    public int calculateStackHeight() {
        int max = 0;
        for (Integer integer : stack) {
            if (integer > max) {
                max = integer;
            }
        }
        return max;
    }
    public String getArgTypes() {
        String argList = "";
        for(String arg : args) {
            argList += builtInDescriptors(arg);
        }
        return argList;
    } 
}


