package eu.amadeos.apps.familytree.service;

import eu.amadeos.apps.familytree.domain.Family;
import eu.amadeos.apps.familytree.domain.FamilyTree;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.gedcom.GedcomNode;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Undo and redo for a family tree. A change starts a {@link Step} and names the records it is going to change
 * ({@link Step#touch}); the step keeps copies of them. Records that are new when the step ends are noted as
 * "not there before", so undo removes them again. Only the records a change touched are copied, so this stays
 * small even for trees with many thousand people.
 */
public final class Undo {
    public static final int LIMIT = 100;

    private record Saved(GedcomNode copy, int index) {
    }

    /** One change: the state of the records it touched, before it. */
    public final class Step {
        private final FamilyTree tree;
        private final String label;
        private final Map<String, Saved> before = new LinkedHashMap<>();
        private final Set<String> idsAtStart;

        private Step(FamilyTree tree, String label) {
            this.tree = tree;
            this.label = label;
            this.idsAtStart = ids(tree);
        }

        public String label() {
            return label;
        }

        /** Keeps a copy of the record before it changes; only the first call per record counts. */
        public Step touch(String id) {
            if (id == null || before.containsKey(id)) return this;
            before.put(id, new Saved(tree.record(id).map(GedcomNode::copy).orElse(null), tree.indexOf(id)));
            return this;
        }

        /** The person, their families and everyone in those families (links change on both sides). */
        public Step touchAround(Person p) {
            touch(p.id());
            for (Family f : p.families()) touchFamily(f);
            for (Family f : p.parentFamilies()) touchFamily(f);
            return this;
        }

        public Step touchFamily(Family f) {
            touch(f.id());
            for (Person q : f.partners()) touch(q.id());
            for (Person q : f.children()) touch(q.id());
            return this;
        }

        private void finish() {
            for (String id : ids(tree)) if (!idsAtStart.contains(id)) before.putIfAbsent(id, new Saved(null, 0));
        }

        boolean isEmpty() {
            return before.isEmpty();
        }

        /** Puts the records back as they were; returns the step that does the opposite. */
        private Step revert() {
            Step opposite = new Step(tree, label);
            for (String id : before.keySet()) opposite.touch(id);
            List<Map.Entry<String, Saved>> entries = List.copyOf(before.entrySet());
            for (Map.Entry<String, Saved> e : entries) tree.restore(e.getKey(), e.getValue().copy(), e.getValue().index());
            tree.changed();
            return opposite;
        }
    }

    private final Deque<Step> undo = new ArrayDeque<>();
    private final Deque<Step> redo = new ArrayDeque<>();

    public Step begin(FamilyTree tree, String label) {
        return new Step(tree, label);
    }

    /** Ends a change: it can now be undone. */
    public void commit(Step step) {
        step.finish();
        if (step.isEmpty()) return;
        undo.push(step);
        while (undo.size() > LIMIT) undo.removeLast();
        redo.clear();
    }

    /** Takes a change back that was not committed (a dialog was cancelled). */
    public void cancel(Step step) {
        step.finish();
        step.revert();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    public String undoLabel() {
        return undo.isEmpty() ? "" : undo.peek().label();
    }

    public String redoLabel() {
        return redo.isEmpty() ? "" : redo.peek().label();
    }

    public boolean undo() {
        if (undo.isEmpty()) return false;
        redo.push(undo.pop().revert());
        return true;
    }

    public boolean redo() {
        if (redo.isEmpty()) return false;
        undo.push(redo.pop().revert());
        return true;
    }

    public void clear() {
        undo.clear();
        redo.clear();
    }

    private static Set<String> ids(FamilyTree tree) {
        Set<String> ids = new HashSet<>();
        for (GedcomNode r : tree.records()) if (r.xref() != null) ids.add(r.xref());
        return ids;
    }
}
