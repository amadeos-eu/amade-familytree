package eu.amadeos.apps.familytree.ui.chart;

import eu.amadeos.apps.familytree.domain.Person;

import java.awt.Component;

/** What the charts report: a click selects, a double-click puts a person in the middle, a right-click asks for a menu. */
public interface ChartListener {
    void select(Person p);

    void centre(Person p);

    void edit(Person p);

    void popup(Person p, Component on, int x, int y);
}
