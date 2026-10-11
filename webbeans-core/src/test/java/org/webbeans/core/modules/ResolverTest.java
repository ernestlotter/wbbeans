package org.webbeans.core.modules;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.webbeans.core.modules.TestModules.info;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.Test;

public class ResolverTest {

    private static Resolver resolver(String... tokens) {
        return new Resolver(new HashSet<String>(Arrays.asList(tokens)));
    }

    private static List<String> names(List<ModuleInfo> list) {
        List<String> names = new ArrayList<String>();
        for (ModuleInfo m : list) {
            names.add(m.getCodeName());
        }
        return names;
    }

    @Test
    public void ordersDependenciesFirstAndTiesByName() {
        ModuleInfo a = info("org.a", "1.0");
        ModuleInfo b = info("org.b", "1.0", ModuleInfo.ATTR_DEPENDENCIES, "org.a > 1.0");
        ModuleInfo c = info("org.c", "1.0", ModuleInfo.ATTR_DEPENDENCIES, "org.b, org.a");
        ModuleInfo z = info("org.z", "1.0");
        Resolver.Resolution r = resolver().resolve(Arrays.asList(c, z, b, a));
        // a first (dependency); then the ready set {b, z} is drained by name
        assertEquals(Arrays.asList("org.a", "org.b", "org.c", "org.z"), names(r.getEnabled()));
        assertTrue(r.getProblems().isEmpty());
    }

    @Test
    public void missingDependencyDisablesModuleAndDependents() {
        ModuleInfo b = info("org.b", "1.0", ModuleInfo.ATTR_DEPENDENCIES, "org.missing");
        ModuleInfo c = info("org.c", "1.0", ModuleInfo.ATTR_DEPENDENCIES, "org.b");
        Resolver.Resolution r = resolver().resolve(Arrays.asList(b, c));
        assertTrue(r.getEnabled().isEmpty());
        assertEquals(Problem.Kind.MISSING_DEPENDENCY, r.getProblems().get(b).getKind());
        assertEquals(Problem.Kind.DEPENDENCY_DISABLED, r.getProblems().get(c).getKind());
    }

    @Test
    public void versionMatching() {
        ModuleInfo a = info("org.a", "1.4.2");
        ModuleInfo ok1 = info("org.ok1", "1.0", ModuleInfo.ATTR_DEPENDENCIES, "org.a > 1.2");
        ModuleInfo ok2 = info("org.ok2", "1.0", ModuleInfo.ATTR_DEPENDENCIES, "org.a = 1.4.2");
        ModuleInfo tooNew = info("org.toonew", "1.0", ModuleInfo.ATTR_DEPENDENCIES, "org.a > 1.5");
        ModuleInfo major = info("org.major", "1.0", ModuleInfo.ATTR_DEPENDENCIES, "org.a > 0.9");
        ModuleInfo exact = info("org.exact", "1.0", ModuleInfo.ATTR_DEPENDENCIES, "org.a = 1.4");
        Resolver.Resolution r = resolver().resolve(Arrays.asList(a, ok1, ok2, tooNew, major, exact));
        assertEquals(Arrays.asList("org.a", "org.ok1", "org.ok2"), names(r.getEnabled()));
        assertEquals(Problem.Kind.VERSION_MISMATCH, r.getProblems().get(tooNew).getKind());
        assertEquals(Problem.Kind.VERSION_MISMATCH, r.getProblems().get(major).getKind());
        assertEquals(Problem.Kind.VERSION_MISMATCH, r.getProblems().get(exact).getKind());
    }

    @Test
    public void cyclesDisableAllMembers() {
        ModuleInfo a = info("org.a", "1.0", ModuleInfo.ATTR_DEPENDENCIES, "org.b");
        ModuleInfo b = info("org.b", "1.0", ModuleInfo.ATTR_DEPENDENCIES, "org.c");
        ModuleInfo c = info("org.c", "1.0", ModuleInfo.ATTR_DEPENDENCIES, "org.a");
        ModuleInfo d = info("org.d", "1.0", ModuleInfo.ATTR_DEPENDENCIES, "org.a");
        ModuleInfo e = info("org.e", "1.0");
        Resolver.Resolution r = resolver().resolve(Arrays.asList(a, b, c, d, e));
        assertEquals(Collections.singletonList("org.e"), names(r.getEnabled()));
        assertEquals(Problem.Kind.CYCLE, r.getProblems().get(a).getKind());
        assertEquals(Problem.Kind.CYCLE, r.getProblems().get(b).getKind());
        assertEquals(Problem.Kind.CYCLE, r.getProblems().get(c).getKind());
        assertEquals(Problem.Kind.DEPENDENCY_DISABLED, r.getProblems().get(d).getKind());
    }

    @Test
    public void duplicatesHigherVersionWins() {
        ModuleInfo old = info("org.a", "1.0");
        ModuleInfo neu = info("org.a", "1.1");
        ModuleInfo user = info("org.b", "1.0", ModuleInfo.ATTR_DEPENDENCIES, "org.a > 1.1");
        Resolver.Resolution r = resolver().resolve(Arrays.asList(old, user, neu));
        assertEquals(Arrays.asList("org.a", "org.b"), names(r.getEnabled()));
        assertTrue(r.getEnabled().contains(neu));
        assertEquals(Problem.Kind.DUPLICATE, r.getProblems().get(old).getKind());
    }

    @Test
    public void autoloadOnlyWhenNeeded() {
        ModuleInfo lib = info("org.lib", "1.0", ModuleInfo.ATTR_AUTOLOAD, "true");
        ModuleInfo unused = info("org.unused", "1.0", ModuleInfo.ATTR_AUTOLOAD, "true");
        ModuleInfo app = info("org.app", "1.0", ModuleInfo.ATTR_DEPENDENCIES, "org.lib");
        Resolver.Resolution r = resolver().resolve(Arrays.asList(lib, unused, app));
        assertEquals(Arrays.asList("org.lib", "org.app"), names(r.getEnabled()));
        assertEquals(Collections.singletonList(unused), r.getNotWanted());
        assertNull(r.getProblems().get(unused));
    }

    @Test
    public void autoloadChainCollapsesWhenDependentFails() {
        ModuleInfo lib = info("org.lib", "1.0", ModuleInfo.ATTR_AUTOLOAD, "true");
        ModuleInfo app = info("org.app", "1.0", ModuleInfo.ATTR_DEPENDENCIES, "org.lib, org.missing");
        Resolver.Resolution r = resolver().resolve(Arrays.asList(lib, app));
        assertTrue(r.getEnabled().isEmpty());
        assertEquals(Collections.singletonList(lib), r.getNotWanted());
        assertEquals(Problem.Kind.MISSING_DEPENDENCY, r.getProblems().get(app).getKind());
    }

    @Test
    public void eagerIsEnabledWithoutDependents() {
        ModuleInfo eager = info("org.eager", "1.0", ModuleInfo.ATTR_AUTOLOAD, "true", ModuleInfo.ATTR_EAGER, "true");
        Resolver.Resolution r = resolver().resolve(Collections.singletonList(eager));
        assertEquals(Collections.singletonList("org.eager"), names(r.getEnabled()));
    }

    @Test
    public void requiresTokensFromPlatformAndModules() {
        ModuleInfo db = info("org.db", "1.0", ModuleInfo.ATTR_PROVIDES, "javax.sql");
        ModuleInfo usesDb = info("org.usesdb", "1.0", ModuleInfo.ATTR_REQUIRES, "javax.sql");
        ModuleInfo usesUi = info("org.usesui", "1.0", ModuleInfo.ATTR_REQUIRES, "org.webbeans.ui");
        ModuleInfo usesNothing = info("org.nope", "1.0", ModuleInfo.ATTR_REQUIRES, "org.nonexistent");
        Resolver.Resolution r = resolver("org.webbeans.ui").resolve(Arrays.asList(db, usesDb, usesUi, usesNothing));
        assertEquals(Arrays.asList("org.db", "org.usesdb", "org.usesui"), names(r.getEnabled()));
        assertEquals(Problem.Kind.MISSING_REQUIRES, r.getProblems().get(usesNothing).getKind());
    }

    @Test
    public void requiresProvidedByDisabledModuleIsMissing() {
        ModuleInfo db = info("org.db", "1.0", ModuleInfo.ATTR_PROVIDES, "javax.sql", ModuleInfo.ATTR_DEPENDENCIES, "org.missing");
        ModuleInfo usesDb = info("org.usesdb", "1.0", ModuleInfo.ATTR_REQUIRES, "javax.sql");
        Resolver.Resolution r = resolver().resolve(Arrays.asList(db, usesDb));
        assertTrue(r.getEnabled().isEmpty());
        assertEquals(Problem.Kind.MISSING_REQUIRES, r.getProblems().get(usesDb).getKind());
    }

    @Test
    public void specificationVersionSemantics() {
        assertEquals(new SpecificationVersion("1.2"), new SpecificationVersion("1.2.0"));
        assertEquals(new SpecificationVersion("1.2").hashCode(), new SpecificationVersion("1.2.0").hashCode());
        assertTrue(new SpecificationVersion("1.10").compareTo(new SpecificationVersion("1.9")) > 0);
        assertFalse(new Dependency(Dependency.Type.MODULE, "x", Dependency.Comparison.AT_LEAST, new SpecificationVersion("1.0"))
                .isSatisfiedBy(new SpecificationVersion("2.0")));
    }
}
