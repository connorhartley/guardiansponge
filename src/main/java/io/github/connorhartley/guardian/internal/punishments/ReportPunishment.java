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
package io.github.connorhartley.guardian.internal.punishments;

import io.github.connorhartley.guardian.Guardian;
import io.github.connorhartley.guardian.data.DataKeys;
import io.github.connorhartley.guardian.detection.Detection;
import io.github.connorhartley.guardian.detection.punishment.Punishment;
import io.github.connorhartley.guardian.detection.punishment.PunishmentReport;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.channel.MessageChannel;
import org.spongepowered.api.text.format.TextColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ReportPunishment implements Punishment {

    private MessageChannel reportChannel;

    private final Guardian plugin;
    private final Detection<?, ?> detection;

    public ReportPunishment(Guardian plugin, Detection<?, ?> detection) {
        this.plugin = plugin;
        this.detection = detection;

        this.reportChannel = MessageChannel.permission("guardian.punishment.report-channel." + detection.getId());
    }

    @Override
    public String getName() {
        return "report";
    }

    @Override
    public Optional<Detection<?, ?>> getDetection() {
        return Optional.ofNullable(this.detection);
    }

    @Override
    public boolean handle(String[] args, User user, PunishmentReport punishmentReport) {
        List<Punishment> punishments = new ArrayList<>();
        if (user.get(DataKeys.GUARDIAN_PUNISHMENT_TAG).isPresent()) {
            punishments.addAll(user.get(DataKeys.GUARDIAN_PUNISHMENT_TAG).get());
        }

        punishments.add(this);

        user.offer(DataKeys.GUARDIAN_PUNISHMENT_TAG, punishments);

        Double probability = punishmentReport.getSeverityTransformer().transform(0d) * 100;

        if (user.getPlayer().isPresent()) {
            this.reportChannel.send(this.plugin, Text.of(Guardian.GUARDIAN_PREFIX, TextColors.GRAY, "The player ",
                    TextColors.DARK_AQUA, user.getPlayer().get(), TextColors.GRAY, " has been found illegally ",
                    TextColors.DARK_AQUA, punishmentReport.getDetectionType(), TextColors.GRAY, " with a certainty of ",
                    TextColors.DARK_AQUA, "%", probability.intValue(), TextColors.GRAY, ". This has been reported to an administrator."));

            return true;
        }
        return false;
    }
}
