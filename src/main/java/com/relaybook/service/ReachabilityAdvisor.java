package com.relaybook.service;

import com.relaybook.model.Contact;
import com.relaybook.model.ContactType;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * Transparent, explainable route/freshness advisor.
 *
 * <p>The advisor is the <em>context</em> of the Strategy pattern: it holds one
 * {@link RoutingPolicy} per contact type and delegates the whole evaluation.
 * The default registry scores emergency contacts with the strict policy and
 * everything else with the default policy; a custom registry can be injected
 * (used by tests and by anyone tuning the rules).</p>
 *
 * <p>It reports route coverage and record freshness; it does not predict
 * whether a person will answer.</p>
 */
public final class ReachabilityAdvisor {
    private final Map<ContactType, RoutingPolicy> policies = new EnumMap<>(ContactType.class);

    public ReachabilityAdvisor() {
        this(null);
    }

    /** Allows tests and tuning code to replace the policy for selected types. */
    public ReachabilityAdvisor(Map<ContactType, RoutingPolicy> customPolicies) {
        for (ContactType type : ContactType.values()) {
            policies.put(type, type == ContactType.EMERGENCY
                    ? new StrictRoutingPolicy() : new DefaultRoutingPolicy());
        }
        if (customPolicies != null) {
            policies.putAll(customPolicies);
        }
    }

    public ReachabilityReport analyze(Contact contact) {
        return analyze(contact, LocalDate.now());
    }

    /** Delegates to the policy registered for this contact's subtype. */
    public ReachabilityReport analyze(Contact contact, LocalDate today) {
        RoutingPolicy policy = policies.getOrDefault(contact.getContactType(), new DefaultRoutingPolicy());
        return policy.evaluate(contact, today);
    }

    public RoutingPolicy policyFor(Contact contact) {
        return policies.getOrDefault(contact.getContactType(), new DefaultRoutingPolicy());
    }
}
