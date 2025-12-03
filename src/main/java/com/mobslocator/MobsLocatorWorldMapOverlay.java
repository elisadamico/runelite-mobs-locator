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
    public MobsLocatorWorldMapOverlay(MobsLocatorConfig config, WorldMapOverlay worldMapOverlay)
    {
        this.config = config;
        this.worldMapOverlay = worldMapOverlay;
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
        int leftMargin = 260;
        int rightMargin = screenWidth - 60;
        int topMargin = 60;
        int bottomMargin = screenHeight - 60;

        // Lists to hold off-screen locations by direction
        java.util.List<MobSpawnData.SpawnLocation> offScreenNorth = new java.util.ArrayList<>();
        java.util.List<MobSpawnData.SpawnLocation> offScreenSouth = new java.util.ArrayList<>();
        java.util.List<MobSpawnData.SpawnLocation> offScreenEast = new java.util.ArrayList<>();
        java.util.List<MobSpawnData.SpawnLocation> offScreenWest = new java.util.ArrayList<>();

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
                // Categorize by primary direction
                int centerX = (leftMargin + rightMargin) / 2;
                int centerY = (topMargin + bottomMargin) / 2;
                int dx = x - centerX;
                int dy = y - centerY;

                // Determine primary direction based on which axis has larger offset
                if (Math.abs(dx) > Math.abs(dy))
                {
                    if (dx < 0)
                        offScreenWest.add(spawn);
                    else
                        offScreenEast.add(spawn);
                }
                else
                {
                    if (dy < 0)
                        offScreenNorth.add(spawn);
                    else
                        offScreenSouth.add(spawn);
                }
            }
        }

        // Draw consolidated arrows for each direction
        if (!offScreenNorth.isEmpty())
        {
            int arrowX = (leftMargin + rightMargin) / 2;
            int arrowY = topMargin + 20;
            drawArrowWithCount(graphics, arrowX, arrowY, -Math.PI / 2, 20, hotPink, offScreenNorth.size());
        }
        if (!offScreenSouth.isEmpty())
        {
            int arrowX = (leftMargin + rightMargin) / 2;
            int arrowY = bottomMargin - 20;
            drawArrowWithCount(graphics, arrowX, arrowY, Math.PI / 2, 20, hotPink, offScreenSouth.size());
        }
        if (!offScreenEast.isEmpty())
        {
            int arrowX = rightMargin - 20;
            int arrowY = (topMargin + bottomMargin) / 2;
            drawArrowWithCount(graphics, arrowX, arrowY, 0, 20, hotPink, offScreenEast.size());
        }
        if (!offScreenWest.isEmpty())
        {
            int arrowX = leftMargin + 20;
            int arrowY = (topMargin + bottomMargin) / 2;
            drawArrowWithCount(graphics, arrowX, arrowY, Math.PI, 20, hotPink, offScreenWest.size());
        }

        return null;
    }

    private void drawArrowWithCount(Graphics2D graphics, int x, int y, double angle, int size, Color color, int count)
    {
        drawArrow(graphics, x, y, angle, size, color);
        
        // Draw count badge
        if (count > 1)
        {
            graphics.setFont(graphics.getFont().deriveFont(java.awt.Font.BOLD, 14f));
            String countStr = String.valueOf(count);
            FontMetrics fm = graphics.getFontMetrics();
            int textWidth = fm.stringWidth(countStr);
            
            int badgeX = x + 15;
            int badgeY = y - 15;
            int badgeSize = Math.max(20, textWidth + 8);
            
            // Draw circle background
            graphics.setColor(new Color(0, 0, 0, 200));
            graphics.fillOval(badgeX - badgeSize/2, badgeY - badgeSize/2, badgeSize, badgeSize);
            
            // Draw circle border
            graphics.setColor(color);
            graphics.setStroke(new java.awt.BasicStroke(2));
            graphics.drawOval(badgeX - badgeSize/2, badgeY - badgeSize/2, badgeSize, badgeSize);
            
            // Draw count text
            graphics.setColor(Color.WHITE);
            graphics.drawString(countStr, badgeX - textWidth/2, badgeY + fm.getAscent()/2 - 2);
        }
    }

    private void drawArrow(Graphics2D graphics, int x, int y, double angle, int size, Color color)
    {
        // Save original transform
        java.awt.geom.AffineTransform oldTransform = graphics.getTransform();
        
        // Rotate around the arrow position
        graphics.rotate(angle, x, y);
        
        // Draw arrow stem (rectangle)
        int stemWidth = size / 3;
        int stemHeight = (int)(size * 1.2);
        graphics.setColor(color);
        graphics.fillRect(x - stemWidth / 2, y - stemHeight, stemWidth, stemHeight);
        
        // Draw arrow head (triangle)
        int[] xPoints = new int[3];
        int[] yPoints = new int[3];
        
        int headWidth = (int)(size * 0.8);
        int headHeight = (int)(size * 0.6);
        
        xPoints[0] = x; // tip
        yPoints[0] = y;
        
        xPoints[1] = x - headWidth / 2; // left
        yPoints[1] = y - headHeight;
        
        xPoints[2] = x + headWidth / 2; // right
        yPoints[2] = y - headHeight;
        
        graphics.fillPolygon(xPoints, yPoints, 3);
        
        // Draw black outline
        graphics.setColor(Color.BLACK);
        graphics.setStroke(new java.awt.BasicStroke(2));
        graphics.drawRect(x - stemWidth / 2, y - stemHeight, stemWidth, stemHeight);
        graphics.drawPolygon(xPoints, yPoints, 3);
        
        // Restore original transform
        graphics.setTransform(oldTransform);
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
