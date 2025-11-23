package com.mobslocator;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import static net.runelite.api.widgets.ComponentID.WORLD_MAP_VIEW;

public class MobsLocatorOverlay extends Overlay
{
    private final Client client;
    private final MobsLocatorPlugin plugin;
    private final MobsLocatorConfig config;
    private final PanelComponent leftPanel = new PanelComponent();
    private final PanelComponent rightPanel = new PanelComponent();

    @Inject
    public MobsLocatorOverlay(Client client, MobsLocatorPlugin plugin, MobsLocatorConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);

        // Make panels transparent so we can draw our own background
        leftPanel.setBackgroundColor(null);
        rightPanel.setBackgroundColor(null);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null)
        {
            return null;
        }

        plugin.hasSearchTermChanged();

        //Widget worldMap = client.getWidget(ComponentID.WORLD_MAP_VIEW);
        Widget worldMap = client.getWidget(595, 25);  // World map widget
        if (worldMap != null && !worldMap.isHidden())
        {
            return null;
        }

        renderMobHighlights(graphics);

        String searchedMob = config.searchedMob().trim();

        if (!searchedMob.isEmpty() && config.showSearchResults())
        {
            renderSideBySidePanels(graphics, localPlayer.getWorldLocation(), searchedMob);
        }

        return null;
    }

    private void renderSideBySidePanels(Graphics2D graphics, WorldPoint playerLocation, String searchedMob)
    {
        leftPanel.getChildren().clear();
        leftPanel.setPreferredSize(new Dimension(250, 0));

        rightPanel.getChildren().clear();
        rightPanel.setPreferredSize(new Dimension(90, 0));

        List<MobSpawnData.SpawnLocation> knownSpawns = MobSpawnData.getSpawnLocations(searchedMob.toLowerCase());

        leftPanel.getChildren().add(TitleComponent.builder()
                .text("All Locations")
                .color(Color.CYAN)
                .build());

        if (!knownSpawns.isEmpty())
        {
            knownSpawns.stream()
                    .sorted((a, b) -> Integer.compare(
                            playerLocation.distanceTo(a.getApproximateCenter()),
                            playerLocation.distanceTo(b.getApproximateCenter())
                    ))
                    .forEach(spawn -> {
                        String membersTag = spawn.isMembers() ? " [M]" : " [F2P]";
                        String locationInfo = String.format("- %s (%d spawns)",
                                spawn.getAreaName(),
                                spawn.getCount());

                        Color tagColor = spawn.isMembers() ? new Color(255, 215, 0) : new Color(192, 192, 192);

                        leftPanel.getChildren().add(LineComponent.builder()
                                .left(locationInfo)
                                .leftColor(Color.WHITE)
                                .right(membersTag)
                                .rightColor(tagColor)
                                .build());
                    });
        }
        else
        {
            leftPanel.getChildren().add(LineComponent.builder()
                    .left("No data available")
                    .leftColor(Color.GRAY)
                    .build());
        }

        rightPanel.getChildren().add(TitleComponent.builder()
                .text("Nearby")
                .color(Color.CYAN)
                .build());

        List<NPC> nearbyMobs = plugin.getSearchedMobs();

        if (!nearbyMobs.isEmpty())
        {
            nearbyMobs.stream()
                    .filter(npc -> npc.getWorldLocation() != null)
                    .sorted((a, b) -> Integer.compare(
                            playerLocation.distanceTo(a.getWorldLocation()),
                            playerLocation.distanceTo(b.getWorldLocation())
                    ))
                    .limit(10)
                    .forEach(npc -> {
                        int distance = playerLocation.distanceTo(npc.getWorldLocation());
                        String locationInfo = String.format("%d tiles away", distance);
                        Color textColor = distance <= 10 ? Color.GREEN :
                                distance <= 25 ? Color.YELLOW : Color.WHITE;

                        rightPanel.getChildren().add(LineComponent.builder()
                                .left(locationInfo)
                                .leftColor(textColor)
                                .build());
                    });
        }
        else
        {
            rightPanel.getChildren().add(LineComponent.builder()
                    .left("None nearby")
                    .leftColor(Color.GRAY)
                    .build());
        }

        // Render panels with transparent backgrounds
        renderPanelWithTransparentBackground(graphics, leftPanel, 0, 0);
        renderPanelWithTransparentBackground(graphics, rightPanel, 260, 0);
    }

    private void renderPanelWithTransparentBackground(Graphics2D graphics, PanelComponent panel, int xOffset, int yOffset)
    {
        graphics.translate(xOffset, yOffset);

        // Render panel to get its size
        Dimension panelSize = panel.render(graphics);

        // Reset position to draw background behind
        graphics.translate(-xOffset, -yOffset);

        // Draw transparent background
        Color bgColor = config.backgroundColor();
        graphics.setColor(new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 100));
        graphics.fillRect(xOffset, yOffset, panelSize.width, panelSize.height);

        // Render panel content on top
        graphics.translate(xOffset, yOffset);
        panel.render(graphics);
        graphics.translate(-xOffset, -yOffset);
    }

    private void renderMobHighlights(Graphics2D graphics)
    {
        String searchedMob = config.searchedMob().trim().toLowerCase();
        if (searchedMob.isEmpty())
        {
            return;
        }

        Color hullColor = config.hullColor();
        int canvasWidth = client.getCanvasWidth();
        int canvasHeight = client.getCanvasHeight();

        int leftMargin = 0;
        int rightMargin = (int)(canvasWidth * 0.82);
        int topMargin = (int)(canvasHeight * 0.05);
        int bottomMargin = (int)(canvasHeight * 0.72);

        for (NPC npc : plugin.getSearchedMobs())
        {
            if (npc == null)
            {
                continue;
            }

            java.awt.Shape hull = npc.getConvexHull();

            if (hull == null)
            {
                continue;
            }

            java.awt.Rectangle bounds = hull.getBounds();

            if (bounds.x < leftMargin ||
                    bounds.x + bounds.width > rightMargin ||
                    bounds.y < topMargin ||
                    bounds.y + bounds.height > bottomMargin)
            {
                continue;
            }

            graphics.setColor(new Color(hullColor.getRed(), hullColor.getGreen(), hullColor.getBlue(), 180));
            graphics.setStroke(new java.awt.BasicStroke(2));
            graphics.draw(hull);

            graphics.setColor(new Color(hullColor.getRed(), hullColor.getGreen(), hullColor.getBlue(), 50));
            graphics.fill(hull);
        }
    }
}
