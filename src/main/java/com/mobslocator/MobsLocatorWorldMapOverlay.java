package com.mobslocator;

import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.util.List;

public class MobsLocatorWorldMapOverlay extends Overlay
{
    private final MobsLocatorConfig config;
    private final WorldMapOverlay worldMapOverlay;
    private final MobsLocatorPlugin plugin;  // ADD THIS LINE
    
    @Inject
    public MobsLocatorWorldMapOverlay(MobsLocatorConfig config, WorldMapOverlay worldMapOverlay, MobsLocatorPlugin plugin)  // ADD plugin parameter
    {
        this.config = config;
        this.worldMapOverlay = worldMapOverlay;
        this.plugin = plugin;  // ADD THIS LINE
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(OverlayPriority.HIGH);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        String searchedMob = config.searchedMob().trim().toLowerCase();
        if (searchedMob.isEmpty())
        {
            return null;
        }

        List<MobSpawnData.SpawnLocation> spawnLocations = MobSpawnData.getSpawnLocations(searchedMob, plugin.getGson());
        if (spawnLocations.isEmpty())
        {
            return null;
        }

        Color hotPink = new Color(255, 0, 255);

        int screenWidth = graphics.getClipBounds().width;
        int screenHeight = graphics.getClipBounds().height;
        int leftMargin = 250;
        int rightMargin = screenWidth - 50;
        int topMargin = 50;
        int bottomMargin = screenHeight - 50;

        for (MobSpawnData.SpawnLocation spawn : spawnLocations)
        {
            WorldPoint worldPoint = spawn.getApproximateCenter();
            Point mapPoint = worldMapOverlay.mapWorldPointToGraphicsPoint(worldPoint);

            if (mapPoint == null)
            {
                continue;
            }

            int x = mapPoint.getX();
            int y = mapPoint.getY();

            boolean isOnScreen = x >= leftMargin && x <= rightMargin &&
                    y >= topMargin && y <= bottomMargin;

            if (isOnScreen)
            {
                int starSize = 12;
                drawStar(graphics, x, y, starSize, hotPink);

                String label = spawn.getAreaName() + " (" + spawn.getCount() + ")";
                graphics.setFont(graphics.getFont().deriveFont(java.awt.Font.BOLD, 16f));
                FontMetrics fm = graphics.getFontMetrics();
                Rectangle2D bounds = fm.getStringBounds(label, graphics);

                int textX = x - (int) bounds.getWidth() / 2;
                int textY = y + starSize + fm.getAscent() + 5;

                if (textX >= leftMargin && textX + bounds.getWidth() <= rightMargin)
                {
                    graphics.setColor(new Color(0, 0, 0, 220));
                    graphics.fillRect(textX - 5, textY - fm.getAscent() - 2, (int) bounds.getWidth() + 10, fm.getHeight() + 4);

                    graphics.setColor(Color.BLACK);
                    graphics.drawRect(textX - 5, textY - fm.getAscent() - 2, (int) bounds.getWidth() + 10, fm.getHeight() + 4);

                    graphics.setColor(hotPink);
                    graphics.drawString(label, textX, textY);
                }
            }
            else
            {
                drawDirectionalArrow(graphics, x, y, leftMargin, rightMargin, topMargin, bottomMargin, hotPink);
            }
        }

        return null;
    }

    private void drawDirectionalArrow(Graphics2D graphics, int targetX, int targetY,
                                      int leftMargin, int rightMargin, int topMargin, int bottomMargin,
                                      Color color)
    {
        int centerX = (leftMargin + rightMargin) / 2;
        int centerY = (topMargin + bottomMargin) / 2;

        double dx = targetX - centerX;
        double dy = targetY - centerY;
        double angle = Math.atan2(dy, dx);

        int arrowX, arrowY;

        double slope = dy / dx;

        if (Math.abs(dx) > Math.abs(dy))
        {
            if (targetX < leftMargin)
            {
                arrowX = leftMargin + 30;
                arrowY = (int) (centerY + (arrowX - centerX) * slope);
            }
            else
            {
                arrowX = rightMargin - 30;
                arrowY = (int) (centerY + (arrowX - centerX) * slope);
            }
            arrowY = Math.max(topMargin + 30, Math.min(bottomMargin - 30, arrowY));
        }
        else
        {
            if (targetY < topMargin)
            {
                arrowY = topMargin + 30;
                arrowX = (int) (centerX + (arrowY - centerY) / slope);
            }
            else
            {
                arrowY = bottomMargin - 30;
                arrowX = (int) (centerX + (arrowY - centerY) / slope);
            }
            arrowX = Math.max(leftMargin + 30, Math.min(rightMargin - 30, arrowX));
        }

        drawArrow(graphics, arrowX, arrowY, angle, 20, color);
    }

    private void drawArrow(Graphics2D graphics, int x, int y, double angle, int size, Color color)
    {
        int[] xPoints = new int[3];
        int[] yPoints = new int[3];

        xPoints[0] = x + (int) (size * Math.cos(angle));
        yPoints[0] = y + (int) (size * Math.sin(angle));

        xPoints[1] = x + (int) (size * 0.4 * Math.cos(angle + 2.5));
        yPoints[1] = y + (int) (size * 0.4 * Math.sin(angle + 2.5));

        xPoints[2] = x + (int) (size * 0.4 * Math.cos(angle - 2.5));
        yPoints[2] = y + (int) (size * 0.4 * Math.sin(angle - 2.5));

        graphics.setColor(color);
        graphics.fillPolygon(xPoints, yPoints, 3);

        graphics.setColor(Color.BLACK);
        graphics.setStroke(new java.awt.BasicStroke(2));
        graphics.drawPolygon(xPoints, yPoints, 3);
    }

    // Helper method to draw a star
    private void drawStar(Graphics2D graphics, int centerX, int centerY, int size, Color color)
    {
        int[] xPoints = new int[10];
        int[] yPoints = new int[10];

        double angle = Math.PI / 2; // Start from top
        double angleStep = Math.PI / 5; // 10 points = 5 outer + 5 inner

        for (int i = 0; i < 10; i++)
        {
            double radius = (i % 2 == 0) ? size : size * 0.4; // Outer and inner points
            xPoints[i] = centerX + (int) (radius * Math.cos(angle));
            yPoints[i] = centerY - (int) (radius * Math.sin(angle));
            angle += angleStep;
        }

        // Fill star
        graphics.setColor(color);
        graphics.fillPolygon(xPoints, yPoints, 10);

        // Draw black outline
        graphics.setColor(Color.BLACK);
        graphics.drawPolygon(xPoints, yPoints, 10);
    }
}
