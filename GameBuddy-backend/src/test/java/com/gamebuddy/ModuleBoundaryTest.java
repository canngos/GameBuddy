package com.gamebuddy;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * Keeps the monolith modular.
 *
 * <p>The case for folding five services into one process was that they already shared a
 * database and a core entity, so the separation was costing deployments and network hops
 * without buying isolation. That argument only holds if the modules stay separable — if
 * this collapses into a big ball of mud, the criticism of the old architecture becomes a
 * description of the new one.
 *
 * <p>So the boundaries are compiled, not documented. Each rule below is a mistake that is
 * easy to make and expensive to undo once a hundred call sites depend on it.
 *
 * <p>{@code allowEmptyShould} is set while the modules are still being moved across: a
 * rule that matches nothing yet should not fail, but it must start applying the moment the
 * first matching class arrives.
 */
@AnalyzeClasses(packages = "com.gamebuddy", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    private static final String ROOT = "com.gamebuddy.";

    /** Everything that is not a feature module: the shared foundation. */
    private static final String SHARED = "shared";

    /**
     * No cycles between modules.
     *
     * <p>The most important rule here. Two modules that call each other cannot be reasoned
     * about, tested, or extracted separately — and a cycle is almost never introduced
     * deliberately, it accumulates one convenient import at a time.
     */
    @ArchTest
    static final ArchRule modulesAreAcyclic =
            SlicesRuleDefinition.slices().matching(ROOT + "(*)..").should().beFreeOfCycles();

    /**
     * {@code shared} is the bottom of the stack.
     *
     * <p>It holds the entities more than one module needs. If it starts importing from a
     * module, that module's concerns have leaked into everyone's foundation.
     */
    @ArchTest
    static final ArchRule sharedDependsOnNoModule = noClasses()
            .that()
            .resideInAPackage("..gamebuddy.shared..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..gamebuddy.auth..",
                    "..gamebuddy.profile..",
                    "..gamebuddy.community..",
                    "..gamebuddy.match..",
                    "..gamebuddy.notif..",
                    "..gamebuddy.billing..")
            .allowEmptyShould(true);

    /**
     * A repository is private to its module.
     *
     * <p>This is what a network boundary used to enforce for free. Without it one module
     * starts querying another's tables directly, ownership of that data becomes
     * "everyone", and you get exactly the confusion that let avatars live in two schemas
     * at once.
     *
     * <p>Cross-module reads go through the owning module's service interface instead.
     */
    @ArchTest
    static final ArchRule repositoriesAreModulePrivate = classes()
            .that()
            .haveSimpleNameEndingWith("Repository")
            .should(beAccessedOnlyFromWithinTheirOwnModule())
            .allowEmptyShould(true);

    /** Controllers are the edge; nothing inside the application should call one. */
    @ArchTest
    static final ArchRule controllersAreNotCalled = noClasses()
            .that()
            .haveSimpleNameNotEndingWith("Controller")
            .should()
            .dependOnClassesThat()
            .haveSimpleNameEndingWith("Controller")
            .allowEmptyShould(true);

    /**
     * Entities never reach the wire.
     *
     * <p>Serialising {@code Gamer} would publish the password hash, the FCM token, and
     * whatever associations Hibernate happened to initialise — a profile response that
     * quietly includes every gamer you have ever blocked.
     *
     * <p>Checked on the response types rather than on controllers. Controllers legitimately
     * touch entities: {@code @AuthenticationPrincipal Gamer} is how Spring Security hands
     * over the authenticated user, since {@code Gamer} is the {@code UserDetails}. Taking
     * an entity in is safe; putting one in a response body is the leak, and a response body
     * that holds no entity cannot serialise one however the controller is written.
     */
    @ArchTest
    static final ArchRule responseBodiesDoNotHoldEntities = noClasses()
            .that()
            .resideInAnyPackage("..interfaces.dto..", "..interfaces.response..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..gamebuddy.shared.entity..")
            .allowEmptyShould(true);

    // ------------------------------------------------------------------------

    /**
     * The module a class belongs to: the first package segment under the root.
     *
     * @return the segment, or {@code null} for classes sitting directly at the root
     */
    private static String moduleOf(JavaClass type) {
        String name = type.getPackageName();
        if (!name.startsWith(ROOT)) {
            return null;
        }
        String rest = name.substring(ROOT.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }

    private static ArchCondition<JavaClass> beAccessedOnlyFromWithinTheirOwnModule() {
        return new ArchCondition<>("be accessed only from within their own module") {
            @Override
            public void check(JavaClass target, ConditionEvents events) {
                String owner = moduleOf(target);
                if (owner == null || SHARED.equals(owner)) {
                    // Shared repositories are deliberately available to every module.
                    return;
                }
                for (JavaAccess<?> access : target.getAccessesToSelf()) {
                    JavaClass caller = access.getOriginOwner();
                    String from = moduleOf(caller);
                    if (from == null || from.equals(owner)) {
                        continue;
                    }
                    events.add(SimpleConditionEvent.violated(
                            caller,
                            "%s (module '%s') reaches into %s (module '%s') at %s"
                                    .formatted(
                                            caller.getName(),
                                            from,
                                            target.getName(),
                                            owner,
                                            access.getSourceCodeLocation())));
                }
            }
        };
    }
}
