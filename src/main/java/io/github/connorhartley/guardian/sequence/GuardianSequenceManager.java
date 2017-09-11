/*
 * MIT License
 *
 * Copyright (c) 2017 Connor Hartley
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.connorhartley.guardian.sequence;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.ichorpowered.guardian.api.entry.EntityEntry;
import com.ichorpowered.guardian.api.sequence.Sequence;
import com.ichorpowered.guardian.api.sequence.SequenceBlueprint;
import com.ichorpowered.guardian.api.sequence.SequenceManager;
import com.ichorpowered.guardian.api.sequence.SequenceRegistry;
import com.ichorpowered.guardian.api.sequence.capture.Capture;
import io.github.connorhartley.guardian.GuardianPlugin;
import org.spongepowered.api.event.Event;
import org.spongepowered.api.scheduler.Task;

import java.util.function.Predicate;

import javax.annotation.Nonnull;

public final class GuardianSequenceManager implements SequenceManager<Event> {

    private final GuardianPlugin plugin;
    private final SequenceRegistry sequenceRegistry;

    private final Multimap<EntityEntry, Sequence<?, ?>> sequences = HashMultimap.create();

    public GuardianSequenceManager(GuardianPlugin plugin, GuardianSequenceRegistry sequenceRegistry) {
        this.plugin = plugin;
        this.sequenceRegistry = sequenceRegistry;
    }

    @Override
    public void invoke(@Nonnull EntityEntry entry, @Nonnull Event event) {
        // Sequence Executor
        this.sequences.get(entry).removeIf(sequence -> this.invokeSequence(sequence, entry, event));

        // Sequence Blueprint Executor
        this.invokeBlueprint(entry, event);
    }

    @Override
    public void invokeFor(@Nonnull EntityEntry entry, @Nonnull Event event, Predicate<Sequence> predicate) {
        // Sequence Executor
        this.sequences.get(entry).removeIf(sequence -> {

            // Note: Do not simplify this. Only invoke the sequence if you have confirmed the predicate test FIRST.
            //       otherwise it defeats the purpose of the test (sequence check is particularly expensive when not
            //       required). As well as this, please ONLY use 'invokeFor' if you need to stop the potentially expensive
            //       check from causing issues in the thread, otherwise just use invoke.
            if (predicate.test(sequence)) {
                return this.invokeSequence(sequence, entry, event);
            }

            return false;
        });

        // Sequence Blueprint Executor
        this.invokeBlueprint(entry, event);
    }

    private boolean invokeSequence(@Nonnull Sequence<?, ?> sequence, @Nonnull EntityEntry entry, @Nonnull Event event) {
        boolean remove = false;

        // 1. Check the event is valid.

        sequence.apply(entry, event);

        // 2. Check if the sequence is cancelled, or is expired.

        if (sequence.isCancelled() || sequence.isExpired()) {
            if (sequence.isRunning()) {
                for (Capture<?, ?> capture : sequence.getCaptureRegistry()) {
                    capture.stop(entry, sequence.getCaptureRegistry().getContainer());
                }
            }

            remove = true;
        }

        // 3. Check if the sequence has finished and fire the event and remove.

        if (sequence.isFinished()) {
            if (sequence.isRunning()) {
                for (Capture<?, ?> capture : sequence.getCaptureRegistry()) {
                    capture.stop(entry, sequence.getCaptureRegistry().getContainer());
                }
            }

            // Fire SequenceFinishEvent.

            // Pop report.

            remove = true;
        }

        return remove;
    }

    private void invokeBlueprint(@Nonnull EntityEntry entry, @Nonnull Event event) {
        for (SequenceBlueprint sequenceBlueprint : this.sequenceRegistry) {

            // 1. Check for matching sequence.

            if (this.sequences.get(entry).stream()
                    .anyMatch(playerSequence -> playerSequence.getSequenceBlueprint()
                            .equals(sequenceBlueprint))) continue;

            // 2. Apply the sequence for the first time to check the event or leave it to be garbage collected.

            Sequence<?, ?> sequence = sequenceBlueprint.create(entry);

            // Fire SequenceStartEvent

            if (sequence.apply(entry, event)) {
                if (sequence.isCancelled()) {
                    continue;
                }

                if (sequence.isFinished()) {
                    if (sequence.isRunning()) {
                        for (Capture<?, ?> capture : sequence.getCaptureRegistry()) {
                            capture.stop(entry, sequence.getCaptureRegistry().getContainer());
                        }
                    }

                    // Fire SequenceFinishEvent.

                    continue;
                }

                this.sequences.put(entry, sequence);
            }
        }
    }

    @Override
    public void clean(boolean force) {
        GuardianSequenceManager.this.sequences.keys().forEach(player -> GuardianSequenceManager.this.clean(player, force));
    }

    @Override
    public void clean(@Nonnull EntityEntry entry, boolean force) {
        if (force) GuardianSequenceManager.this.sequences.removeAll(entry);
        else GuardianSequenceManager.this.sequences.entries().forEach((el) -> {
            if (el.getKey().equals(entry) && el.getValue().isExpired())
                GuardianSequenceManager.this.sequences.remove(el.getKey(), el.getValue());
        });
    }

    public void update() {
        GuardianSequenceManager.this.sequences.forEach((entry, playerSequence) -> {
            if (playerSequence.isRunning()) {
                for (Capture capture : playerSequence.getCaptureRegistry()) {
                    capture.update(entry, playerSequence.getCaptureRegistry().getContainer());
                }
            }
        });
    }

    public static class SequenceTask {

        private final GuardianPlugin plugin;
        private final GuardianSequenceManager sequenceManager;

        private Task task;

        public SequenceTask(GuardianPlugin plugin, GuardianSequenceManager sequenceManager) {
            this.plugin = plugin;
            this.sequenceManager = sequenceManager;
        }

        public void start() {
            this.task = Task.builder().execute(() -> {
                this.sequenceManager.clean(false);
                this.sequenceManager.update();
            }).name("SequenceTask").intervalTicks(1).submit(this.plugin);
        }

        public void stop() {
            if (this.task != null) this.task.cancel();
        }

    }

}