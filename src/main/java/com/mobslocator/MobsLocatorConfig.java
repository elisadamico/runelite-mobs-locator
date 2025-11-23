package com.mobslocator;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import java.awt.Color;

@ConfigGroup("mobslocator")
public interface MobsLocatorConfig extends Config
{
    @ConfigSection(
            name = "Search",
            description = "Mob search settings",
            position = 0
    )
    String searchSection = "search";

    @ConfigSection(
            name = "Display",
            description = "Display settings",
            position = 1
    )
    String displaySection = "display";

    @ConfigItem(
            keyName = "searchedMob",
            name = "Search for Mob",
            description = "Enter the name of the mob you want to locate, then click the game screen to activate",
            section = searchSection,
            position = 0
    )
    default String searchedMob()
    {
        return "";
    }


    @ConfigItem(
            keyName = "showSearchResults",
            name = "Show Search Results",
            description = "Display search results in overlay",
            section = searchSection,
            position = 1
    )
    default boolean showSearchResults()
    {
        return true;
    }

    @ConfigItem(
            keyName = "hullColor",
            name = "Hull Highlight Color",
            description = "Color of the outline around searched mobs",
            section = displaySection,
            position = 0
    )
    default Color hullColor()
    {
        return new Color(255, 0, 255); // Hot pink default
    }

    @ConfigItem(
            keyName = "minimapDotColor",
            name = "Minimap Dot Color",
            description = "Color of minimap location dots",
            section = displaySection,
            position = 1
    )
    default Color minimapDotColor()
    {
        return new Color(255, 0, 255); // Hot pink default
    }

    @ConfigItem(
            keyName = "showMinimapDots",
            name = "Show Minimap Dots",
            description = "Show mob locations on minimap",
            section = displaySection,
            position = 2
    )
    default boolean showMinimapDots()
    {
        return true;
    }






    @ConfigItem(
            keyName = "backgroundColor",
            name = "Panel Background Color",
            description = "Background color of the overlay panels (the boxes showing mob locations)",
            section = displaySection,
            position = 6
    )
    default Color backgroundColor()
    {
        return new Color(0, 0, 0, 15);  // More transparent (was 100)
    }
}
