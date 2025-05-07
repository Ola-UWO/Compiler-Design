package codegenjvm;

import java.util.*;

import util.*;

public class JVMCodeGenerator {
    /** Root of the class hierarchy tree */
    private ClassTreeNode root;

    /** Boolean indicating whether debugging is enabled */
    private boolean debug = false;

    public JVMCodeGenerator(ClassTreeNode root, boolean debug) {
        this.root = root;
        this.debug = debug;
    }

    public void generate() {
        CodeGenVisitor codeGenVisitor = new CodeGenVisitor();
        LinkedList<ClassTreeNode> temp = new LinkedList<ClassTreeNode>();
        temp.addFirst(root);
        while (!temp.isEmpty()) {
            var curr = temp.removeFirst();
            if (!curr.isBuiltIn()) codeGenVisitor.visit(curr.getASTNode());
            var iter = curr.getChildrenList();
            while (iter.hasNext()) {
                temp.addLast(iter.next());
            }
        }
    }
}
