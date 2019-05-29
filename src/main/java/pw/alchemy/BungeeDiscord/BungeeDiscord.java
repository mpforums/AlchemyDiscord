package pw.alchemy.BungeeDiscord;

import com.mashape.unirest.http.Unirest;
import discord4j.core.DiscordClient;
import discord4j.core.DiscordClientBuilder;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.entity.MessageChannel;
import discord4j.core.object.entity.User;
import discord4j.core.object.util.Snowflake;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.connection.Server;
import net.md_5.bungee.api.event.ChatEvent;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;
import net.md_5.bungee.event.EventHandler;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class BungeeDiscord extends Plugin implements Listener {
    private String token = "";
    private String channelId = "";
    private String webhook = "";

    @Override
    public void onEnable() {
        super.onEnable();

        Configuration config = getConfig();
        token = config.getString("token");
        channelId = config.getString("channelId");
        webhook = config.getString("webhook");
        getProxy().getPluginManager().registerListener(this, this);

        DiscordClient discordClient = new DiscordClientBuilder(token).build();
        discordClient.login().subscribe();
        MessageChannel channel = (MessageChannel) discordClient.getChannelById(Snowflake.of(channelId)).block();
        discordClient.getEventDispatcher().on(MessageCreateEvent.class).subscribe(this::onDiscordMessage);
    }

    private void onDiscordMessage(MessageCreateEvent event) {
        if (event.getMessage().getChannelId().asString().equals(channelId)) {
            Optional<User> user = event.getMessage().getAuthor();
            Optional<String> msg = event.getMessage().getContent();

            if (user.isPresent() && msg.isPresent()) {

                ComponentBuilder builder = new ComponentBuilder("Discord");
                builder.color(ChatColor.DARK_AQUA);
                builder.append(String.format(" %s: %s", user.get().getUsername(), msg.get()));
                builder.color(ChatColor.WHITE);

                getProxy().broadcast(builder.create());
            }
        }
    }

    private Configuration getConfig() {
        Configuration configuration;
        try {
            if (!getDataFolder().exists())
                getDataFolder().mkdir();

            File file = new File(getDataFolder(), "config.yml");

            if (!file.exists()) {
                try (InputStream in = getResourceAsStream("config.yml")) {
                    Files.copy(in, file.toPath());
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }

            configuration = ConfigurationProvider.getProvider(YamlConfiguration.class)
                    .load(new File(getDataFolder(), "config.yml"));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        return configuration;
    }

    Map<ProxiedPlayer, Server> players = new HashMap<>();

    @EventHandler
    public void onDisconnect(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        players.remove(player);
        String playerName = player.getDisplayName();

        String uuid = player.getUniqueId().toString().replace("-", "");

        String msg = "Left the network";

        sendWebhookMeta(playerName, uuid, msg);
    }

    @EventHandler
    public void onSwitch(ServerSwitchEvent event) {
        ProxiedPlayer player = event.getPlayer();
        String uuid = player.getUniqueId().toString().replace("-", "");
        String playerName = player.getDisplayName();
        String serverName = player.getServer().getInfo().getName();

        String msg;
        if (players.containsKey(player))
            msg = String.format("Switched to %s", serverName);
        else {
            players.put(player, player.getServer());
            msg = String.format("Joined %s", player.getServer().getInfo().getName());
        }

        sendWebhookMeta(playerName, uuid, msg);
    }

    @EventHandler
    public void onChat(ChatEvent event) {
        if (event.isCommand())
            return;
        String username = event.getSender().toString();
        ProxiedPlayer player = getProxy().getPlayer(username);
        String uuid = player.getUniqueId().toString();
        String serverName = player.getServer().getInfo().getName();
        String message = event.getMessage();

        sendWebhookMessge(String.format("%s (%s)", username, serverName), uuid, message);
    }

    private void sendWebhookRaw(String body) {
        Unirest.post(webhook).header("Content-Type", "application/json").body(body).asJsonAsync();
    }

    private void sendWebhookMeta(String username, String uuid, String msg) {
        String avatar = String.format("https://crafatar.com/renders/head/%s", uuid);

        String body = new JSONObject().put("username", username).put("avatar_url", avatar)
                .put("embeds", new JSONArray().put(new JSONObject().put("title", msg).put("color", "4491614")))
                .toString();
        sendWebhookRaw(body);
    }

    private void sendWebhookMessge(String username, String uuid, String message) {
        String avatar = String.format("https://crafatar.com/renders/head/%s", uuid);
        message = message.replace("@", "@\u200B");

        String body = new JSONObject().put("username", username).put("avatar_url", avatar).put("content", message)
                .toString();
        sendWebhookRaw(body);
    }
}
