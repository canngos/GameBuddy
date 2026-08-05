package com.gamebuddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * GameBuddy, as one process.
 *
 * <p>This replaces five Spring Boot services that already shared a single Postgres
 * database and all mapped the same {@code gamer} table. That arrangement had the costs of
 * microservices — five deployments, network hops between them, no cross-service
 * transaction — and none of the benefits: a change to {@code Gamer} forced all five to
 * redeploy, one database meant one point of failure, and there was one developer, not five
 * teams. It was a distributed monolith.
 *
 * <p>The avatars bug (avatars and achievements resolving to {@code schappl} in some
 * services and {@code schauth} in others, so a purchased avatar was invisible to the
 * profile screen) is the kind of defect that arrangement invites and this one makes
 * impossible: there is now one entity per table, in one place.
 *
 * <p><strong>Modular, not shapeless.</strong> The five services survive as packages under
 * {@code com.gamebuddy.<module>}, each owning its entities, services and
 * controllers. The boundaries are enforced rather than aspirational — see
 * {@code ModuleBoundaryTest}. If a module ever needs extracting again, that is a matter of
 * moving a package rather than untangling a decade of accreted coupling.
 */
@SpringBootApplication
public class GameBuddyApplication {

    public static void main(String[] args) {
        SpringApplication.run(GameBuddyApplication.class, args);
    }
}
