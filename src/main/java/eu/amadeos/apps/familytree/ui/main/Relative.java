package eu.amadeos.apps.familytree.ui.main;

import eu.amadeos.apps.familytree.domain.Sex;

import java.util.Locale;

/** The relatives one can add from a person. */
public enum Relative {
    FATHER(Sex.MALE), MOTHER(Sex.FEMALE), PARTNER(Sex.UNKNOWN), SON(Sex.MALE), DAUGHTER(Sex.FEMALE),
    BROTHER(Sex.MALE), SISTER(Sex.FEMALE);

    private final Sex sex;

    Relative(Sex sex) {
        this.sex = sex;
    }

    public Sex sex() {
        return sex;
    }

    public String textKey() {
        return "add." + name().toLowerCase(Locale.ROOT);
    }
}
