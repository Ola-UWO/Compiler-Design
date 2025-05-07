package codegenjvm;

import java.util.*;
import ast.*;
import util.*;
import visitor.*;
import java.io.*;

public class CodeGenVisitor extends Visitor {

    StringBuffer sourceCode = new StringBuffer();
    StringBuffer methodBuffer = new StringBuffer();
    StringBuffer stmtBuffer = new StringBuffer();
    StringBuffer fieldBuffer = new StringBuffer();
    boolean hasConstructor = false;
    boolean hasReturn = false;
    int stack = 0;
    int locals = 1;
    String className;

    /**
     * Visit a class node
     * 
     * @param node the class node
     * @return result of the visit
     */
    public Object visit(Class_ node) {
        sourceCode.setLength(0); // reset for new class
        methodBuffer.setLength(0);
        fieldBuffer.setLength(0);
        String className = node.getName();
        // header
        sourceCode.append(
                String.format(".source %s.btm%n.class protected %s%n.super %s%n.implements java/lang/Cloneable%n%n",
                        className, className, builtInDescriptors(node.getParent())));
        node.getMemberList().accept(this);
        writeToFile(className + ".j");
        return null;
    }

    /**
     * Visit a list node of members
     * 
     * @param node the member list node
     * @return result of the visit
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
     * 
     * @param node the field node
     * @return result of the visit
     */
    public Object visit(Field node) {

        fieldBuffer.append(
                String.format(".field protected %s %s%n", node.getName(), builtInDescriptors(node.getType())));
        if (node.getInit() != null)
            node.getInit().accept(this);
        return null;
    }

    /**
     * Visit a method node
     * 
     * @param node the method node
     * @return result of the visit
     */
    public Object visit(Method node) {
        // header
        // node.
        if (!hasConstructor) { // subclassing user def classes not implemented yet
            methodBuffer.append(defualtConstructor());
        }
        methodBuffer.append(boilerPlateMain());
        methodBuffer.append(
                String.format(".method protected %s(", node.getName()));
        node.getFormalList().accept(this);
        methodBuffer.append(
                String.format(")%s%n", builtInDescriptors(node.getReturnType())));
        node.getStmtList().accept(this);
        methodBuffer.append(String.format("    .limit stack %d%n    .limit locals %d%n", stack, locals)); 
        stack = 0; // reset counters 
        locals = 0;
        methodBuffer.append(stmtBuffer);
        if (!hasReturn) {
            methodBuffer.append(String.format("    return%n"));
            hasReturn = false;
        }
        methodBuffer.append(String.format(".end method%n%n"));
        return null;
    }

    /**
     * Visit a list node of formals
     * 
     * @param node the formal list node
     * @return result of the visit
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
     * 
     * @param node the statement list node
     * @return result of the visit
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
     * 
     * @param node the declaration statement node
     * @return result of the visit
     */
    public Object visit(DeclStmt node) {
        node.getInit().accept(this);
        return null;
    }

    /**
     * Visit an expression statement node
     * 
     * @param node the expression statement node
     * @return result of the visit
     */
    public Object visit(ExprStmt node) {
        node.getExpr().accept(this);
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
        if (node.getPredExpr() != null)
            node.getPredExpr().accept(this);
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
        node.getStmtList().accept(this);
        return null;
    }

    /**
     * Visit a return statement node
     * 
     * @param node the return statement node
     * @return result of the visit
     */
    public Object visit(ReturnStmt node) {
        if (node.getExpr() != null) {
            var expr = node.getExpr();
            expr.accept(this);
            var exprType = expr.getExprType();
            stmtBuffer.append(String.format("    .%s", returnByteCodes(exprType)));
        }
        stmtBuffer.append(String.format("    return%n"));
        return null;
    }

    /**
     * Visit a list node of expressions
     * 
     * @param node the expression list node
     * @return result of the visit
     */
    public Object visit(ExprList node) {
        for (Iterator it = node.getIterator(); it.hasNext();)
            ((Expr) it.next()).accept(this);
        return null;
    }

    /**
     * Visit a dispatch expression node
     * 
     * @param node the dispatch expression node
     * @return result of the visit
     */
    public Object visit(DispatchExpr node) {
        node.getRefExpr().accept(this);
        node.getActualList().accept(this);
        return null;
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
        node.getExpr().accept(this);
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
        node.getExpr().accept(this);
        return null;
    }

    /**
     * Visit a unary NOT expression node
     * 
     * @param node the unary NOT expression node
     * @return result of the visit
     */
    public Object visit(UnaryNotExpr node) {
        node.getExpr().accept(this);
        return null;
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
     * Visit a variable expression node
     * 
     * @param node the variable expression node
     * @return result of the visit
     */
    public Object visit(VarExpr node) {
        if (node.getRef() != null)
            node.getRef().accept(this);
        return null;
    }

    /**
     * Visit an array expression node
     * 
     * @param node the array expression node
     * @return result of the visit
     */
    public Object visit(ArrayExpr node) {
        if (node.getRef() != null)
            node.getRef().accept(this);
        node.getIndex().accept(this);
        return null;
    }

    /**
     * Visit an int constant expression node
     * 
     * @param node the int constant expression node
     * @return result of the visit
     */
    public Object visit(ConstIntExpr node) {
        return null;
    }

    /**
     * Visit a boolean constant expression node
     * 
     * @param node the boolean constant expression node
     * @return result of the visit
     */
    public Object visit(ConstBooleanExpr node) {
        return null;
    }

    /**
     * Visit a string constant expression node
     * 
     * @param node the string constant expression node
     * @return result of the visit
     */
    public Object visit(ConstStringExpr node) {
        return null;
    }

    public void writeToFile(String fileName) {
        // fileName =  "/Users/ola/Downloads/a5/src/codegenjvm/" + fileName;
        sourceCode.append(fieldBuffer);
        sourceCode.append(methodBuffer);
        try (var pw = new PrintStream(new FileOutputStream(fileName), true)) {
            pw.print(sourceCode);
        } catch (FileNotFoundException fnfe) {
            fnfe.printStackTrace();
        }
    }

    public String descriptors(String type) {
        switch (type) {
            case "int":
                return "I";
            case "boolean":
                return "Z";
            case "void":
                return "V";
            default:
                String descriptor = type.endsWith("[]") ? String.format("[L%s;", type) : String.format("L%s;", type);
                return descriptor;
        }
    }

    public String builtInDescriptors(String type) {
        switch (type) {
            case "Object":
                return "java/lang/Object";
            case "TextIO":
                return "java/io/TextIO";
            case "Sys":
                return "java/lang/Sys";
            case "String":
                return "java/lang/String";
            default:
                return descriptors(type);
        }
    }

    public String defualtConstructor() {
        return String.format(
                ".method protected <init>()V%n    .limit stack 1%n    .limit locals 1%n    aload_0%n    invokespecial java/lang/Object/<init>()V%n    return%n.end method%n%n");
    }

    public String boilerPlateMain() {
        return String.format(
            ".method static public main([Ljava/lang/String;)V%n    .limit stack 2%n    .limit locals 2%n    new Main%n    dup%n    invokespecial Main/<init>()V%n    invokevirtual Main/main()V%n    return%n.end method%n%n");
    }
    public String returnByteCodes(String type) {
        switch (type) {
            case "int":
            case "boolean":
                return "i";
            default:
                return "a";
        }
    }
}
