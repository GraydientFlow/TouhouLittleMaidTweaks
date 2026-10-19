// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.station;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/** Equal weighted shares, rounded to whole adults, with usable remainders redistributed fairly. */
public final class SpeciesCapacity {
    private SpeciesCapacity() {}

    /** All values use the same integer weight quantum; the caller caches by budget and unit sizes. */
    public static <K> Map<K, Long> allocate(long budget, Map<K, Long> unitSizes, Comparator<K> tieOrder) {
        if (budget < 0) throw new IllegalArgumentException("Negative capacity");
        if (unitSizes.isEmpty()) return Map.of();
        var keys = new ArrayList<>(unitSizes.keySet());
        // At an equal next share, try the larger animal first; smaller ones can fill its remainder.
        keys.sort(Comparator.<K>comparingLong(unitSizes::get).reversed().thenComparing(tieOrder));
        int count = keys.size();
        long[] units = new long[count], used = new long[count];
        boolean[] active = new boolean[count];
        for (int i = 0; i < count; i++) {
            units[i] = unitSizes.get(keys.get(i));
            if (units[i] <= 0) throw new IllegalArgumentException("Non-positive animal size");
            active[i] = true;
        }
        long remaining = budget;
        while (remaining > 0) {
            int eligible = 0;
            for (int i = 0; i < count; i++) {
                if (units[i] > remaining) active[i] = false;
                if (active[i]) eligible++;
            }
            if (eligible == 0) break;

            // Raise every eligible share in bulk. This never iterates once per allowed animal.
            long low = 0, high = budget;
            while (low < high) {
                long middle = low + (high - low + 1) / 2;
                if (fits(middle, remaining, units, used, active)) low = middle;
                else high = middle - 1;
            }
            for (int i = 0; i < count; i++) if (active[i]) {
                long extra = Math.max(0, low / units[i] * units[i] - used[i]);
                used[i] += extra;
                remaining -= extra;
            }
            if (remaining == 0) break;
            boolean removed = false;
            for (int i = 0; i < count; i++) if (active[i] && units[i] > remaining) {
                active[i] = false;
                removed = true;
            }
            if (removed) continue;

            // The next common boundary cannot fit every tied animal. Resolve just that boundary.
            long next = Long.MAX_VALUE;
            for (int i = 0; i < count; i++) if (active[i]) next = Math.min(next, used[i] + units[i]);
            for (int i = 0; i < count; i++) if (active[i] && used[i] + units[i] == next && units[i] <= remaining) {
                used[i] += units[i];
                remaining -= units[i];
            }
            // A partial boundary leaves at least one type unable to fit; the next pass removes it.
        }
        var result = new HashMap<K, Long>();
        for (int i = 0; i < count; i++) result.put(keys.get(i), used[i]);
        return Map.copyOf(result);
    }

    private static boolean fits(long level, long remaining, long[] units, long[] used, boolean[] active) {
        long needed = 0;
        for (int i = 0; i < units.length; i++) if (active[i]) {
            needed += Math.max(0, level / units[i] * units[i] - used[i]);
            if (needed > remaining) return false;
        }
        return true;
    }
}
