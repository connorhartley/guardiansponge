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

import com.google.common.reflect.TypeToken;
import io.github.connorhartley.guardian.Guardian;
import io.github.connorhartley.guardian.detection.Detection;
import io.github.connorhartley.guardian.detection.check.Check;
import io.github.connorhartley.guardian.detection.check.CheckType;
import io.github.connorhartley.guardian.internal.contexts.player.PlayerControlContext;
import io.github.connorhartley.guardian.internal.contexts.player.PlayerLocationContext;
import io.github.connorhartley.guardian.internal.contexts.world.MaterialSpeedContext;
import io.github.connorhartley.guardian.sequence.SequenceBlueprint;
import io.github.connorhartley.guardian.sequence.SequenceBuilder;
import io.github.connorhartley.guardian.sequence.SequenceReport;
import io.github.connorhartley.guardian.sequence.condition.ConditionResult;
import io.github.connorhartley.guardian.storage.container.StorageKey;
import io.github.connorhartley.guardian.util.check.CommonMovementConditions;
import io.github.connorhartley.guardian.util.check.PermissionCheckCondition;
import org.spongepowered.api.data.key.Keys;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.entity.MoveEntityEvent;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;

import java.util.Map;

public class JesusCheck extends Check {

    JesusCheck(CheckType checkType, User user) {
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
        private double threshold = 1.35;
        private double minimumWaterTime = 0;
        private double minimumTickRange = 30;
        private double maximumTickRange = 50;

        public Type(Detection detection) {
            this.detection = detection;

            if (this.detection.getConfiguration().get(new StorageKey<>("analysis-time"), new TypeToken<Double>(){}).isPresent()) {
                this.analysisTime = this.detection.getConfiguration().get(new StorageKey<>("analysis-time"),
                        new TypeToken<Double>(){}).get().getValue() / 0.05;
            }

            if (this.detection.getConfiguration().get(new StorageKey<>("threshold"), new TypeToken<Double>() {}).isPresent()) {
                this.threshold = this.detection.getConfiguration().get(new StorageKey<>("threshold"),
                        new TypeToken<Double>() {}).get().getValue();
            }

            if (this.detection.getConfiguration().get(new StorageKey<>("minimum-water-time"), new TypeToken<Double>() {}).isPresent()) {
                this.minimumWaterTime = this.detection.getConfiguration().get(new StorageKey<>("minimum-water-time"),
                        new TypeToken<Double>() {}).get().getValue();
            }

            if (this.detection.getConfiguration().get(new StorageKey<>("tick-bounds"), new TypeToken<Map<String, Double>>(){}).isPresent()) {
                this.minimumTickRange = this.analysisTime * this.detection.getConfiguration().get(new StorageKey<>("tick-bounds"),
                        new TypeToken<Map<String, Double>>(){}).get().getValue().get("min");
                this.maximumTickRange = this.analysisTime * this.detection.getConfiguration().get(new StorageKey<>("tick-bounds"),
                        new TypeToken<Map<String, Double>>(){}).get().getValue().get("max");
            }
        }

        @Override
        public Detection getDetection() {
            return this.detection;
        }

        @Override
        public SequenceBlueprint getSequence() {
            return new SequenceBuilder()

                    .capture(
                            new PlayerLocationContext((Guardian) this.getDetection().getPlugin(), this.getDetection()),
                            new PlayerControlContext.HorizontalSpeed((Guardian) this.getDetection().getPlugin(), this.getDetection()),
                            new MaterialSpeedContext((Guardian) this.getDetection().getPlugin(), this.getDetection()),
                            new PlayerControlContext.InvalidMove((Guardian) this.getDetection().getPlugin(), this.getDetection())
                    )

                    // Trigger : Move Entity Event

                    .action(MoveEntityEvent.class)

                    // After 2 Seconds : Move Entity Event

                    .action(MoveEntityEvent.class)
                            .delay(((Double) this.analysisTime).intValue())
                            .expire(((Double) this.maximumTickRange).intValue())

                            /*
                             * Cancels the sequence if the player being tracked, dies, teleports,
                             * teleports through Nucleus and mounts or dismounts a vehicle. This
                             * is due to the location comparison at the beginning and end of a check
                             * which these events change the behaviour of.
                             */
                            .failure(new CommonMovementConditions.DeathCondition(this.detection))
                            .failure(new CommonMovementConditions.NucleusTeleportCondition(this.detection))
                            .failure(new CommonMovementConditions.VehicleMountCondition(this.detection))
                            .condition(new CommonMovementConditions.TeleportCondition(this.detection))

                            // Does the player have permission?
                            .condition(new PermissionCheckCondition(this.detection))

                            .condition((user, event, contextValuation, sequenceReport, lastAction) -> {
                                SequenceReport.Builder report = SequenceReport.builder().of(sequenceReport);

                                Guardian plugin = (Guardian) this.getDetection().getPlugin();

                                Location<World> start = null;
                                Location<World> present = null;

                                long currentTime;
                                long playerControlTicks = 0;
                                long blockModifierTicks = 0;

                                double playerControlSpeed = 1.0;
                                double blockModifier = 1.0;
                                double playerControlModifier = 4.0;

                                int materialLiquid = 0;

                                PlayerControlContext.HorizontalSpeed.State playerControlState = PlayerControlContext.HorizontalSpeed.State.WALKING;

                                if (contextValuation.<PlayerLocationContext, Location<World>>get(PlayerLocationContext.class, "start_location").isPresent()) {
                                    start = contextValuation.<PlayerLocationContext, Location<World>>get(PlayerLocationContext.class, "start_location").get();
                                }

                                if (contextValuation.<PlayerLocationContext, Location<World>>get(PlayerLocationContext.class, "present_location").isPresent()) {
                                    present = contextValuation.<PlayerLocationContext, Location<World>>get(PlayerLocationContext.class, "present_location").get();
                                }

                                if (contextValuation.<PlayerControlContext.HorizontalSpeed, Double>get(
                                        PlayerControlContext.HorizontalSpeed.class, "horizontal_control_speed").isPresent()) {
                                    playerControlSpeed = contextValuation.<PlayerControlContext.HorizontalSpeed, Double>get(
                                            PlayerControlContext.HorizontalSpeed.class, "horizontal_control_speed").get();
                                }

                                if (contextValuation.<PlayerControlContext.HorizontalSpeed, Integer>get(
                                        PlayerControlContext.HorizontalSpeed.class, "update").isPresent()) {
                                    playerControlTicks = contextValuation.<PlayerControlContext.HorizontalSpeed, Integer>get(
                                            PlayerControlContext.HorizontalSpeed.class, "update").get();
                                }

                                if (contextValuation.<MaterialSpeedContext, Double>get(MaterialSpeedContext.class, "speed_amplifier").isPresent()) {
                                    blockModifier = contextValuation.<MaterialSpeedContext, Double>get(MaterialSpeedContext.class, "speed_amplifier").get();
                                }

                                if (contextValuation.<MaterialSpeedContext, Integer>get(MaterialSpeedContext.class, "amplifier_material_liquid").isPresent()) {
                                    materialLiquid = contextValuation.<MaterialSpeedContext, Integer>get(MaterialSpeedContext.class, "amplifier_material_liquid").get();
                                }

                                if (contextValuation.<MaterialSpeedContext, Integer>get(MaterialSpeedContext.class, "update").isPresent()) {
                                    blockModifierTicks = contextValuation.<MaterialSpeedContext, Integer>get(MaterialSpeedContext.class, "update").get();
                                }

                                if (contextValuation.<PlayerControlContext.HorizontalSpeed, Double>get(
                                        PlayerControlContext.HorizontalSpeed.class, "control_modifier").isPresent()) {
                                    playerControlModifier = contextValuation.<PlayerControlContext.HorizontalSpeed, Double>get(
                                            PlayerControlContext.HorizontalSpeed.class, "control_modifier").get();
                                }

                                if (contextValuation.<PlayerControlContext.HorizontalSpeed, PlayerControlContext.HorizontalSpeed.State>get(
                                        PlayerControlContext.HorizontalSpeed.class, "control_speed_state").isPresent()) {
                                    playerControlState = contextValuation.<PlayerControlContext.HorizontalSpeed, PlayerControlContext.HorizontalSpeed.State>get(
                                            PlayerControlContext.HorizontalSpeed.class, "control_speed_state").get();
                                }

                                if (playerControlTicks < this.minimumTickRange || blockModifierTicks < this.minimumTickRange) {
                                    plugin.getLogger().warn("The server may be overloaded. A detection check has been skipped as it is less than a second and a half behind.");
                                    return new ConditionResult(false, report.build(false));
                                } else if (playerControlTicks > this.maximumTickRange || blockModifierTicks > this.maximumTickRange) {
                                    return new ConditionResult(false, report.build(false));
                                }

                                if (user.getPlayer().isPresent() && start != null && present != null) {

                                    currentTime = System.currentTimeMillis();

                                    if (user.getPlayer().get().get(Keys.VEHICLE).isPresent()) {
                                        return new ConditionResult(false, report.build(false));
                                    }

                                    if (user.getPlayer().get().get(Keys.CAN_FLY).isPresent()) {
                                        if (user.getPlayer().get().get(Keys.CAN_FLY).get()) {
                                            return new ConditionResult(false, report.build(false));
                                        }
                                    }

                                    double travelDisplacement = Math.abs(Math.sqrt((
                                            (present.getX() - start.getX()) *
                                                    (present.getX() - start.getX())) +
                                            (present.getZ() - start.getZ()) *
                                                    (present.getZ() - start.getZ())));

                                    travelDisplacement += playerControlModifier / 2;

                                    double waterTime = materialLiquid * (((
                                            ((1 / ((playerControlTicks + blockModifierTicks) / 2)) *
                                                    ((long) this.analysisTime * 1000)) + (currentTime - lastAction)) / 2) / 1000) * 0.05;

                                    double maximumSpeed = playerControlSpeed * blockModifier * (((
                                            ((1 / ((playerControlTicks + blockModifierTicks) / 2)) *
                                                    ((long) this.analysisTime * 1000)) + (currentTime - lastAction)) / 2) / 1000) + 0.01;

                                    report
                                            .information("Horizontal travel speed should be less than " + maximumSpeed +
                                                    " while they're " + playerControlState.name() + ".");

                                    if (travelDisplacement > maximumSpeed && waterTime > this.minimumWaterTime &&
                                            (travelDisplacement - maximumSpeed) > this.threshold) {
                                        report
                                                .information("Overshot maximum speed by " + (travelDisplacement - maximumSpeed) + ".")
                                                .type("walking on water (jesus)")
                                                .severity(travelDisplacement - maximumSpeed);

                                        // TODO : Remove this after testing \/
                                        plugin.getLogger().warn(user.getName() + " has triggered the horizontal speed check and overshot " +
                                                "the maximum speed by " + (travelDisplacement - maximumSpeed) + ".");

                                        return new ConditionResult(true, report.build(true));
                                    }
                                }
                                return new ConditionResult(false, sequenceReport);
                            })

                    .build(this);
        }

        @Override
        public Check createInstance(User user) {
            return new HorizontalSpeedCheck(this, user);
        }
    }

}