// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Graydients contributors
// License/source notice update: 2026-10-17.
// See COPYRIGHT, LICENSE and THIRD_PARTY_NOTICES.md for terms and attribution.

package io.github.graydients.tlmtweaks.sync;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Enforce the compressed byte limit before the JDK subscriber retains a body chunk. */
public final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
    private final int limit;
    private Flow.Subscription subscription;
    private long received;
    private boolean ended;

    public LimitedBody(int limit) {
        if (limit < 0) throw new IllegalArgumentException("Negative body limit");
        this.limit = limit;
    }
    @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
    @Override public void onSubscribe(Flow.Subscription incoming) {
        if (subscription != null) { incoming.cancel(); return; }
        subscription = incoming;
        delegate.onSubscribe(incoming);
    }
    @Override public void onNext(List<ByteBuffer> items) {
        if (ended) return;
        for (ByteBuffer item : items) {
            received += item.remaining();
            if (received > limit) {
                subscription.cancel();
                onError(new IOException("Official response exceeds the size limit"));
                return;
            }
        }
        delegate.onNext(items);
    }
    @Override public void onError(Throwable error) {
        if (!ended) { ended = true; delegate.onError(error); }
    }
    @Override public void onComplete() {
        if (!ended) { ended = true; delegate.onComplete(); }
    }
}
