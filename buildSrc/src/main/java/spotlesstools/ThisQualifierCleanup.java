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

/**
 * Strips {@code this.} from field accesses that no local name shadows, the other half of
 * Checkstyle's {@code RequireThis} with {@code validateOnlyOverlapping}.
 *
 * <p>Checkstyle demands {@code this.} where a field is shadowed; this removes it everywhere else,
 * so the qualifier appears exactly where it disambiguates. It runs as the {@code
 * removeRedundantThis} Spotless custom step before palantir, so {@code spotlessApply} applies it
 * and {@code spotlessCheck} enforces it.
 *
 * <p>The pass works on the JavaParser AST because {@code this.x = x} in a constructor must keep its
 * qualifier, and only scope analysis tells the two cases apart. {@link LexicalPreservingPrinter}
 * reprints only the nodes that changed, leaving formatting to palantir.
 *
 * @see <a href="https://checkstyle.sourceforge.io/checks/coding/requirethis.html">Checkstyle:
 *     RequireThis</a>
 * @see <a href="https://javaparser.org/">JavaParser</a>
 */
public final class ThisQualifierCleanup {
    private ThisQualifierCleanup() {}

    /**
     * Returns {@code source} with every unshadowed {@code this.field} reduced to {@code field}.
     * {@code Outer.this.field} is left alone.
     */
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

    /**
     * Whether the enclosing method or constructor declares {@code name} anywhere: as a parameter,
     * local, lambda or catch parameter, or pattern binding.
     *
     * <p>Deliberately coarse. It ignores block scope, so a name declared in any branch keeps every
     * {@code this.} in the method. A kept redundant qualifier costs nothing; a removed required one
     * changes which variable the code writes.
     */
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
