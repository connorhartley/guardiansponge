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
package io.ichorpowered.guardian.internal.check.movement;

import com.google.common.reflect.TypeToken;
import com.ichorpowered.guardian.api.detection.Detection;
import com.ichorpowered.guardian.api.detection.DetectionConfiguration;
import com.ichorpowered.guardian.api.detection.check.Check;
import com.ichorpowered.guardian.api.detection.check.CheckBlueprint;
import com.ichorpowered.guardian.api.event.origin.Origin;
import com.ichorpowered.guardian.api.sequence.SequenceBlueprint;
import io.github.connorhartley.guardian.GuardianPlugin;
import io.ichorpowered.guardian.internal.capture.PlayerControlCapture;
import io.ichorpowered.guardian.sequence.GuardianSequenceBuilder;
import io.ichorpowered.guardian.sequence.SequenceReport;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Collection;
import java.util.Collections;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class VerticalSpeedCheck implements Check<GuardianPlugin, DetectionConfiguration> {

    private final CheckBlueprint<GuardianPlugin, DetectionConfiguration> checkBlueprint;
    private final Detection<GuardianPlugin, DetectionConfiguration> detection;

    private double analysisTime = 40;
    private double minimumTickRange = 30;
    private double maximumTickRange = 50;

    public VerticalSpeedCheck(CheckBlueprint<GuardianPlugin, DetectionConfiguration> checkBlueprint,
                              Detection<GuardianPlugin, DetectionConfiguration> detection) {
        this.checkBlueprint = checkBlueprint;
        this.detection = detection;

        this.analysisTime = this.detection.getConfiguration().getStorage().getNode("analysis", "time").getDouble(2d) / 0.05;
        this.minimumTickRange = this.analysisTime * this.detection.getConfiguration().getStorage().getNode("analysis", "range", "minimum").getDouble(0.75);
        this.maximumTickRange = this.analysisTime * this.detection.getConfiguration().getStorage().getNode("analysis", "range", "maximum").getDouble(1.25);
    }

    @Nonnull
    @Override
    public GuardianPlugin getOwner() {
        return this.detection.getOwner();
    }

    @Nonnull
    @Override
    public Detection<GuardianPlugin, DetectionConfiguration> getDetection() {
        return this.detection;
    }

    @Nonnull
    @Override
    public CheckBlueprint<GuardianPlugin, DetectionConfiguration> getCheckBlueprint() {
        return this.checkBlueprint;
    }

    @Nonnull
    @Override
    public SequenceBlueprint<GuardianPlugin, DetectionConfiguration> getSequence() {
        return new GuardianSequenceBuilder<GuardianPlugin, DetectionConfiguration>()
                .capture(
                        new PlayerLocationCapture<>(this.detection.getOwner(), this.detection),
                        new PlayerControlCapture.Common<>(this.detection.getOwner(), this.detection)
                )

                // Trigger : Move Entity Event

                .action(MoveEntityEvent.class)

                // After : Move Entity Event

                .action(MoveEntityEvent.class)
                .delay(Double.valueOf(this.analysisTime).intValue())
                .expire(Double.valueOf(this.maximumTickRange).intValue())

                // TODO: Permission check.

                .condition((entityEntry, event, captureContainer, summary, last) -> {
                    summary.set(SequenceReport.class, new SequenceReport(false, Origin.source(event).owner(entityEntry).build()));

                    if (!entityEntry.getEntity(TypeToken.of(Player.class)).isPresent()) return summary;
                    Player player = entityEntry.getEntity(TypeToken.of(Player.class)).get();

                        /*
                         * Capture Collection
                         */

                    Integer locationTicks = captureContainer.get(PlayerLocationCapture.UPDATE);
                    Location<World> present = captureContainer.get(PlayerLocationCapture.PRESET_LOCATION);
                    Location<World> initial = captureContainer.get(PlayerLocationCapture.INITIAL_LOCATION);

                    Integer verticalTicks = captureContainer.get(PlayerControlCapture.Common.UPDATE);
                    Double verticalOffset = captureContainer.get(PlayerControlCapture.Common.VERTICAL_OFFSET);

                        /*
                         * Analysis
                         */

                    if (locationTicks == null || initial == null || present == null || verticalTicks == null
                            || verticalOffset == null) return summary;

                    if (locationTicks < this.minimumTickRange) {
                        this.getOwner().getLogger().warn("The server may be overloaded. A check could not be completed.");
                        return summary;
                    } else if (locationTicks > this.maximumTickRange) {
                        return summary;
                    }

                    if (player.get(Keys.VEHICLE).isPresent()) return summary;

                    long current = System.currentTimeMillis();

                    double verticalDisplacement = Math.abs(present.getY() - initial.getY());

                    double maximumVerticalDisplacement = (verticalOffset *
                            (verticalOffset / 0.2)) * (((
                            ((1 / verticalTicks) * ((long) this.analysisTime * 1000)) + (current - last)
                    ) / 2) / 1000) + 0.01;

                    if (verticalDisplacement > maximumVerticalDisplacement && present.getY() - initial.getY() > 0) {
                        SequenceReport report = new SequenceReport(true, Origin.source(event).owner(entityEntry).build());
                        report.put("type", "Vertical Speed");

                        report.put("information", Collections.singletonList(
                                "Overshot maximum movement by " + (verticalDisplacement - maximumVerticalDisplacement) + ".")
                        );

                        report.put("initial_location", initial);
                        report.put("final_location", present);
                        report.put("severity", (verticalDisplacement - maximumVerticalDisplacement) / verticalDisplacement);

                        if (present.getY() - initial.getY() < 0) {
                            report.put("information", report.<Collection<String>>get("information").add(
                                    "Falling entity instead of climbing."
                            ));
                        }

                        summary.set(SequenceReport.class, report);
                    }

                    return summary;
                })
                .build(this.detection.getOwner(), this);
    }

    @Override
    public boolean compare(@Nullable Check<?, ?> check) {
        assert check != null;
        return check.equals(this);
    }

    public static class Blueprint implements CheckBlueprint<GuardianPlugin, DetectionConfiguration> {

        @Override
        public Check<GuardianPlugin, DetectionConfiguration> create(Detection<GuardianPlugin, DetectionConfiguration> detection) {
            return new VerticalSpeedCheck(this, detection);
        }

        @Override
        public Class<? extends Check> getCheckClass() {
            return VerticalSpeedCheck.class;
        }

    }

}
