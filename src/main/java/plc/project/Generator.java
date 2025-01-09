package plc.project;

import java.io.PrintWriter;
import java.util.List;

public final class Generator implements Ast.Visitor<Void> {

    private final PrintWriter writer;
    private int indent = 0;

    public Generator(PrintWriter writer) {
        this.writer = writer;
    }

    private void print(Object... objects) {
        for (Object object : objects) {
            if (object instanceof Ast) {
                visit((Ast) object);
            } else {
                writer.write(object.toString());
            }
        }
    }

    private void newline(int indent) {
        writer.println();
        for (int i = 0; i < indent; i++) {
            writer.write("    ");
        }
    }

    @Override
    public Void visit(Ast.Source ast) {
        print("public class Main {");
        newline(0);
        newline(1);

        if (!ast.getFields().isEmpty()) {
            for (Ast.Field field : ast.getFields()) {
                print("    ");
                visit(field);
                newline(1);
            }
            newline(1);
        }

        print("    public static void main(String[] args) {");
        newline(2);
        print("        System.exit(new Main().main());");
        newline(1);
        print("    }");

        for (Ast.Method method : ast.getMethods()) {
            newline(0);
            newline(1);
            print("    ");
            indent = 1;
            visit(method);
            indent = 0;
        }

        newline(0);
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Field ast) {
        if (ast.getConstant()) {
            print("final ");
        }
        print(ast.getVariable().getType().getJvmName(), " ", ast.getVariable().getJvmName());
        if (ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Method ast) {
        print(ast.getFunction().getReturnType().getJvmName(), " ", ast.getFunction().getJvmName(), "(");
        for (int i = 0; i < ast.getParameters().size(); i++) {
            if (i > 0) {
                print(", ");
            }
            print(ast.getParameterTypeNames().get(i), " ", ast.getParameters().get(i));
        }
        print(") {");

        if (!ast.getStatements().isEmpty()) {
            for (Ast.Statement stmt : ast.getStatements()) {
                newline(indent + 1);
                visit(stmt);
            }
            newline(indent);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Expression ast) {
        print(ast.getExpression(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Declaration ast) {
        print(ast.getVariable().getType().getJvmName(), " ", ast.getVariable().getJvmName());
        if (ast.getValue().isPresent()) {
            print(" = ", ast.getValue().get());
        }
        print(";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Assignment ast) {
        print(ast.getReceiver(), " = ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.If ast) {
        print("if (", ast.getCondition(), ") {");
        if (!ast.getThenStatements().isEmpty()) {
            for (Ast.Statement stmt : ast.getThenStatements()) {
                newline(indent + 1);
                visit(stmt);
            }
            newline(indent);
        }
        print("}");

        if (!ast.getElseStatements().isEmpty()) {
            print(" else {");
            for (Ast.Statement stmt : ast.getElseStatements()) {
                newline(indent + 1);
                visit(stmt);
            }
            newline(indent);
            print("}");
        }
        return null;
    }

    @Override
    public Void visit(Ast.Statement.For ast) {
        print("for (");

        if (ast.getInitialization() != null) {
            print(" ");
            if (ast.getInitialization() instanceof Ast.Statement.Declaration) {
                Ast.Statement.Declaration decl = (Ast.Statement.Declaration) ast.getInitialization();
                print(decl.getVariable().getType().getJvmName(), " ", decl.getVariable().getJvmName());
                decl.getValue().ifPresent(value -> {
                    print(" = ", value);
                });
            } else if (ast.getInitialization() instanceof Ast.Statement.Assignment) {
                Ast.Statement.Assignment assign = (Ast.Statement.Assignment) ast.getInitialization();
                print(assign.getReceiver(), " = ", assign.getValue());
            }
            print(";");
        } else {
            print(";");
        }

        if (ast.getCondition() != null) {
            print(" ", ast.getCondition());
        }
        print(";");

        if (ast.getIncrement() != null) {
            print(" ");
            if (ast.getIncrement() instanceof Ast.Statement.Assignment) {
                Ast.Statement.Assignment assign = (Ast.Statement.Assignment) ast.getIncrement();
                print(assign.getReceiver(), " = ", assign.getValue());
            }
        }

        print(" ) {");

        for (Ast.Statement stmt : ast.getStatements()) {
            newline(indent + 1);
            visit(stmt);
        }
        newline(indent);
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.While ast) {
        print("while (", ast.getCondition(), ") {");

        if (!ast.getStatements().isEmpty()) {
            for (Ast.Statement stmt : ast.getStatements()) {
                newline(indent + 1);
                visit(stmt);
            }
            newline(indent);
        }
        print("}");
        return null;
    }

    @Override
    public Void visit(Ast.Statement.Return ast) {
        print("return ", ast.getValue(), ";");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Literal ast) {
        if (ast.getLiteral() instanceof String) {
            print("\"", ast.getLiteral(), "\"");
        } else if (ast.getLiteral() == Boolean.TRUE) {
            print("true");
        } else if (ast.getLiteral() == Boolean.FALSE) {
            print("false");
        } else {
            print(ast.getLiteral());
        }
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Group ast) {
        print("(", ast.getExpression(), ")");
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Binary ast) {
        print(ast.getLeft(), " ", ast.getOperator(), " ", ast.getRight());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Access ast) {
        print(ast.getVariable().getJvmName());
        return null;
    }

    @Override
    public Void visit(Ast.Expression.Function ast) {
        print(ast.getFunction().getJvmName(), "(");
        for (int i = 0; i < ast.getArguments().size(); i++) {
            if (i > 0) {
                print(", ");
            }
            print(ast.getArguments().get(i));
        }
        print(")");
        return null;
    }
}
