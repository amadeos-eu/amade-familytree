package eu.amadeos.apps.familytree.ui.text;

import eu.amadeos.apps.familytree.domain.DateText;
import eu.amadeos.apps.familytree.domain.Fact;
import eu.amadeos.apps.familytree.domain.FactType;
import eu.amadeos.apps.familytree.domain.GenDate;
import eu.amadeos.apps.familytree.domain.Living;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.domain.Sex;
import eu.amadeos.commons.i18n.Messages;

import java.util.OptionalInt;

/** The program's words for facts, dates, ages and people, in its language. */
public final class Texts {
    private final Messages msg;
    private final DateText dates;

    public Texts(Messages msg) {
        this.msg = msg;
        this.dates = new DateText(msg);
    }

    public Messages msg() {
        return msg;
    }

    public DateText dates() {
        return dates;
    }

    public String get(String key) {
        return msg.get(key);
    }

    public String format(String key, Object... args) {
        return msg.format(key, args);
    }

    /** "Birth", "Occupation", or for EVEN/FACT their own type, or the tag of an unknown fact. */
    public String factName(Fact f) {
        if ((f.tag().equals("EVEN") || f.tag().equals("FACT")) && !f.customType().isEmpty()) return f.customType();
        return f.type().map(t -> msg.get(t.textKey())).orElse(f.tag());
    }

    public String factName(FactType t) {
        return msg.get(t.textKey());
    }

    public String date(GenDate d) {
        return dates.format(d);
    }

    /** "14 March 1928, Esslingen am Neckar" or what of it is known. */
    public String when(Fact f) {
        String d = dates.format(f.date());
        String p = f.place();
        if (d.isEmpty()) return p;
        if (p.isEmpty()) return d;
        return msg.format("fact.date-place", d, p);
    }

    public String sex(Sex s) {
        return msg.get(s.textKey());
    }

    public String nee(String birthSurname) {
        return msg.format("person.nee", birthSurname);
    }

    /** "aged 80" for the dead, "age 71" for the living, "" when unknown. */
    public String age(Person p) {
        GenDate born = p.birthDate();
        if (p.isDeceased()) {
            OptionalInt a = Living.age(born, p.deathDate());
            return a.isPresent() ? msg.format("person.died-aged", a.getAsInt()) : "";
        }
        GenDate today = GenDate.exact(java.time.LocalDate.now().getDayOfMonth(),
                java.time.LocalDate.now().getMonthValue(), java.time.LocalDate.now().getYear());
        OptionalInt a = Living.age(born, today);
        if (a.isEmpty() || a.getAsInt() > Living.MAX_AGE) return "";
        return msg.format("person.age", a.getAsInt());
    }

    /** "son", "daughter" or "child". */
    public String child(Sex s) {
        return msg.get(switch (s) {
            case MALE -> "relative.son";
            case FEMALE -> "relative.daughter";
            default -> "relative.child";
        });
    }
}
