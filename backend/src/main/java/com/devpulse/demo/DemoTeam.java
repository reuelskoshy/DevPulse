package com.devpulse.demo;

import java.time.Duration;
import java.net.IDN;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.devpulse.user.domain.DpUserRole;

/**
 * The fixed cast of the live demo: a manager and five direct reports at a fictional company, "acme", each with a
 * distinct working rhythm so the team page tells a story. Every address is on {@link #RESERVED_DOMAIN}, which
 * registration refuses, so a demo persona can never collide with a real account.
 */
public final class DemoTeam {

    public static final String RESERVED_DOMAIN = "devpulse.demo";
    public static final String MANAGER_EMAIL = "maya.chen@" + RESERVED_DOMAIN;

    /** Stored (encrypted) in place of a GitHub token. It is never sent anywhere: the demo is read-only, so sync is blocked. */
    static final String DEMO_ACCESS_TOKEN = "demo-token-not-real";
    static final String DEFAULT_BRANCH = "main";

    enum Pace {
        /** Hands-on but lighter: reviews and planning eat into coding time. */
        MODERATE,
        /** The team's heaviest committer. */
        HIGH,
        /** Consistent weekday output. */
        STEADY,
        /** Was active, has tailed off, and has not committed in {@value DemoDataset#QUIET_LAST_COMMIT_DAYS_AGO} days. */
        FADING
    }

    /** A repository in the shared "acme" pool. Real GitHub ids are positive, so negative ids can never collide. */
    record Repo(String name, long githubRepoId, boolean privateRepo, List<String> messages) {
        String fullName() {
            return "acme/" + name;
        }
    }

    /** GitHub connection details. {@code userId} is negative for the same reason as {@link Repo#githubRepoId()}. */
    record GitHubIdentity(long userId, Duration syncedAgo) { }

    /** Inclusive range of days-ago with no commits (holiday). */
    record TimeOff(int fromDaysAgo, int toDaysAgo) {
        static final TimeOff NONE = new TimeOff(-1, -1);

        boolean covers(int daysAgo) {
            return daysAgo >= fromDaysAgo && daysAgo <= toDaysAgo;
        }
    }

    /**
     * How someone works: expected commits per weekday and weekend day, the chance of a weekday with no commits at
     * all (meetings, focus on reviews), and the UTC hours their commits land in.
     */
    record Rhythm(Pace pace, double weekdayCommits, double weekendCommits, double dayOffChance,
                  int firstHourUtc, int lastHourUtc, TimeOff timeOff) { }

    /** How often someone commits to a repository relative to their other repositories. */
    record RepoWeight(Repo repo, int weight) { }

    /**
     * {@code github} and {@code rhythm} are null for someone who has not connected GitHub. {@code repos} is an
     * ordered list, never a hash map, because iteration order feeds the seeded random picks.
     */
    record Persona(String name, DpUserRole role, String location, GitHubIdentity github, Rhythm rhythm,
                   List<RepoWeight> repos) {

        String email() {
            return name.toLowerCase(Locale.ROOT).replace(' ', '.') + "@" + RESERVED_DOMAIN;
        }

        String login() {
            return name.toLowerCase(Locale.ROOT).replace(' ', '-');
        }

        String firstName() {
            return name.substring(0, name.indexOf(' '));
        }

        boolean connected() {
            return github != null;
        }
    }

    static final Repo CHECKOUT_SERVICE = new Repo("checkout-service", -2001L, true, List.of(
            "feat(cart): support stacking promo codes",
            "fix(payments): retry Stripe webhooks on 5xx responses",
            "feat(checkout): add Apple Pay to express checkout",
            "fix(tax): round VAT per line item instead of per order",
            "refactor(orders): extract the order state machine",
            "perf(cart): cache shipping quotes for five minutes",
            "test(payments): cover partial refund edge cases",
            "fix(checkout): keep the cart when a session expires mid-payment",
            "feat(orders): emit order.completed events to the data pipeline",
            "chore(deps): bump stripe-java to 28.2.0",
            "fix(inventory): release reserved stock when a payment fails",
            "docs(api): document the Idempotency-Key header",
            "feat(payments): add the 3-D Secure 2 challenge flow",
            "refactor(cart): replace BigDecimal helpers with a Money type"));

    static final Repo WEB_APP = new Repo("web-app", -2002L, true, List.of(
            "feat(search): add an instant results dropdown",
            "fix(auth): refresh tokens before they expire in background tabs",
            "feat(account): let customers download invoices as PDF",
            "fix(a11y): trap focus inside the cart drawer",
            "perf(home): lazy-load below-the-fold product carousels",
            "refactor(routing): move account pages to nested routes",
            "test(e2e): cover guest checkout in Playwright",
            "fix(i18n): format prices in the shopper's locale",
            "feat(orders): show live delivery tracking on the order page",
            "chore(deps): upgrade to React 19",
            "fix(layout): stop the header shifting on scroll",
            "style(ui): align empty states with the design system",
            "feat(wishlist): sync wishlists across devices"));

    static final Repo DESIGN_SYSTEM = new Repo("design-system", -2003L, false, List.of(
            "feat(button): add loading and icon-only variants",
            "fix(modal): restore focus to the trigger on close",
            "feat(tokens): add elevation and motion tokens",
            "docs(storybook): add usage guidelines for form fields",
            "fix(select): announce the selected option to screen readers",
            "feat(toast): support action buttons and auto-dismiss",
            "refactor(theme): generate CSS variables from design tokens",
            "test(visual): add dark mode snapshots",
            "fix(tooltip): flip placement near the viewport edge",
            "feat(table): add sortable columns and a sticky header",
            "chore(release): publish v4.3.0",
            "perf(icons): make the icon set tree-shakeable"));

    static final Repo MOBILE_APP = new Repo("mobile-app", -2004L, true, List.of(
            "feat(push): deep-link order notifications to the order screen",
            "fix(ios): keep the keyboard from covering the promo code field",
            "feat(checkout): confirm saved cards with Face ID",
            "fix(android): handle the back gesture on the payment sheet",
            "perf(feed): virtualize the product list",
            "refactor(state): move cart state into a shared store",
            "test(e2e): cover login and reorder flows",
            "chore(deps): bump React Native to 0.81",
            "fix(offline): queue add-to-cart actions while offline",
            "feat(profile): add a dark mode toggle",
            "fix(images): cache product thumbnails on disk"));

    static final Repo DATA_PIPELINE = new Repo("data-pipeline", -2005L, false, List.of(
            "feat(ingest): stream order events from Kafka into the warehouse",
            "fix(dbt): deduplicate late-arriving order updates",
            "feat(metrics): add a daily conversion funnel model",
            "perf(spark): partition clickstream by event date",
            "fix(schema): make coupon_code nullable in orders_v2",
            "test(dbt): add freshness checks for revenue models",
            "refactor(airflow): split the nightly DAG into per-domain tasks",
            "feat(exports): publish the weekly cohort report to S3",
            "chore(deps): bump dbt-core to 1.10",
            "fix(backfill): make order backfills idempotent",
            "docs(events): document the event naming conventions"));

    static final Repo INFRA = new Repo("infra", -2006L, true, List.of(
            "feat(k8s): add horizontal pod autoscaling for checkout-service",
            "fix(terraform): pin the AWS provider to 6.x",
            "chore(ci): cache Maven dependencies between runs",
            "feat(observability): add SLO alerts for checkout latency",
            "fix(helm): set memory limits for data-pipeline workers",
            "refactor(terraform): move RDS config into a reusable module",
            "chore(ci): run Playwright tests in parallel shards",
            "feat(security): rotate database credentials via Secrets Manager",
            "fix(dns): lower TTLs ahead of the CDN migration",
            "chore(deps): bump base images to Temurin 21.0.8",
            "docs(runbook): add incident steps for payment outages"));

    static final List<Repo> REPOS = List.of(CHECKOUT_SERVICE, WEB_APP, DESIGN_SYSTEM, MOBILE_APP, DATA_PIPELINE, INFRA);

    static final Persona MANAGER = new Persona("Maya Chen", DpUserRole.MANAGER, "Berlin",
            new GitHubIdentity(-1001L, Duration.ofHours(2)),
            new Rhythm(Pace.MODERATE, 2.4, 0.15, 0.15, 7, 17, TimeOff.NONE),
            List.of(w(CHECKOUT_SERVICE, 5), w(WEB_APP, 3), w(INFRA, 2)));

    static final List<Persona> MEMBERS = List.of(
            new Persona("Arjun Mehta", DpUserRole.MEMBER, "London",
                    new GitHubIdentity(-1002L, Duration.ofMinutes(75)),
                    new Rhythm(Pace.HIGH, 7.0, 1.0, 0.03, 8, 19, TimeOff.NONE),
                    List.of(w(CHECKOUT_SERVICE, 5), w(WEB_APP, 2), w(DATA_PIPELINE, 2), w(INFRA, 1))),
            new Persona("Sofia Alvarez", DpUserRole.MEMBER, "Lisbon",
                    new GitHubIdentity(-1003L, Duration.ofHours(3)),
                    new Rhythm(Pace.STEADY, 4.2, 0.2, 0.08, 7, 16, new TimeOff(36, 42)),
                    List.of(w(WEB_APP, 4), w(DESIGN_SYSTEM, 4), w(MOBILE_APP, 2))),
            new Persona("Daniel Okafor", DpUserRole.MEMBER, "Amsterdam",
                    new GitHubIdentity(-1004L, Duration.ofHours(4)),
                    new Rhythm(Pace.STEADY, 3.8, 0.3, 0.08, 9, 18, new TimeOff(58, 62)),
                    List.of(w(DATA_PIPELINE, 6), w(INFRA, 4))),
            new Persona("Hana Sato", DpUserRole.MEMBER, "Stockholm",
                    new GitHubIdentity(-1005L, Duration.ofHours(5)),
                    new Rhythm(Pace.FADING, 1.6, 0.1, 0.1, 8, 17, TimeOff.NONE),
                    List.of(w(MOBILE_APP, 7), w(DESIGN_SYSTEM, 3))),
            // Joined recently and has not connected GitHub yet: no github_accounts row at all.
            new Persona("Lucas Moreau", DpUserRole.MEMBER, "Paris", null, null, List.of()));

    /** Manager first, so direct reports can point at the saved manager. */
    static final List<Persona> PERSONAS = Stream.concat(Stream.of(MANAGER), MEMBERS.stream()).toList();

    private DemoTeam() {
    }

    /**
     * True for any address on the reserved domain or a subdomain of it, ignoring case, surrounding spaces and
     * trailing dots, and also for Unicode look-alikes of it (see {@link #domainSkeleton}).
     */
    public static boolean isReservedEmail(String email) {
        if (email == null) {
            return false;
        }
        String skeleton = domainSkeleton(email.substring(email.lastIndexOf('@') + 1));
        return skeleton.equals(RESERVED_DOMAIN) || skeleton.endsWith("." + RESERVED_DOMAIN);
    }

    /**
     * Folds a domain so visually confusable spellings compare equal: punycode labels are decoded, compatibility
     * forms (full-width letters, ligatures) are unified with NFKC, accents are dropped, invisible format characters
     * and whitespace are removed, every dot variant IDNA accepts becomes {@code .}, and the Cyrillic, Greek and
     * Armenian letters that look like Latin ones are mapped to them.
     */
    static String domainSkeleton(String domain) {
        String unicode = IDN.toUnicode(domain.strip(), IDN.ALLOW_UNASSIGNED);
        String folded = Normalizer.normalize(Normalizer.normalize(unicode, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT),
                Normalizer.Form.NFKD);
        StringBuilder skeleton = new StringBuilder(folded.length());
        folded.codePoints().forEach(codePoint -> {
            int type = Character.getType(codePoint);
            if (type == Character.NON_SPACING_MARK || type == Character.ENCLOSING_MARK || type == Character.COMBINING_SPACING_MARK
                    || type == Character.FORMAT || Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                return;
            }
            if (DOT_VARIANTS.indexOf(codePoint) >= 0) {
                skeleton.append('.');
                return;
            }
            int index = HOMOGLYPHS.indexOf(codePoint);
            if (index >= 0) {
                skeleton.append(LATIN_EQUIVALENTS.charAt(index));
            } else {
                skeleton.appendCodePoint(codePoint);
            }
        });
        int end = skeleton.length();
        while (end > 0 && skeleton.charAt(end - 1) == '.') {
            end--;
        }
        return skeleton.substring(0, end);
    }

    /** Ideographic, full-width and half-width full stops, and the one-dot leader. */
    private static final String DOT_VARIANTS = "\u3002\uFF0E\uFF61\u2024";
    /** Each character here looks like the Latin letter at the same position in {@link #LATIN_EQUIVALENTS}. */
    private static final String HOMOGLYPHS =
            "\u0430\u0435\u043E\u0440\u0441\u0443\u0445\u0455\u0456\u0458\u0501\u04BB\u04CF\u051B\u051D\u043C"
            + "\u03BD\u0475\u03BF\u03C1\u03C5\u03B9\u03BA\u03B1\u057D\u0585\u0131\u0261\u01C0";
    private static final String LATIN_EQUIVALENTS = "aeopcyxsijdhlqwmvvopuikauoigl";

    private static RepoWeight w(Repo repo, int weight) {
        return new RepoWeight(repo, weight);
    }
}
