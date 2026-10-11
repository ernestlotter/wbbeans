package org.webbeans.core.modules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Computes which modules can be enabled and in what order. Pure function of the module descriptors;
 * never throws for a bad module, it reports a {@link Problem} instead.
 *
 * <p>Rules:
 * <ul>
 *   <li>Duplicate code names: the highest version wins, the others are {@link Problem.Kind#DUPLICATE}.</li>
 *   <li>Members of dependency cycles are {@link Problem.Kind#CYCLE}.</li>
 *   <li>The enabled set is the largest set closed under module dependencies and requires tokens
 *       (iterated to a fixed point).</li>
 *   <li>Autoload modules are enabled only if an enabled module depends on them; eager modules whenever satisfiable.</li>
 *   <li>Order is a topological sort (dependencies first), ties broken by code name.</li>
 * </ul>
 */
public final class Resolver {

    /** The outcome of a resolution. */
    public static final class Resolution {
        private final List<ModuleInfo> enabled;
        private final Map<ModuleInfo, Problem> problems;
        private final List<ModuleInfo> notWanted;

        Resolution(List<ModuleInfo> enabled, Map<ModuleInfo, Problem> problems, List<ModuleInfo> notWanted) {
            this.enabled = Collections.unmodifiableList(enabled);
            this.problems = Collections.unmodifiableMap(problems);
            this.notWanted = Collections.unmodifiableList(notWanted);
        }

        /** Modules to enable, dependencies first. */
        public List<ModuleInfo> getEnabled() {
            return enabled;
        }

        /** Modules that could not be enabled, with the reason. */
        public Map<ModuleInfo, Problem> getProblems() {
            return problems;
        }

        /** Autoload modules nothing depends on: not enabled, not a problem. */
        public List<ModuleInfo> getNotWanted() {
            return notWanted;
        }
    }

    private final Set<String> platformTokens;

    /** @param platformTokens capability tokens provided by the platform itself (e.g. {@code org.webbeans.ui}) */
    public Resolver(Set<String> platformTokens) {
        this.platformTokens = new HashSet<String>(platformTokens);
    }

    public Resolution resolve(Collection<ModuleInfo> candidates) {
        Map<ModuleInfo, Problem> problems = new LinkedHashMap<ModuleInfo, Problem>();

        // 1. duplicates: keep the highest version per code name
        Map<String, ModuleInfo> byName = new TreeMap<String, ModuleInfo>();
        for (ModuleInfo m : candidates) {
            ModuleInfo other = byName.get(m.getCodeName());
            if (other == null) {
                byName.put(m.getCodeName(), m);
            } else if (m.getVersion().compareTo(other.getVersion()) > 0) {
                problems.put(other, new Problem(Problem.Kind.DUPLICATE,
                        "Superseded by " + m + (m.getLocation() != null ? " at " + m.getLocation() : "")));
                byName.put(m.getCodeName(), m);
            } else {
                problems.put(m, new Problem(Problem.Kind.DUPLICATE,
                        "Superseded by " + other + (other.getLocation() != null ? " at " + other.getLocation() : "")));
            }
        }

        // 2. cycles (over module dependencies whose target exists)
        for (ModuleInfo m : findCycleMembers(byName)) {
            problems.put(m, new Problem(Problem.Kind.CYCLE, "Part of a dependency cycle"));
        }

        // 3. fixed point
        Set<ModuleInfo> enabled = new LinkedHashSet<ModuleInfo>();
        for (ModuleInfo m : byName.values()) {
            if (!problems.containsKey(m)) {
                enabled.add(m);
            }
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            Set<String> tokens = new HashSet<String>(platformTokens);
            for (ModuleInfo m : enabled) {
                tokens.addAll(m.getProvides());
            }
            Set<ModuleInfo> wanted = wanted(enabled);
            for (ModuleInfo m : new ArrayList<ModuleInfo>(enabled)) {
                if (!wanted.contains(m)) {
                    enabled.remove(m);
                    changed = true;
                    continue;
                }
                Problem p = check(m, byName, enabled, tokens);
                if (p != null) {
                    problems.put(m, p);
                    enabled.remove(m);
                    changed = true;
                }
            }
        }

        // 4. autoload modules nobody wanted
        List<ModuleInfo> notWanted = new ArrayList<ModuleInfo>();
        for (ModuleInfo m : byName.values()) {
            if (!enabled.contains(m) && !problems.containsKey(m)) {
                notWanted.add(m);
            }
        }

        // 5. order
        return new Resolution(topologicalOrder(enabled, byName), problems, notWanted);
    }

    /** Non-autoload (and eager) modules, plus autoload modules some wanted module depends on, transitively. */
    private Set<ModuleInfo> wanted(Set<ModuleInfo> enabled) {
        Map<String, ModuleInfo> enabledByName = new HashMap<String, ModuleInfo>();
        for (ModuleInfo m : enabled) {
            enabledByName.put(m.getCodeName(), m);
        }
        Set<ModuleInfo> wanted = new LinkedHashSet<ModuleInfo>();
        List<ModuleInfo> queue = new ArrayList<ModuleInfo>();
        for (ModuleInfo m : enabled) {
            if (!m.isAutoload() || m.isEager()) {
                wanted.add(m);
                queue.add(m);
            }
        }
        while (!queue.isEmpty()) {
            ModuleInfo m = queue.remove(queue.size() - 1);
            for (Dependency d : m.getDependencies()) {
                if (d.getType() != Dependency.Type.MODULE) {
                    continue;
                }
                ModuleInfo target = enabledByName.get(d.getName());
                if (target != null && wanted.add(target)) {
                    queue.add(target);
                }
            }
        }
        return wanted;
    }

    private Problem check(ModuleInfo m, Map<String, ModuleInfo> byName, Set<ModuleInfo> enabled, Set<String> tokens) {
        for (Dependency d : m.getDependencies()) {
            if (d.getType() == Dependency.Type.REQUIRES) {
                if (!tokens.contains(d.getName())) {
                    return new Problem(Problem.Kind.MISSING_REQUIRES, "Requires '" + d.getName() + "' which nothing provides");
                }
                continue;
            }
            ModuleInfo target = byName.get(d.getName());
            if (target == null) {
                return new Problem(Problem.Kind.MISSING_DEPENDENCY, "Depends on missing module " + d);
            }
            if (!d.isSatisfiedBy(target.getVersion())) {
                return new Problem(Problem.Kind.VERSION_MISMATCH,
                        "Depends on " + d + " but found version " + target.getVersion());
            }
            if (!enabled.contains(target)) {
                Problem tp = target.getProblem();
                return new Problem(Problem.Kind.DEPENDENCY_DISABLED,
                        "Depends on " + target.getCodeName() + " which is disabled" + (tp != null ? " (" + tp + ")" : ""));
            }
        }
        return null;
    }

    private static Set<ModuleInfo> findCycleMembers(Map<String, ModuleInfo> byName) {
        // Tarjan's SCC; members of an SCC with size > 1 (self-loops are rejected at parse time) are cycle members
        Set<ModuleInfo> result = new LinkedHashSet<ModuleInfo>();
        Map<ModuleInfo, Integer> index = new HashMap<ModuleInfo, Integer>();
        Map<ModuleInfo, Integer> low = new HashMap<ModuleInfo, Integer>();
        List<ModuleInfo> stack = new ArrayList<ModuleInfo>();
        Set<ModuleInfo> onStack = new HashSet<ModuleInfo>();
        int[] counter = {0};
        for (ModuleInfo m : byName.values()) {
            if (!index.containsKey(m)) {
                tarjan(m, byName, index, low, stack, onStack, counter, result);
            }
        }
        return result;
    }

    private static void tarjan(ModuleInfo v, Map<String, ModuleInfo> byName, Map<ModuleInfo, Integer> index,
            Map<ModuleInfo, Integer> low, List<ModuleInfo> stack, Set<ModuleInfo> onStack, int[] counter,
            Set<ModuleInfo> result) {
        index.put(v, counter[0]);
        low.put(v, counter[0]);
        counter[0]++;
        stack.add(v);
        onStack.add(v);
        for (Dependency d : v.getDependencies()) {
            if (d.getType() != Dependency.Type.MODULE) {
                continue;
            }
            ModuleInfo w = byName.get(d.getName());
            if (w == null) {
                continue;
            }
            if (!index.containsKey(w)) {
                tarjan(w, byName, index, low, stack, onStack, counter, result);
                low.put(v, Math.min(low.get(v), low.get(w)));
            } else if (onStack.contains(w)) {
                low.put(v, Math.min(low.get(v), index.get(w)));
            }
        }
        if (low.get(v).equals(index.get(v))) {
            List<ModuleInfo> scc = new ArrayList<ModuleInfo>();
            ModuleInfo w;
            do {
                w = stack.remove(stack.size() - 1);
                onStack.remove(w);
                scc.add(w);
            } while (w != v);
            if (scc.size() > 1) {
                result.addAll(scc);
            }
        }
    }

    private static List<ModuleInfo> topologicalOrder(Set<ModuleInfo> enabled, Map<String, ModuleInfo> byName) {
        Map<ModuleInfo, Integer> inDegree = new HashMap<ModuleInfo, Integer>();
        Map<ModuleInfo, List<ModuleInfo>> dependents = new HashMap<ModuleInfo, List<ModuleInfo>>();
        for (ModuleInfo m : enabled) {
            inDegree.put(m, 0);
            dependents.put(m, new ArrayList<ModuleInfo>());
        }
        for (ModuleInfo m : enabled) {
            for (Dependency d : m.getDependencies()) {
                if (d.getType() != Dependency.Type.MODULE) {
                    continue;
                }
                ModuleInfo target = byName.get(d.getName());
                if (target != null && enabled.contains(target)) {
                    inDegree.put(m, inDegree.get(m) + 1);
                    dependents.get(target).add(m);
                }
            }
        }
        TreeSet<ModuleInfo> ready = new TreeSet<ModuleInfo>(ByCodeName.INSTANCE);
        for (Map.Entry<ModuleInfo, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) {
                ready.add(e.getKey());
            }
        }
        List<ModuleInfo> order = new ArrayList<ModuleInfo>();
        while (!ready.isEmpty()) {
            ModuleInfo m = ready.pollFirst();
            order.add(m);
            for (ModuleInfo dep : dependents.get(m)) {
                int n = inDegree.get(dep) - 1;
                inDegree.put(dep, n);
                if (n == 0) {
                    ready.add(dep);
                }
            }
        }
        if (order.size() != enabled.size()) {
            throw new IllegalStateException("Cycle survived cycle detection"); // cannot happen
        }
        return order;
    }

    private enum ByCodeName implements java.util.Comparator<ModuleInfo> {
        INSTANCE;

        @Override
        public int compare(ModuleInfo a, ModuleInfo b) {
            return a.getCodeName().compareTo(b.getCodeName());
        }
    }
}
