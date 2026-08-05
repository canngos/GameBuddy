/**
 * GameBuddy's modular monolith.
 *
 * <h2>Layout</h2>
 *
 * <pre>
 * com.gamebuddy
 *   ├── shared      entities and types more than one module needs (Gamer, Games, ...)
 *   ├── auth        registration, login, sessions, account deletion
 *   ├── profile     profiles, avatars, achievements, coins, friends
 *   ├── community   communities, posts, comments, moderation
 *   ├── match       recommendations, swipes, matches, chat
 *   ├── notif       push notifications
 *   └── billing     subscriptions and in-app purchases
 * </pre>
 *
 * <h2>The rules</h2>
 *
 * <ol>
 *   <li><b>A module may depend on {@code shared}, and on nothing else below it.</b> Modules
 *       do not import each other's internals. This is the rule that kept the services
 *       honest when they were separate processes, and it is the one worth keeping.
 *   <li><b>Cross-module work goes through a module's public service interface</b>, or
 *       through an application event where the caller does not need a result. Reaching
 *       into another module's repository is how a monolith becomes a big ball of mud.
 *   <li><b>Entities belong to exactly one module.</b> Anything genuinely shared lives in
 *       {@code shared}. There is one {@code Gamer} class now, not five.
 * </ol>
 *
 * <p>These are checked by {@code ModuleBoundaryTest} rather than left to discipline. A
 * boundary nobody verifies is a comment.
 *
 * <h2>What stays out of process</h2>
 *
 * <p>The Python recommendation model, because it is a different runtime. It is reached
 * over HTTP and is the only remaining network hop inside the system — and even that should
 * become a batch job that precomputes recommendations into Postgres, leaving the swipe
 * path with no outbound call at all.
 */
package com.gamebuddy;
