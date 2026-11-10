package eu.amadeos.apps.familytree.ui.text;

import eu.amadeos.apps.familytree.domain.Fact;
import eu.amadeos.apps.familytree.domain.Family;
import eu.amadeos.apps.familytree.domain.GenDate;
import eu.amadeos.apps.familytree.domain.Living;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.domain.Sex;
import eu.amadeos.apps.familytree.ui.chart.ChartColors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static eu.amadeos.apps.familytree.ui.text.Html.esc;
import static eu.amadeos.apps.familytree.ui.text.Html.link;

/**
 * The HTML pages of the program: the family sheet (a couple, their parents, marriage and children), the timeline
 * of a person's life and the summary beside the charts. Names are links ({@code person:I12}).
 */
public final class Pages {
    private final Texts t;

    public Pages(Texts texts) {
        this.t = texts;
    }

    // ---- family sheet ----------------------------------------------------------------------------------------

    /**
     * The families shown for a person on the family sheet: the families as a partner, else the family as a child.
     */
    public static List<Family> sheetFamilies(Person p) {
        List<Family> f = p.families();
        return f.isEmpty() ? p.parentFamilies() : f;
    }

    public String familySheet(Person p, int index, ChartColors c) {
        StringBuilder b = new StringBuilder(Html.page(c));
        List<Family> families = sheetFamilies(p);
        if (families.isEmpty()) {
            b.append("<h1>").append(esc(p.fullName())).append("</h1><p class=m>")
                    .append(esc(t.get("family.none"))).append("</p>");
            return b.append(Html.end()).toString();
        }
        Family f = families.get(Math.max(0, Math.min(index, families.size() - 1)));
        Optional<Person> a = f.husband();
        Optional<Person> w = f.wife();
        b.append("<table width='100%' cellspacing=0><tr>");
        column(b, a, f);
        column(b, w, f);
        b.append("</tr></table>");
        Optional<Fact> marr = f.marriage();
        b.append("<h2>").append(esc(t.get("family.marriage"))).append("</h2><p>");
        b.append(marr.map(m -> esc(t.when(m))).filter(s -> !s.isEmpty()).orElse("<span class=m>"
                + esc(t.get("family.unknown")) + "</span>"));
        for (Fact other : f.facts()) {
            if (other.tag().equals("MARR")) continue;
            b.append("<br>").append(esc(t.factName(other))).append(": ").append(esc(t.when(other)));
        }
        b.append("</p>");
        List<Person> kids = f.children();
        b.append("<h2>").append(esc(t.format("family.children", kids.size()))).append("</h2>");
        if (!kids.isEmpty()) {
            b.append("<table cellspacing=0><tr><th></th><th>").append(esc(t.get("family.name"))).append("</th><th>")
                    .append(esc(t.get("family.born"))).append("</th><th>").append(esc(t.get("family.died")))
                    .append("</th><th>").append(esc(t.get("family.partners"))).append("</th></tr>");
            int n = 1;
            for (Person k : kids) {
                b.append("<tr><td class=m>").append(n++).append("</td><td>").append(link(k)).append(" <span class=m>")
                        .append(esc(sexMark(k.sex()))).append("</span></td><td>")
                        .append(esc(k.birth().map(t::when).orElse(""))).append("</td><td>")
                        .append(esc(k.isDeceased() ? k.death().map(t::when).orElse("†") : "")).append("</td><td>");
                List<String> partners = new ArrayList<>();
                for (Person pk : k.partners()) partners.add(link(pk));
                b.append(String.join(", ", partners)).append("</td></tr>");
            }
            b.append("</table>");
        }
        if (families.size() > 1) {
            b.append("<p class=m>").append(esc(t.format("family.more", families.size()))).append("</p>");
        }
        return b.append(Html.end()).toString();
    }

    private static String sexMark(Sex s) {
        return switch (s) {
            case MALE -> "♂";
            case FEMALE -> "♀";
            default -> "";
        };
    }

    private void column(StringBuilder b, Optional<Person> person, Family f) {
        b.append("<td width='50%'>");
        if (person.isEmpty()) {
            b.append("<p class=m>").append(esc(t.get("family.unknown-partner"))).append("</p></td>");
            return;
        }
        Person p = person.get();
        b.append("<h1>").append(link(p)).append("</h1>");
        String nee = p.birthSurname();
        b.append("<span class=m>").append(esc(nee.isEmpty() ? p.lifespan() : p.lifespan() + " · " + t.nee(nee)))
                .append("</span>");
        b.append("<table cellspacing=0>");
        for (String tag : List.of("BIRT", "CHR", "BAPM", "OCCU", "RESI", "DEAT", "BURI")) {
            for (Fact fact : p.facts()) {
                if (!fact.tag().equals(tag)) continue;
                String value = fact.type().map(ft -> ft.isAttribute()).orElse(false)
                        ? join(fact.details(), t.when(fact)) : join(t.when(fact), fact.details());
                if (value.isEmpty()) continue;
                b.append("<tr><th>").append(esc(t.factName(fact))).append("</th><td>").append(esc(value))
                        .append("</td></tr>");
                break;
            }
        }
        p.father().ifPresent(x -> b.append("<tr><th>").append(esc(t.get("family.father"))).append("</th><td>")
                .append(link(x)).append("</td></tr>"));
        p.mother().ifPresent(x -> b.append("<tr><th>").append(esc(t.get("family.mother"))).append("</th><td>")
                .append(link(x)).append("</td></tr>"));
        List<String> others = new ArrayList<>();
        for (Family other : p.families()) {
            if (other.equals(f)) continue;
            other.partnerOf(p).ifPresent(x -> others.add(link(x)));
        }
        if (!others.isEmpty()) {
            b.append("<tr><th>").append(esc(t.get("family.other-partners"))).append("</th><td>")
                    .append(String.join(", ", others)).append("</td></tr>");
        }
        b.append("</table></td>");
    }

    private static String join(String a, String b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a + " · " + b;
    }

    // ---- timeline --------------------------------------------------------------------------------------------

    record Event(GenDate date, String what, String place, Person who, boolean own) {
    }

    public String timeline(Person p, ChartColors c) {
        StringBuilder b = new StringBuilder(Html.page(c));
        b.append("<h1>").append(esc(p.fullName())).append("</h1><span class=m>").append(esc(p.lifespan()))
                .append("</span>");
        List<Event> events = events(p);
        b.append("<table cellspacing=0 style='margin-top:10px'><tr><th>").append(esc(t.get("timeline.date")))
                .append("</th><th>").append(esc(t.get("timeline.age"))).append("</th><th>")
                .append(esc(t.get("timeline.event"))).append("</th><th>").append(esc(t.get("timeline.place")))
                .append("</th></tr>");
        for (Event e : events) {
            OptionalInt age = Living.age(p.birthDate(), e.date());
            b.append("<tr><td>").append(esc(t.date(e.date()))).append("</td><td class=m>")
                    .append(age.isPresent() ? age.getAsInt() : "").append("</td><td>");
            b.append(e.own() ? "<b>" + esc(e.what()) + "</b>" : esc(e.what()));
            if (e.who() != null) b.append(" ").append(link(e.who()));
            b.append("</td><td>").append(esc(e.place())).append("</td></tr>");
        }
        b.append("</table>");
        if (events.isEmpty()) b.append("<p class=m>").append(esc(t.get("timeline.empty"))).append("</p>");
        return b.append(Html.end()).toString();
    }

    /** The person's own facts and the births, marriages and deaths of the closest family during their life. */
    List<Event> events(Person p) {
        List<Event> list = new ArrayList<>();
        for (Fact f : p.facts()) {
            String what = t.factName(f);
            String details = f.details();
            if (!details.isEmpty() && !f.tag().equals("EVEN") && !f.tag().equals("FACT")) what += ": " + details;
            list.add(new Event(f.date(), what, f.place(), null, true));
        }
        for (Family fam : p.families()) {
            Person partner = fam.partnerOf(p).orElse(null);
            for (Fact f : fam.facts()) {
                list.add(new Event(f.date(), t.factName(f) + (partner != null ? " " + t.get("timeline.with") : ""),
                        f.place(), partner, true));
            }
            if (partner != null) {
                partner.death().ifPresent(d -> list.add(new Event(d.date(), t.get("timeline.death-partner"),
                        d.place(), partner, false)));
            }
            for (Person k : fam.children()) {
                k.birth().ifPresent(bf -> list.add(new Event(bf.date(), t.format("timeline.birth-child",
                        t.child(k.sex())), bf.place(), k, false)));
                k.death().ifPresent(df -> list.add(new Event(df.date(), t.format("timeline.death-child",
                        t.child(k.sex())), df.place(), k, false)));
            }
        }
        for (Person parent : p.parents()) {
            parent.death().ifPresent(d -> list.add(new Event(d.date(), t.get(parent.sex() == Sex.FEMALE
                    ? "timeline.death-mother" : "timeline.death-father"), d.place(), parent, false)));
        }
        long born = p.birthDate().sortKey();
        long died = p.isDeceased() ? p.deathDate().sortKey() : Long.MAX_VALUE - 1;
        list.removeIf(e -> !e.own() && (!e.date().isValid() || e.date().sortKey() < born
                || died != Long.MAX_VALUE && e.date().sortKey() > died + 50));
        list.sort(Comparator.comparingLong((Event e) -> e.date().sortKey()));
        return list;
    }

    // ---- summary beside the charts ---------------------------------------------------------------------------

    public String summary(Person p, ChartColors c) {
        StringBuilder b = new StringBuilder(Html.page(c));
        b.append("<h2 style='margin-top:0'>").append(esc(t.get("details.facts"))).append("</h2><table cellspacing=0>");
        int shown = 0;
        for (Fact f : p.facts()) {
            String value = f.type().map(ft -> ft.isAttribute()).orElse(false)
                    ? join(f.details(), t.when(f)) : join(t.when(f), f.details());
            if (value.isEmpty() && f.tag().equals("DEAT")) value = "†";
            if (value.isEmpty()) continue;
            b.append("<tr><th>").append(esc(t.factName(f))).append("</th><td>").append(esc(value)).append("</td></tr>");
            shown++;
        }
        if (shown == 0) b.append("<tr><td class=m>").append(esc(t.get("details.no-facts"))).append("</td></tr>");
        b.append("</table><h2>").append(esc(t.get("details.family"))).append("</h2><table cellspacing=0>");
        row(b, t.get("details.parents"), p.parents());
        for (Family f : p.families()) {
            Optional<Person> partner = f.partnerOf(p);
            if (partner.isEmpty()) continue;
            String m = f.marriage().map(x -> t.dates().format(x.date())).filter(s -> !s.isEmpty())
                    .map(s -> " <span class=m>∞ " + esc(s) + "</span>").orElse("");
            b.append("<tr><th>").append(esc(t.get("details.partner"))).append("</th><td>").append(link(partner.get()))
                    .append(m).append("</td></tr>");
        }
        row(b, t.get("details.children"), p.children());
        row(b, t.get("details.siblings"), p.siblings());
        b.append("</table>");
        int sources = p.citationCount();
        int media = p.mediaIds().size();
        b.append("<h2>").append(esc(t.get("details.sources-media"))).append("</h2><table cellspacing=0>")
                .append("<tr><th>").append(esc(t.get("details.sources"))).append("</th><td>").append(sources)
                .append("</td></tr><tr><th>").append(esc(t.get("details.media"))).append("</th><td>").append(media)
                .append("</td></tr></table>");
        String notes = p.notes();
        if (!notes.isBlank()) {
            String shortNotes = notes.length() > 400 ? notes.substring(0, 400) + "…" : notes;
            b.append("<h2>").append(esc(t.get("details.notes"))).append("</h2><p>").append(esc(shortNotes)).append("</p>");
        }
        return b.append(Html.end()).toString();
    }

    private void row(StringBuilder b, String label, List<Person> people) {
        if (people.isEmpty()) return;
        List<String> links = new ArrayList<>();
        for (Person x : people) links.add(link(x, x.calledName().isEmpty() ? x.fullName() : shortOf(x)));
        b.append("<tr><th>").append(esc(label)).append("</th><td>").append(String.join(", ", links))
                .append("</td></tr>");
    }

    private static String shortOf(Person x) {
        return x.shortName();
    }
}
