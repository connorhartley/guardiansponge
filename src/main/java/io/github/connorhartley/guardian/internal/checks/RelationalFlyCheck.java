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
package io.github.connorhartley.guardian.internal.checks;

import io.github.connorhartley.guardian.Guardian;
import io.github.connorhartley.guardian.context.ContextBuilder;
import io.github.connorhartley.guardian.context.ContextTypes;
import io.github.connorhartley.guardian.context.container.ContextContainer;
import io.github.connorhartley.guardian.detection.Detection;
import io.github.connorhartley.guardian.detection.check.Check;
import io.github.connorhartley.guardian.detection.check.CheckType;
import io.github.connorhartley.guardian.internal.contexts.player.PlayerLocationContext;
import io.github.connorhartley.guardian.internal.contexts.player.PlayerPositionContext;
import io.github.connorhartley.guardian.sequence.SequenceBlueprint;
import io.github.connorhartley.guardian.sequence.SequenceBuilder;
import io.github.connorhartley.guardian.sequence.condition.ConditionResult;
import io.github.connorhartley.guardian.sequence.report.ReportType;
import io.github.connorhartley.guardian.sequence.report.SequenceReport;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class RelationalFlyCheck extends Check {

    public RelationalFlyCheck(CheckType checkType, User user) {
        super(checkType, user);
        this.setChecking(true);
    }

    @Override
    public void update() {}

    @Override
    public void finish() {
        this.setChecking(false);
    }

    public static class Type implements CheckType {

        private final Detection detection;

        private double analysisTime = 40;
        private double minimumTickRange = 30;
        private double maximumTickRange = 50;
        private double amplitudeMinor = 1.4;
        private double amplitudeMean = 2.3;
        private double amplitudeMajor = 3;

        public Type(Detection detection) {
            this.detection = detection;

            System.out.println("Test.");

            if (this.detection.getConfiguration().get("analysis-time", 2.0).isPresent()) {
                this.analysisTime = this.detection.getConfiguration().get("analysis-time", 2.0).get().getValue() / 0.05;
            }

            if (this.detection.getConfiguration().get("tick-bounds", new HashMap<String, Double>()).isPresent()) {
                this.minimumTickRange = this.analysisTime * this.detection.getConfiguration().get("tick-bounds",
                        new HashMap<String, Double>()).get().getValue().get("min");
                this.maximumTickRange = this.analysisTime * this.detection.getConfiguration().get("tick-bounds",
                        new HashMap<String, Double>()).get().getValue().get("max");
            }

            if (this.detection.getConfiguration().get("distance-amplitude", new HashMap<String, Double>()).isPresent()) {
                Map<String, Double> storageValueMap = this.detection.getConfiguration().get("distance-amplitude",
                        new HashMap<String, Double>()).get().getValue();

                this.amplitudeMinor = storageValueMap.get("minor");
                this.amplitudeMean = storageValueMap.get("mean");
                this.amplitudeMajor = storageValueMap.get("major");
            }
        }

        @Override
        public Detection getDetection() {
            return this.detection;
        }

        @Override
        public ContextBuilder getContextTracker() {
            return ContextBuilder.builder()
                    .append(PlayerLocationContext.class)
                    .append(PlayerPositionContext.Altitude.class)
                    .build();
        }

        @Override
        public SequenceBlueprint getSequence() {
            return new SequenceBuilder()

                    .context(this.getDetection().getContextProvider(), this.getContextTracker())

                    .action(MoveEntityEvent.class)

                    .action(MoveEntityEvent.class)
                    .delay(((Double) this.analysisTime).intValue())
                    .expire(((Double) this.maximumTickRange).intValue())

                    .condition((user, event, contextContainer, sequenceReport, lastAction) -> {
                        if (!user.hasPermission("guardian.detection.fly.exempt")) {
                            return new ConditionResult(true, sequenceReport);
                        }
                        return new ConditionResult(false, sequenceReport);
                    })

                    .success((user, event, contextContainers, sequenceReport, lastAction) -> {
                        Guardian plugin = (Guardian) this.detection.getPlugin();

                        Location<World> start = null;
                        Location<World> present = null;

                        long currentTime;
                        long playerAltitudeGainTicks = 0;
                        double playerAltitudeGain = 0;

                        for (ContextContainer contextContainer : contextContainers) {
                            if (contextContainer.get(ContextTypes.START_LOCATION).isPresent()) {
                                start = contextContainer.get(ContextTypes.START_LOCATION).get();
                            }

                            if (contextContainer.get(ContextTypes.PRESENT_LOCATION).isPresent()) {
                                present = contextContainer.get(ContextTypes.PRESENT_LOCATION).get();
                            }

                            if (contextContainer.get(ContextTypes.GAINED_ALTITUDE).isPresent()) {
                                playerAltitudeGainTicks = contextContainer.getContext().updateAmount();
                                playerAltitudeGain = contextContainer.get(ContextTypes.GAINED_ALTITUDE).get();
                            }
                        }

                        if (playerAltitudeGainTicks < this.minimumTickRange) {
                            plugin.getLogger().warn("The server may be overloaded. A detection check has been skipped as it is less than a second and a half behind.");
                            SequenceReport failReport = SequenceReport.of(sequenceReport)
                                    .append(ReportType.TEST, false)
                                    .build();

                            return new ConditionResult(false, failReport);
                        } else if (playerAltitudeGainTicks > this.maximumTickRange) {
                            SequenceReport failReport = SequenceReport.of(sequenceReport)
                                    .append(ReportType.TEST, false)
                                    .build();

                            return new ConditionResult(false, failReport);
                        }

                        if (user.getPlayer().isPresent() && start != null && present != null) {
                            // ### For correct movement context ###
                            if (user.getPlayer().get().get(Keys.IS_SITTING).isPresent()) {
                                if (user.getPlayer().get().get(Keys.IS_SITTING).get()) {
                                    SequenceReport failReport = SequenceReport.of(sequenceReport)
                                            .append(ReportType.TEST, false)
                                            .build();

                                    return new ConditionResult(false, failReport);
                                }
                            }
                            // ####################################

                            currentTime = System.currentTimeMillis();

                            if (user.getPlayer().get().get(Keys.CAN_FLY).isPresent()) {
                                if (user.getPlayer().get().get(Keys.CAN_FLY).get()) {
                                    SequenceReport failReport = SequenceReport.of(sequenceReport)
                                            .append(ReportType.TEST, false)
                                            .build();

                                    return new ConditionResult(false, failReport);
                                }
                            }

                            long contextTime = (1 / playerAltitudeGainTicks) * ((long) this.analysisTime * 1000);
                            long sequenceTime = (currentTime - lastAction);

                            double travelDisplacement = Math.abs(Math.sqrt((
                                    (present.getX() - start.getX()) *
                                            (present.getX() - start.getX())) +
                                    (present.getZ() - start.getZ()) *
                                            (present.getZ() - start.getZ())));

                            double position;

                            if (travelDisplacement > this.amplitudeMean) {
                                position = (playerAltitudeGain / travelDisplacement) / (((contextTime + sequenceTime) / 2) / 1000);
                            } else {
                                position = (playerAltitudeGain / this.amplitudeMean) / (((contextTime + sequenceTime) / 2) / 1000);
                            }

                            System.out.println("Position: " + position);

                            SequenceReport.Builder successReportBuilder = SequenceReport.of(sequenceReport)
                                    .append(ReportType.INFORMATION, "Player position in time should be " + position +
                                            ".");

                            if (playerAltitudeGain > this.amplitudeMajor && (position * this.amplitudeMajor) >
                                    this.amplitudeMinor) {
                                successReportBuilder.append(ReportType.TEST, true)
                                        .append(ReportType.INFORMATION, "Overshot maximum speed by " +
                                                ((position * this.amplitudeMajor) - this.amplitudeMinor) + ".")
                                        .append(ReportType.SEVERITY, (position * this.amplitudeMajor) - this.amplitudeMinor);

                                // TODO : Remove this after testing \/
                                plugin.getLogger().warn(user.getName() + " has triggered the fly check and overshot " +
                                        "the maximum speed by " + ((position * this.amplitudeMajor) - this.amplitudeMinor) + ".");
                            } else {
                                successReportBuilder.append(ReportType.TEST, false);
                            }

                            return new ConditionResult(true, successReportBuilder.build());
                        }

                        return new ConditionResult(false, sequenceReport);
                    })

                    .build(this);
        }

        @Override
        public Check createInstance(User user) {
            return new RelationalFlyCheck(this, user);
        }
    }
}
