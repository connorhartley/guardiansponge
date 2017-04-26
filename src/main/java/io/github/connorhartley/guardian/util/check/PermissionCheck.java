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
package io.github.connorhartley.guardian.util.check;

import io.github.connorhartley.guardian.sequence.context.ContextValuation;
import io.github.connorhartley.guardian.detection.Detection;
import io.github.connorhartley.guardian.sequence.SequenceReport;
import io.github.connorhartley.guardian.sequence.condition.Condition;
import io.github.connorhartley.guardian.sequence.condition.ConditionResult;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.Event;

public class PermissionCheck implements Condition {

    private final Detection detection;

    public PermissionCheck(Detection detection) {
        this.detection = detection;
    }

    @Override
    public ConditionResult test(User user, Event event, ContextValuation contextValuation, SequenceReport sequenceReport, long lastAction) {
        if (user.getPlayer().isPresent()) {
            if (!user.getPlayer().get().hasPermission(this.detection.getPermission(Detection.PermissionTarget.BYPASS))) {
                return new ConditionResult(true, sequenceReport);
            }
        }
        return new ConditionResult(false, sequenceReport);
    }

}