/*
 * Copyright (c) 2023, skotizhoe <skotizhoe@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.twitchemotes;

import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import javax.inject.Inject;

import joptsimple.internal.Strings;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IndexedSprite;
import net.runelite.api.MessageNode;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.config.ConfigManager;

@PluginDescriptor(
		name = "Twitch Emotes",
		description = "Adds Twitch emotes to chat"
)
@Slf4j
public class twitchemotesPlugin extends Plugin
{
	private static final Pattern TAG_REGEXP = Pattern.compile("<[^>]*>");
	private static final Pattern WHITESPACE_REGEXP = Pattern.compile("[\\s\\u00A0]");
	private static final Pattern SLASH_REGEXP = Pattern.compile("/");
	private static final Pattern PUNCTUATION_REGEXP = Pattern.compile("[\\W_\\d]");
	private static final List<String> EMOTE_TRIGGERS = new ArrayList<>();

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ChatMessageManager chatMessageManager;

	private int modIconsStart = -1;

	@Override
	protected void startUp()
	{
		clientThread.invokeLater(this::loadTwitchEmojiIcons);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged gameStateChanged)
	{
		if (gameStateChanged.getGameState() == GameState.LOGGED_IN)
		{
			loadTwitchEmojiIcons();
		}
	}

	private void loadTwitchEmojiIcons()
	{
		final IndexedSprite[] modIcons = client.getModIcons();
		if (modIconsStart != -1 || modIcons == null)
		{
			return;
		}

		try {
			File directory = new File(getClass().getResource("/images/").toURI());
			File[] files = directory.listFiles();
			modIconsStart = files.length;
			final IndexedSprite[] newModIcons = Arrays.copyOf(modIcons, modIcons.length + files.length);
			if (files != null) {
				int index = 0;
				for (File file : files) {
					if (file.isFile()) {
						final BufferedImage image = ImageUtil.loadImageResource(getClass(), "/images/" + file.getName().toLowerCase().replace(".png", "") + ".png");
						EMOTE_TRIGGERS.add(file.getName().toLowerCase().replace(".png", ""));
						final IndexedSprite sprite = ImageUtil.getImageIndexedSprite(image, client);
						newModIcons[modIconsStart + (index++)] = sprite;
					}
				}
			}
			log.debug("Adding TwitchEmoji icons");
			client.setModIcons(newModIcons);
		} catch (URISyntaxException e) {
			log.warn("Error locating TwitchEmoji images directory.");
		}

	}

	@Subscribe
	public void onChatMessage(ChatMessage chatMessage)
	{
		if (client.getGameState() != GameState.LOGGED_IN || modIconsStart == -1)
		{
			return;
		}

		switch (chatMessage.getType())
		{
			case PUBLICCHAT:
			case MODCHAT:
			case FRIENDSCHAT:
			case PRIVATECHAT:
			case PRIVATECHATOUT:
			case MODPRIVATECHAT:
				break;
			default:
				return;
		}

		final MessageNode messageNode = chatMessage.getMessageNode();
		final String message = messageNode.getValue();
		final String updatedMessage = updateMessage(message);

		if (updatedMessage == null)
		{
			return;
		}

		messageNode.setRuneLiteFormatMessage(updatedMessage);
		client.refreshChat();
	}

	@Subscribe
	public void onOverheadTextChanged(final OverheadTextChanged event)
	{
		if (!(event.getActor() instanceof Player))
		{
			return;
		}

		final String message = event.getOverheadText();
		final String updatedMessage = updateMessage(message);

		if (updatedMessage == null)
		{
			return;
		}

		event.getActor().setOverheadText(updatedMessage);
	}

	@Nullable
	String updateMessage(final String message)
	{
		final String[] slashWords = SLASH_REGEXP.split(message);
		boolean editedMessage = false;
		for (int s = 0; s < slashWords.length; s++)
		{
			final String[] messageWords = WHITESPACE_REGEXP.split(slashWords[s]);

			for (int i = 0; i < messageWords.length; i++)
			{
				//Remove tags except for <lt> and <gt>
				final String pretrigger = removeTags(messageWords[i]);
				final Matcher matcherTrigger = PUNCTUATION_REGEXP.matcher(pretrigger);
				final String trigger = matcherTrigger.replaceAll("");
				if (trigger.equals(""))
				{
					continue;
				}


				final String emote = EMOTE_TRIGGERS.stream().filter(e -> e.equalsIgnoreCase(trigger)).findFirst().orElse(null);

				if (emote == null) {
					continue;
				}

				int twitchEmojiId;

				twitchEmojiId = modIconsStart + EMOTE_TRIGGERS.indexOf(emote);

				if (twitchEmojiId != 0)
				{

					{
						messageWords[i] = messageWords[i].replace(trigger, "<img=" + twitchEmojiId + ">");
					}
				}
				editedMessage = true;
			}
			slashWords[s] = Strings.join(messageWords, " ");
		}

		if (!editedMessage)
		{
			return null;
		}

		return Strings.join(slashWords, "/");
	}

	/**
	 * Remove tags, except for &lt;lt&gt; and &lt;gt&gt;
	 *
	 * @return
	 */
	private static String removeTags(String str)
	{
		StringBuilder stringBuffer = new StringBuilder();
		Matcher matcher = TAG_REGEXP.matcher(str);
		while (matcher.find())
		{
			matcher.appendReplacement(stringBuffer, "");
			String match = matcher.group(0);
			switch (match)
			{
				case "<lt>":
				case "<gt>":
					stringBuffer.append(match);
					break;
			}
		}
		matcher.appendTail(stringBuffer);
		return stringBuffer.toString();
	}

}