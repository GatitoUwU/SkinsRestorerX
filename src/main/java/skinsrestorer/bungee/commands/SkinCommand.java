package skinsrestorer.bungee.commands;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.CommandHelp;
import co.aikar.commands.annotation.*;
import co.aikar.commands.contexts.OnlineProxiedPlayer;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import skinsrestorer.bungee.SkinApplier;
import skinsrestorer.bungee.SkinsRestorer;
import skinsrestorer.shared.storage.Config;
import skinsrestorer.shared.storage.CooldownStorage;
import skinsrestorer.shared.storage.Locale;
import skinsrestorer.shared.storage.SkinStorage;
import skinsrestorer.shared.utils.C;
import skinsrestorer.shared.utils.MojangAPI;
import skinsrestorer.shared.utils.MojangAPI.SkinRequestException;

import java.util.concurrent.TimeUnit;

@CommandAlias("skin") @CommandPermission("%skin")
public class SkinCommand extends BaseCommand {
    @HelpCommand
    public static void onHelp(CommandSender sender, CommandHelp help) {
        sender.sendMessage(new TextComponent("SkinsRestorer Help"));
        help.showHelp();
    }


    @Subcommand("clear") @CommandPermission("%skinClear")
    @Description("Clears your skin.")
    public void onSkinClear(ProxiedPlayer p) {
        this.onSkinClearOther(p, new OnlineProxiedPlayer(p));
    }

    @Subcommand("clear") @CommandPermission("%skinClearOther")
    @CommandCompletion("@players")
    @Description("Clears the skin of another player.")
    public void onSkinClearOther(CommandSender sender, OnlineProxiedPlayer target) {
        ProxyServer.getInstance().getScheduler().runAsync(SkinsRestorer.getInstance(), () -> {
            ProxiedPlayer p = target.getPlayer();
            String skin = SkinStorage.getDefaultSkinNameIfEnabled(p.getName(), true);

            // remove users custom skin and set default skin / his skin
            SkinStorage.removePlayerSkin(p.getName());
            if (this.setSkin(p, skin, false)) {
                p.sendMessage(new TextComponent(Locale.SKIN_CLEAR_SUCCESS));
                if (!sender.getName().equals(target.getPlayer().getName()))
                    sender.sendMessage(new TextComponent(Locale.SKIN_CLEAR_ISSUER.replace("%player", target.getPlayer().getName())));
            }
        });
    }


    @Subcommand("update") @CommandPermission("%skinUpdate")
    @Description("Updates your skin.")
    public void onSkinUpdate(ProxiedPlayer p) {
        this.onSkinUpdateOther(p, new OnlineProxiedPlayer(p));
    }

    @Subcommand("update") @CommandPermission("%skinUpdateOther")
    @CommandCompletion("@players")
    @Description("Updates the skin of another player.")
    public void onSkinUpdateOther(CommandSender sender, OnlineProxiedPlayer target) {
        ProxyServer.getInstance().getScheduler().runAsync(SkinsRestorer.getInstance(), () -> {
            ProxiedPlayer p = target.getPlayer();
            String skin = SkinStorage.getPlayerSkin(p.getName());

            // User has no custom skin set, get the default skin name / his skin
            if (skin == null)
                skin = SkinStorage.getDefaultSkinNameIfEnabled(p.getName(), true);

            if (!SkinStorage.forceUpdateSkinData(skin)) {
                p.sendMessage(new TextComponent(Locale.ERROR_UPDATING_SKIN));
                return;
            }

            if (this.setSkin(p, skin, false)) {
                p.sendMessage(new TextComponent(Locale.SUCCESS_UPDATING_SKIN));
                if (!sender.getName().equals(target.getPlayer().getName())) {
                    sender.sendMessage(new TextComponent(Locale.SUCCESS_UPDATING_SKIN_OTHER.replace("%player", target.getPlayer().getName())));
                }
            }
        });
    }


    @Subcommand("set") @CommandPermission("%skinSet")
    @Description("Sets your skin.")
    public void onSkinSet(ProxiedPlayer p, String skin) {
        this.onSkinSetOther(p, new OnlineProxiedPlayer(p), skin);
    }

    @Subcommand("set") @CommandPermission("%skinSetOther")
    @CommandCompletion("@players")
    @Description("Sets the skin of another player.")
    public void onSkinSetOther(CommandSender sender, OnlineProxiedPlayer target, String skin) {
        ProxyServer.getInstance().getScheduler().runAsync(SkinsRestorer.getInstance(), () -> {
            this.setSkin(target.getPlayer(), skin);
            if (!sender.getName().equals(target.getPlayer().getName())) {
                sender.sendMessage(new TextComponent(Locale.ADMIN_SET_SKIN.replace("%player", target.getPlayer().getName())));
            }
        });
    }


    @CatchUnknown @CommandPermission("%skinSet")
    public void onDefault(ProxiedPlayer p, String[] args) {
        this.onSkinSetOther(p, new OnlineProxiedPlayer(p), args[0]);
    }


    private void setSkin(ProxiedPlayer p, String skin) {
        this.setSkin(p, skin, true);
    }

    // if save is false, we won't save the skin skin name
    // because default skin names shouldn't be saved as the users custom skin
    private boolean setSkin(ProxiedPlayer p, String skin, boolean save) {
        if (!C.validUsername(skin)) {
            p.sendMessage(new TextComponent(Locale.INVALID_PLAYER.replace("%player", skin)));
            return false;
        }

        if (Config.DISABLED_SKINS_ENABLED)
            if (!p.hasPermission("skinsrestorer.bypassdisabled")) {
                for (String dskin : Config.DISABLED_SKINS)
                    if (skin.equalsIgnoreCase(dskin)) {
                        p.sendMessage(new TextComponent(Locale.SKIN_DISABLED));
                        return false;
                    }
            }

        if (!p.hasPermission("skinsrestorer.bypasscooldown") && CooldownStorage.hasCooldown(p.getName())) {
            p.sendMessage(new TextComponent(Locale.SKIN_COOLDOWN_NEW.replace("%s", "" + CooldownStorage.getCooldown(p.getName()))));
            return false;
        }

        CooldownStorage.resetCooldown(p.getName());
        CooldownStorage.setCooldown(p.getName(), Config.SKIN_CHANGE_COOLDOWN, TimeUnit.SECONDS);

        String oldSkinName = SkinStorage.getPlayerSkin(p.getName());
        try {
            MojangAPI.getUUID(skin);
            if (save) {
                SkinStorage.setPlayerSkin(p.getName(), skin);
                SkinApplier.applySkin(p);
            } else {
                SkinApplier.applySkin(p, skin, null);
            }
            p.sendMessage(new TextComponent(Locale.SKIN_CHANGE_SUCCESS));
            return true;
        } catch (SkinRequestException e) {
            p.sendMessage(new TextComponent(e.getReason()));

            // set custom skin name back to old one if there is an exception
            this.rollback(p, oldSkinName, save);
        } catch (Exception e) {
            e.printStackTrace();

            // set custom skin name back to old one if there is an exception
            this.rollback(p, oldSkinName, save);
        }
        return false;
    }

    private void rollback(ProxiedPlayer p, String oldSkinName, boolean save) {
        if (save)
            SkinStorage.setPlayerSkin(p.getName(), oldSkinName != null ? oldSkinName : p.getName());
    }
}
