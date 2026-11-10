package eu.amadeos.apps.familytree.ui.text;

import eu.amadeos.apps.familytree.domain.Relationship;
import eu.amadeos.apps.familytree.domain.Sex;
import eu.amadeos.commons.i18n.Messages;

/**
 * The word for a {@link Relationship} in the program's language: "son", "great-grandmother", "second cousin
 * once removed", "Schwager", "half-sister". Words differ by sex where the language has them (keys end in
 * {@code .m}, {@code .f} and {@code .u} for unknown).
 */
public final class RelationshipText {
    private final Messages msg;

    public RelationshipText(Messages msg) {
        this.msg = msg;
    }

    public String of(Relationship r) {
        return switch (r.kind()) {
            case SAME -> msg.get("rel.same");
            case NONE -> msg.get("rel.none");
            case PARTNER -> word("rel.partner", r.sex());
            case BLOOD -> blood(r.up(), r.down(), r.half(), r.sex());
            case PARTNERS_RELATIVE -> inLaw(r);
            case RELATIVES_PARTNER -> relativesPartner(r);
        };
    }

    private String inLaw(Relationship r) {
        int up = r.inner().up(), down = r.inner().down();
        // The words a language has: father-in-law, brother-in-law, stepson.
        if (up == 1 && down == 0) return word("rel.parent-in-law", r.sex());
        if (up == 1 && down == 1) return word("rel.sibling-in-law", r.sex());
        if (up == 0 && down == 1) return word("rel.stepchild", r.sex());
        return msg.format("rel.of-partner", blood(up, down, r.inner().half(), r.sex()), r.via().fullName());
    }

    private String relativesPartner(Relationship r) {
        int up = r.inner().up(), down = r.inner().down();
        if (up == 0 && down == 1) return word("rel.child-in-law", r.sex());
        if (up == 1 && down == 1) return word("rel.sibling-in-law", r.sex());
        if (up == 1 && down == 0) return word("rel.step-parent", r.sex());
        return msg.format("rel.partner-of", word("rel.partner", r.sex()), r.via().fullName());
    }

    /** The word for a blood relative {@code up} generations to the common ancestor and {@code down} back. */
    public String blood(int up, int down, boolean half, Sex sex) {
        if (down == 0) return ancestor(up, sex);
        if (up == 0) return descendant(down, sex);
        if (up == 1 && down == 1) return word(half ? "rel.half-sibling" : "rel.sibling", sex);
        if (down == 1) {
            // Aunt or uncle of me or of one of my ancestors.
            return lineage("rel.aunt", "rel.great-aunt", "rel.aunt.many", up - 1, sex);
        }
        if (up == 1) return lineage("rel.niece", "rel.grand-niece", "rel.niece.many", down - 1, sex);
        int degree = Math.min(up, down) - 1;
        int removed = Math.abs(up - down);
        String cousin = msg.format(key("rel.cousin", sex), degree);
        return removed == 0 ? cousin : msg.format("rel.removed", cousin, removed);
    }

    private String ancestor(int up, Sex sex) {
        if (up == 1) return word("rel.parent", sex);
        if (up == 2) return word("rel.grandparent", sex);
        if (up == 3) return word("rel.great-grandparent", sex);
        return many("rel.ancestor.many", up, sex);
    }

    private String descendant(int down, Sex sex) {
        if (down == 1) return word("rel.child", sex);
        if (down == 2) return word("rel.grandchild", sex);
        if (down == 3) return word("rel.great-grandchild", sex);
        return many("rel.descendant.many", down, sex);
    }

    /** One step ("aunt"), two ("great-aunt"), more ("great-great-aunt"). */
    private String lineage(String one, String two, String more, int level, Sex sex) {
        if (level <= 1) return word(one, sex);
        if (level == 2) return word(two, sex);
        return many(more, level + 1, sex);
    }

    /**
     * Patterns for many generations get the repeated prefix ({@code rel.repeat}: "great-", "Ur", "arrière-") as
     * {0}, the number of generations as {1}, the number of repeats as {2} and the prefix one time less as {3}
     * (German says "Urgroßtante" where English says "great-great-aunt").
     */
    private String many(String base, int generations, Sex sex) {
        int repeats = Math.max(1, generations - 2);
        return msg.format(key(base, sex), repeat(repeats), generations, repeats, repeat(repeats - 1));
    }

    /** "Ur", "Urur", "Ururur": the first one as written, the others in lower case. */
    private String repeat(int times) {
        if (times <= 0) return "";
        String prefix = msg.get("rel.repeat");
        return prefix + prefix.toLowerCase(msg.locale()).repeat(times - 1);
    }

    private String word(String base, Sex sex) {
        return msg.get(key(base, sex));
    }

    private static String key(String base, Sex sex) {
        return base + switch (sex) {
            case MALE -> ".m";
            case FEMALE -> ".f";
            default -> ".u";
        };
    }
}
