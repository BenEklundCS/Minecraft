package spotlesstools;

import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.TypePatternExpr;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

// Strips redundant `this.` field qualifiers so the codebase matches the Checkstyle convention
// (this. only when a field is actually shadowed). Runs as a Spotless custom step, so it's applied
// by spotlessApply and enforced by spotlessCheck like any other formatter rule.
//
// AST-based on purpose: `this.x = x` in a constructor MUST keep its `this.`, and only scope
// analysis can tell a redundant qualifier from a required one. A text/regex pass cannot.
public final class ThisQualifierCleanup {
    private ThisQualifierCleanup() {}

    public static String clean(String source) {
        StaticJavaParser.getParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        CompilationUnit cu = StaticJavaParser.parse(source);
        LexicalPreservingPrinter.setup(cu); // preserve original whitespace; only touch what we edit

        for (FieldAccessExpr access : cu.findAll(FieldAccessExpr.class)) {
            if (!(access.getScope() instanceof ThisExpr thisExpr)) continue;
            if (thisExpr.getTypeName().isPresent()) continue; // qualified `Outer.this.x` — leave alone
            String name = access.getNameAsString();
            if (shadowed(access, name)) continue; // a local/param/pattern needs the qualifier — keep it
            access.replace(new NameExpr(name));
        }
        return LexicalPreservingPrinter.print(cu);
    }

    // Conservative: if the enclosing method/constructor declares this name anywhere — as a
    // parameter, local, lambda/catch param, or pattern binding — keep `this.`. Over-keeping a
    // qualifier is harmless; wrongly removing a required one is not, so we err toward keeping.
    private static boolean shadowed(Node node, String name) {
        Optional<CallableDeclaration> callable = node.findAncestor(CallableDeclaration.class);
        if (callable.isEmpty()) return false; // field initializer / no local scope — safe to strip
        CallableDeclaration<?> scope = callable.get();
        Set<String> declared = new HashSet<>();
        scope.findAll(Parameter.class).forEach(p -> declared.add(p.getNameAsString()));
        scope.findAll(VariableDeclarator.class).forEach(v -> declared.add(v.getNameAsString()));
        scope.findAll(TypePatternExpr.class).forEach(p -> declared.add(p.getNameAsString()));
        return declared.contains(name);
    }
}
