// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sync;

/** A connection address or local-world key scopes consent; a registry UUID only scopes pack ownership. */
public final class ConnectionSession {
    public record Token(long generation, String serverKey, ClientPreferences.Consent consent) {}
    private Token current = new Token(0, "", ClientPreferences.Consent.OFF);
    private boolean serverEnabled;

    public synchronized Token begin(String key, ClientPreferences.Consent consent) {
        current = new Token(current.generation() + 1, key, consent);
        serverEnabled = false;
        return current;
    }

    public synchronized void policy(boolean enabled) {
        if (serverEnabled == enabled) return;
        current = new Token(current.generation() + 1, current.serverKey(), current.consent());
        serverEnabled = enabled;
    }

    public synchronized Token token() { return current; }
    public synchronized boolean allowed(Token token) {
        return token == current && token.consent().enabled() && serverEnabled;
    }
}
