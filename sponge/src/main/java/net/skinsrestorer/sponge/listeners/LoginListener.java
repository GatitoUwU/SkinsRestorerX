/*
 * SkinsRestorer
 *
 * Copyright (C) 2022 SkinsRestorer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 */
package net.skinsrestorer.sponge.listeners;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.skinsrestorer.shared.listeners.LoginProfileEvent;
import net.skinsrestorer.shared.listeners.LoginProfileListener;
import net.skinsrestorer.sponge.SkinsRestorer;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.entity.living.player.User;
import org.spongepowered.api.event.EventListener;
import org.spongepowered.api.event.network.ServerSideConnectionEvent;
import org.spongepowered.api.profile.GameProfile;

@RequiredArgsConstructor
@Getter
public class LoginListener extends LoginProfileListener implements EventListener<ServerSideConnectionEvent.Login> {
    private final SkinsRestorer plugin;

    @Override
    public void handle(@NotNull ServerSideConnectionEvent.Login event) {
        LoginProfileEvent wrapped = wrap(event);
        if (handleSync(wrapped))
            return;

        GameProfile profile = event.profile();

        profile.name().flatMap(name -> handleAsync(wrapped)).ifPresent(result ->
                plugin.getSkinApplierSponge().updateProfileSkin(event.user(), result));
    }

    private LoginProfileEvent wrap(ServerSideConnectionEvent.Login event) {
        return new LoginProfileEvent() {
            @Override
            public boolean isOnline() {
                return Sponge.server().isOnlineModeEnabled();
            }

            @Override
            public String getPlayerName() {
                return event.profile().name().orElseThrow(() -> new RuntimeException("Could not get player name!"));
            }

            @Override
            public boolean isCancelled() {
                return event.isCancelled();
            }
        };
    }
}
