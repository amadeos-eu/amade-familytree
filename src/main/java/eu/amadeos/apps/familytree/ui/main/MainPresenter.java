package eu.amadeos.apps.familytree.ui.main;

import eu.amadeos.apps.familytree.domain.Family;
import eu.amadeos.apps.familytree.domain.FamilyTree;
import eu.amadeos.apps.familytree.domain.Living;
import eu.amadeos.apps.familytree.domain.Person;
import eu.amadeos.apps.familytree.domain.Relationship;
import eu.amadeos.apps.familytree.domain.Sex;
import eu.amadeos.apps.familytree.repository.FamilyTreePreferences;
import eu.amadeos.apps.familytree.service.DesktopOpener;
import eu.amadeos.apps.familytree.service.GedcomExport;
import eu.amadeos.apps.familytree.service.GedcomImport;
import eu.amadeos.apps.familytree.service.PhotoDownloader;
import eu.amadeos.apps.familytree.service.StoredTree;
import eu.amadeos.apps.familytree.service.TreeStore;
import eu.amadeos.apps.familytree.service.Undo;
import eu.amadeos.commons.i18n.Language;
import eu.amadeos.commons.i18n.LocaleSettings;
import eu.amadeos.commons.i18n.Messages;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The logic of the main window: which tree is open, who is in the middle of the chart and who is selected, the
 * view, and every change to the tree (each one saved at once and undoable).
 */
public final class MainPresenter {
    /** What the presenter works with. */
    public record Services(TreeStore store, FamilyTreePreferences prefs, Supplier<PhotoDownloader> downloader,
                           DesktopOpener opener, LocaleSettings locales, String version, Runnable exit) {
    }

    private final MainView view;
    private final Messages msg;
    private final Services s;
    private final Undo undo = new Undo();
    private final Deque<String> back = new ArrayDeque<>();
    private final Deque<String> forward = new ArrayDeque<>();

    private StoredTree stored;
    private FamilyTree tree;
    private Person root;
    private Person selected;
    private ViewKind viewKind;

    public MainPresenter(MainView view, Messages msg, Services services) {
        this.view = view;
        this.msg = msg;
        this.s = services;
        this.viewKind = ViewKind.values()[services.prefs().view()];
    }

    // ---- starting and trees ----------------------------------------------------------------------------------

    /** Opens the tree used last, or the welcome page; a file given on the command line is imported. */
    public void start(Optional<Path> file) {
        Optional<StoredTree> last = s.prefs().lastTree().flatMap(s.store()::find);
        if (last.isEmpty() && s.store().list().size() == 1) last = Optional.of(s.store().list().getFirst());
        if (last.isPresent()) openTree(last.get());
        else view.showWelcome();
        showView(viewKind);
        file.ifPresent(this::importFile);
        updateActions();
    }

    public Optional<StoredTree> stored() {
        return Optional.ofNullable(stored);
    }

    public Optional<FamilyTree> tree() {
        return Optional.ofNullable(tree);
    }

    public Optional<Person> selected() {
        return Optional.ofNullable(selected);
    }

    public Optional<Person> root() {
        return Optional.ofNullable(root);
    }

    public ViewKind viewKind() {
        return viewKind;
    }

    public Undo undoHistory() {
        return undo;
    }

    public void openTree(StoredTree t) {
        try {
            FamilyTree loaded = s.store().load(t);
            show(t, loaded);
        } catch (IOException | RuntimeException e) {
            view.showError(msg.format("error.open-tree", t.name(), e.getMessage()));
        }
    }

    private void show(StoredTree t, FamilyTree loaded) {
        stored = t;
        tree = loaded;
        undo.clear();
        back.clear();
        forward.clear();
        s.prefs().setLastTree(t.name());
        view.showTree(t, loaded);
        Person start = home().orElseGet(() -> loaded.persons().stream().findFirst().orElse(null));
        root = start;
        selected = start;
        view.showPerson(root, selected);
        view.showStatus(msg.format("status.opened", t.name(), loaded.count("INDI")));
        updateActions();
    }

    /** The home person of the open tree: chosen by the user, else the first person. */
    public Optional<Person> home() {
        if (tree == null || stored == null) return Optional.empty();
        return s.prefs().homePerson(stored.name()).flatMap(tree::person);
    }

    public void newTree() {
        Optional<String> name = view.askName(msg.get("new.title"), msg.get("new.label"), msg.get("new.default"));
        if (name.isEmpty() || name.get().isBlank()) return;
        try {
            FamilyTree fresh = FamilyTree.empty();
            StoredTree t = s.store().create(name.get(), fresh);
            show(t, fresh);
            // A new tree starts with the person it is about: usually oneself.
            Undo.Step step = undo.begin(fresh, msg.get("undo.add-person"));
            Person p = fresh.newPerson("", "", Sex.UNKNOWN);
            if (view.editPerson(p, step, true)) {
                undo.commit(step);
                s.prefs().setHomePerson(t.name(), p.id());
                save();
                root = p;
                selected = p;
                view.refresh();
                view.showPerson(root, selected);
            } else {
                undo.cancel(step);
                view.refresh();
            }
            updateActions();
        } catch (IOException e) {
            view.showError(msg.format("error.save", e.getMessage()));
        }
    }

    public void openTreeDialog() {
        MainView.TreeActions actions = new MainView.TreeActions() {
            @Override
            public Optional<StoredTree> rename(StoredTree t) {
                Optional<String> name = view.askName(msg.get("trees.rename.title"), msg.get("new.label"), t.name());
                if (name.isEmpty() || name.get().isBlank() || name.get().equals(t.name())) return Optional.empty();
                try {
                    StoredTree renamed = s.store().rename(t, name.get());
                    s.prefs().homePerson(t.name()).ifPresent(id -> s.prefs().setHomePerson(renamed.name(), id));
                    if (t.equals(stored)) {
                        stored = renamed;
                        s.prefs().setLastTree(renamed.name());
                        view.showTree(renamed, tree);
                        view.showPerson(root, selected);
                    }
                    return Optional.of(renamed);
                } catch (IOException e) {
                    view.showError(msg.format("error.rename", e.getMessage()));
                    return Optional.empty();
                }
            }

            @Override
            public boolean delete(StoredTree t) {
                if (!view.confirm(msg.format("trees.delete.confirm", t.name()))) return false;
                try {
                    s.store().delete(t, trash());
                    if (t.equals(stored)) closeTree();
                    return true;
                } catch (IOException e) {
                    view.showError(msg.format("error.delete", e.getMessage()));
                    return false;
                }
            }

            @Override
            public void newTree() {
                MainPresenter.this.newTree();
            }

            @Override
            public void importFile() {
                importGedcom();
            }
        };
        view.chooseTree(s.store().list(), actions).ifPresent(this::openTree);
    }

    private static Path trash() {
        String data = System.getenv("XDG_DATA_HOME");
        Path base = data != null && !data.isBlank() ? Path.of(data)
                : Path.of(System.getProperty("user.home"), ".local", "share");
        return base.resolve("Trash");
    }

    private void closeTree() {
        stored = null;
        tree = null;
        root = null;
        selected = null;
        undo.clear();
        s.prefs().setLastTree("");
        view.showWelcome();
        updateActions();
    }

    // ---- import and export -----------------------------------------------------------------------------------

    public void importGedcom() {
        view.chooseImportFile().ifPresent(this::importFile);
    }

    public void importFile(Path file) {
        view.runWithProgress(msg.get("import.reading"), progress -> GedcomImport.read(file), imp -> {
            Optional<MainView.ImportChoice> choice = view.askImport(imp.report(), imp.suggestedName(), tree != null);
            if (choice.isEmpty()) {
                imp.close();
                return;
            }
            try {
                if (choice.get().merge() && tree != null) {
                    Undo.Step step = undo.begin(tree, msg.get("undo.import"));
                    int added = imp.mergeInto(stored, tree);
                    undo.commit(step);
                    save();
                    view.refresh();
                    view.showStatus(msg.format("status.merged", added));
                } else {
                    StoredTree t = imp.into(s.store(), choice.get().name());
                    openTree(t);
                    view.showStatus(msg.format("status.imported", t.name(), imp.report().people()));
                }
            } catch (IOException e) {
                view.showError(msg.format("error.import", e.getMessage()));
            } finally {
                imp.close();
            }
            if (choice.get().downloadPhotos()) downloadPhotos();
            updateActions();
        }, e -> view.showError(msg.format("error.import", e.getMessage())));
    }

    public void export() {
        if (tree == null) return;
        Living living = Living.now();
        int privatePeople = (int) tree.persons().stream().filter(Person::isPrivate).count();
        int livingPeople = (int) tree.persons().stream().filter(living::isLiving).count();
        Optional<GedcomExport.Options> options = view.askExport(privatePeople, livingPeople);
        if (options.isEmpty()) return;
        Optional<Path> file = view.chooseExportFile(stored.name(), options.get().format());
        if (file.isEmpty()) return;
        FamilyTree copy = tree.copy();
        StoredTree t = stored;
        view.runWithProgress(msg.get("export.writing"),
                progress -> GedcomExport.export(copy, t, options.get(), file.get(), s.version()),
                result -> view.showStatus(msg.format("status.exported", result.written(), file.get().getFileName(),
                        result.leftOut() + result.shortened())),
                e -> view.showError(msg.format("error.export", e.getMessage())));
    }

    public void downloadPhotos() {
        if (tree == null) return;
        long web = tree.mediaRecords().stream().filter(m -> m.isWeb()).count();
        if (web == 0) {
            view.showStatus(msg.get("photos.none"));
            return;
        }
        StoredTree t = stored;
        FamilyTree current = tree;
        view.runWithProgress(msg.get("photos.title"), progress -> s.downloader().get().download(t,
                current.mediaRecords(), (done, total, name) -> progress.update(done, total, name),
                progress::cancelled), result -> {
            Undo.Step step = undo.begin(current, msg.get("undo.photos"));
            for (PhotoDownloader.Downloaded d : result.files()) step.touch(d.media().id());
            PhotoDownloader.apply(result);
            current.changed();
            undo.commit(step);
            save();
            view.refresh();
            view.showStatus(msg.format("photos.done", result.files().size(), result.failed()));
        }, e -> view.showError(msg.format("error.photos", e.getMessage())));
    }

    public void findPhotos() {
        if (tree == null) return;
        Optional<Path> folder = view.chooseFolder(msg.get("photos.folder"));
        if (folder.isEmpty()) return;
        Undo.Step step = undo.begin(tree, msg.get("undo.photos"));
        tree.mediaRecords().forEach(m -> step.touch(m.id()));
        try {
            int found = GedcomImport.findInFolder(tree, stored, folder.get());
            undo.commit(step);
            if (found > 0) save();
            view.refresh();
            view.showStatus(msg.format("photos.found", found));
        } catch (IOException e) {
            undo.cancel(step);
            view.showError(msg.format("error.photos", e.getMessage()));
        }
    }

    public void saveChartPicture() {
        if (tree == null || root == null) return;
        view.choosePictureFile(stored.name() + " – " + root.shortName() + ".png").ifPresent(f -> {
            try {
                view.saveChartPicture(f);
                view.showStatus(msg.format("status.picture", f.getFileName()));
            } catch (IOException e) {
                view.showError(msg.format("error.save", e.getMessage()));
            }
        });
    }

    public void print() {
        if (tree != null) view.print();
    }

    public void showFolder() {
        try {
            s.opener().open(stored != null ? stored.folder() : s.store().root());
        } catch (IOException e) {
            view.showError(msg.format("error.open", e.getMessage()));
        }
    }

    // ---- saving and undo -------------------------------------------------------------------------------------

    void save() {
        if (stored == null || tree == null) return;
        try {
            s.store().save(stored, tree);
        } catch (IOException e) {
            view.showError(msg.format("error.save", e.getMessage()));
        }
    }

    public void undo() {
        String label = undo.undoLabel();
        if (undo.undo()) afterUndo(msg.format("status.undone", label));
    }

    public void redo() {
        String label = undo.redoLabel();
        if (undo.redo()) afterUndo(msg.format("status.redone", label));
    }

    private void afterUndo(String status) {
        save();
        // People may be gone or be new objects now: look them up again by id.
        root = again(root);
        selected = again(selected);
        if (root == null) root = home().orElseGet(() -> tree.persons().stream().findFirst().orElse(null));
        if (selected == null) selected = root;
        view.refresh();
        view.showPerson(root, selected);
        view.showStatus(status);
        updateActions();
    }

    private Person again(Person p) {
        return p == null || tree == null ? null : tree.person(p.id()).orElse(null);
    }

    // ---- selection and navigation ----------------------------------------------------------------------------

    public void select(Person p) {
        if (p == null) return;
        selected = p;
        view.showPerson(root, selected);
        updateActions();
    }

    /** Puts a person in the middle of the chart (and selects them); Back returns to the one before. */
    public void centre(Person p) {
        if (p == null) return;
        if (root != null && !root.equals(p)) {
            back.push(root.id());
            forward.clear();
        }
        root = p;
        selected = p;
        view.showPerson(root, selected);
        updateActions();
    }

    public void centreSelected() {
        centre(selected);
    }

    public void goHome() {
        home().or(() -> Optional.ofNullable(root)).ifPresent(this::centre);
    }

    public void back() {
        navigate(back, forward);
    }

    public void forward() {
        navigate(forward, back);
    }

    private void navigate(Deque<String> from, Deque<String> to) {
        while (!from.isEmpty() && tree != null) {
            Optional<Person> p = tree.person(from.pop());
            if (p.isEmpty()) continue;
            if (root != null) to.push(root.id());
            root = p.get();
            selected = root;
            view.showPerson(root, selected);
            break;
        }
        updateActions();
    }

    public void setHome() {
        if (selected == null || stored == null) return;
        s.prefs().setHomePerson(stored.name(), selected.id());
        view.showStatus(msg.format("status.home", selected.fullName()));
    }

    public void showView(ViewKind kind) {
        viewKind = kind;
        s.prefs().setView(kind.ordinal());
        view.showView(kind, generations());
    }

    public int generations() {
        return s.prefs().generations(viewKind.ordinal(), viewKind.defaultGenerations());
    }

    public void moreGenerations() {
        changeGenerations(1);
    }

    public void fewerGenerations() {
        changeGenerations(-1);
    }

    public void setGenerations(int g) {
        if (!viewKind.hasGenerations()) return;
        int n = Math.max(1, Math.min(viewKind.maxGenerations(), g));
        s.prefs().setGenerations(viewKind.ordinal(), n);
        view.showView(viewKind, n);
    }

    private void changeGenerations(int delta) {
        setGenerations(generations() + delta);
    }

    // ---- changes ---------------------------------------------------------------------------------------------

    public void editSelected() {
        if (selected == null || tree == null) return;
        Undo.Step step = undo.begin(tree, msg.format("undo.edit", selected.shortName()));
        step.touchAround(selected);
        if (view.editPerson(selected, step, false)) {
            undo.commit(step);
            tree.changed();
            save();
            selected = again(selected);
            root = again(root);
            if (root == null) root = selected;
            view.refresh();
            view.showPerson(root, selected);
        } else {
            undo.cancel(step);
            selected = again(selected);
            root = again(root);
            view.refresh();
            view.showPerson(root, selected);
        }
        updateActions();
    }

    /** Adds a new relative of the selected person and opens the editor for them; Cancel takes it back. */
    public void addRelative(Relative kind) {
        if (tree == null) return;
        Person from = selected;
        if (from == null && kind != Relative.PARTNER) return;
        Undo.Step step = undo.begin(tree, msg.get(kind.textKey() + ".undo"));
        if (from != null) step.touchAround(from);
        String surname = from == null ? "" : switch (kind) {
            case FATHER, BROTHER, SISTER -> from.primaryName().map(n -> n.surname()).orElse("");
            case SON, DAUGHTER -> from.sex() == Sex.FEMALE && !from.partners().isEmpty()
                    ? from.partners().getFirst().surname() : from.surname();
            default -> "";
        };
        Person p = switch (kind) {
            case FATHER, MOTHER -> tree.addParent(from, kind.sex(), "", surname);
            case PARTNER -> tree.addPartner(from, from.sex() == Sex.MALE ? Sex.FEMALE
                    : from.sex() == Sex.FEMALE ? Sex.MALE : Sex.UNKNOWN, "", "");
            case SON, DAUGHTER -> tree.addChild(from, chooseFamilyForChild(from), kind.sex(), "", surname);
            case BROTHER, SISTER -> tree.addSibling(from, kind.sex(), "", surname);
        };
        if (view.editPerson(p, step, true)) {
            undo.commit(step);
            save();
            selected = p;
            view.refresh();
            view.showPerson(root, selected);
            view.showStatus(msg.format("status.added", p.fullName()));
        } else {
            undo.cancel(step);
            selected = again(from);
            root = again(root);
            view.refresh();
            view.showPerson(root, selected);
        }
        updateActions();
    }

    /** With several partners, the child goes into the family with the partner chosen last (the first one). */
    private Family chooseFamilyForChild(Person parent) {
        List<Family> fams = parent.families();
        return fams.isEmpty() ? null : fams.getFirst();
    }

    /** Connects an existing person as parent, partner or child of the selected one. */
    public void connectExisting() {
        if (selected == null || tree == null) return;
        Optional<Connection> how = view.chooseConnection(selected);
        if (how.isEmpty()) return;
        List<Person> candidates = tree.persons().stream().filter(p -> !p.equals(selected))
                .sorted(FamilyTree.BY_NAME).toList();
        Optional<Person> other = view.choosePerson(msg.get(how.get().textKey()), candidates);
        if (other.isEmpty()) return;
        connect(selected, how.get(), other.get());
    }

    public void connect(Person p, Connection how, Person other) {
        Undo.Step step = undo.begin(tree, msg.get("undo.connect"));
        step.touchAround(p).touchAround(other);
        try {
            switch (how) {
                case PARENT -> {
                    if (p.parents().contains(other)) throw new IllegalStateException(msg.get("connect.already"));
                    Family f = tree.parentFamilyFor(p, other.sex() == Sex.FEMALE ? Sex.FEMALE : Sex.MALE);
                    step.touch(f.id());
                    tree.linkPartner(f, other);
                }
                case PARTNER -> {
                    if (p.partners().contains(other)) throw new IllegalStateException(msg.get("connect.already"));
                    Family f = tree.newFamily();
                    tree.linkPartner(f, p);
                    tree.linkPartner(f, other);
                }
                case CHILD -> {
                    if (p.children().contains(other)) throw new IllegalStateException(msg.get("connect.already"));
                    Family f = chooseFamilyForChild(p);
                    if (f == null) {
                        f = tree.newFamily();
                        tree.linkPartner(f, p);
                    }
                    step.touchFamily(f);
                    tree.linkChild(f, other);
                }
            }
        } catch (IllegalStateException e) {
            undo.cancel(step);
            view.showError(e.getMessage());
            return;
        }
        undo.commit(step);
        save();
        view.refresh();
        view.showPerson(root, selected);
        view.showStatus(msg.format("status.connected", other.fullName(), p.fullName()));
        updateActions();
    }

    public void deleteSelected() {
        if (selected == null || tree == null) return;
        Person p = selected;
        if (!view.confirm(msg.format("delete.confirm", p.fullName()))) return;
        Undo.Step step = undo.begin(tree, msg.format("undo.delete", p.shortName()));
        step.touchAround(p);
        // The selection moves to a parent, partner or child; they are known only before the links go.
        Person next = p.parents().stream().findFirst().or(() -> p.partners().stream().findFirst())
                .or(() -> p.children().stream().findFirst()).orElse(null);
        // Media and other records stay; only links to the person go.
        tree.removePerson(p);
        undo.commit(step);
        save();
        next = again(next);
        if (root != null && root.equals(p)) root = next != null ? next : home().orElseGet(() ->
                tree.persons().stream().findFirst().orElse(null));
        selected = next != null ? next : root;
        view.refresh();
        view.showPerson(root, selected);
        view.showStatus(msg.format("status.deleted", p.fullName()));
        updateActions();
    }

    public void relationship() {
        if (selected == null || tree == null) return;
        Person to = home().orElse(root);
        if (to == null) return;
        view.showRelationship(to, selected, Relationship.between(to, selected));
    }

    // ---- help and the rest -------------------------------------------------------------------------------------

    public void handbook() {
        try {
            s.opener().handbook();
        } catch (IOException e) {
            view.showError(msg.format("error.open", e.getMessage()));
        }
    }

    public void about() {
        view.showAbout();
    }

    public void setLanguage(Optional<Language> language) {
        try {
            s.locales().setApplicationLanguage(language);
        } catch (IOException e) {
            view.showError(msg.format("error.save", e.getMessage()));
            return;
        }
        view.showStatus(Messages.commons(msg.locale()).get("language.restart"));
    }

    public void exit() {
        view.close();
        s.exit().run();
    }

    void updateActions() {
        view.updateActions(tree != null, selected != null, undo.canUndo() ? undo.undoLabel() : "",
                undo.canRedo() ? undo.redoLabel() : "", !back.isEmpty(), !forward.isEmpty());
    }
}
