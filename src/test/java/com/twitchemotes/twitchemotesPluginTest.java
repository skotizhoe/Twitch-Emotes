package com.twitchemotes;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class twitchemotesPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(twitchemotesPlugin.class);
		RuneLite.main(args);
	}
}