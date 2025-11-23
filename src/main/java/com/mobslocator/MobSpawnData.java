package com.mobslocator;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.runelite.api.coords.WorldPoint;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class MobSpawnData
{
    private static final Map<String, List<SpawnLocation>> MOB_SPAWNS = new HashMap<>();
    private static final List<Region> REGIONS = new ArrayList<>();
    private static final int CLUSTER_DISTANCE = 50;
    private static boolean initialized = false;
    private static Gson gson;

    public static synchronized void initialize(Gson gsonInstance)
    {
        if (initialized)
        {
            return;
        }

        gson = gsonInstance;
        loadRegions();
        loadSpawnData();
        initialized = true;
    }

    private static void loadRegions()
    {
        loadRegionsFromFile("/surface_areas.json");
        loadRegionsFromFile("/regions.json");
        REGIONS.sort((a, b) -> Integer.compare(a.getArea(), b.getArea()));
        System.out.println("Loaded " + REGIONS.size() + " region definitions");
    }

    private static void loadRegionsFromFile(String filename)
    {
        try
        {
            InputStream inputStream = MobSpawnData.class.getResourceAsStream(filename);
            if (inputStream == null)
            {
                System.err.println("Could not find " + filename + " resource");
                return;
            }
            JsonArray jsonArray = gson.fromJson(new InputStreamReader(inputStream), JsonArray.class);

            for (JsonElement element : jsonArray)
            {
                JsonObject obj = element.getAsJsonObject();
                
                if (!obj.has("name") || !obj.has("bounds"))
                {
                    continue;
                }

                String name = obj.get("name").getAsString();
                JsonArray boundsArray = obj.getAsJsonArray("bounds");
                
                if (boundsArray.size() != 2)
                {
                    continue;
                }

                JsonArray minBounds = boundsArray.get(0).getAsJsonArray();
                JsonArray maxBounds = boundsArray.get(1).getAsJsonArray();

                int minX = minBounds.get(0).getAsInt();
                int minY = minBounds.get(1).getAsInt();
                int maxX = maxBounds.get(0).getAsInt();
                int maxY = maxBounds.get(1).getAsInt();

                REGIONS.add(new Region(name, minX, minY, maxX, maxY));
            }
        }
        catch (Exception e)
        {
            System.err.println("Error loading " + filename + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void loadSpawnData()
    {
        try
        {
            InputStream inputStream = MobSpawnData.class.getResourceAsStream("/npc_spawns.json");
            if (inputStream == null)
            {
                System.err.println("Could not find npc_spawns.json resource");
                return;
            }
            JsonArray jsonArray = gson.fromJson(new InputStreamReader(inputStream), JsonArray.class);
            
            Map<String, List<NpcSpawn>> rawSpawnsByName = new HashMap<>();

            for (JsonElement element : jsonArray)
            {
                JsonObject obj = element.getAsJsonObject();
                
                if (!obj.has("name") || !obj.has("x") || !obj.has("y") || !obj.has("p"))
                {
                    continue;
                }

                String name = obj.get("name").getAsString();

                int x = obj.get("x").getAsInt();
                int y = obj.get("y").getAsInt();
                int plane = obj.get("p").getAsInt();

                WorldPoint worldPoint = new WorldPoint(x, y, plane);
                
                rawSpawnsByName.computeIfAbsent(name.toLowerCase(), k -> new ArrayList<>())
                    .add(new NpcSpawn(worldPoint, name));
            }

            for (Map.Entry<String, List<NpcSpawn>> entry : rawSpawnsByName.entrySet())
            {
                String mobName = entry.getKey();
                List<NpcSpawn> spawns = entry.getValue();
                
                List<SpawnLocation> clusterLocations = clusterSpawns(spawns);
                MOB_SPAWNS.put(mobName, clusterLocations);
            }

            System.out.println("Loaded spawn data for " + MOB_SPAWNS.size() + " different mobs");
        }
        catch (Exception e)
        {
            System.err.println("Error loading NPC spawn data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static List<SpawnLocation> clusterSpawns(List<NpcSpawn> spawns)
    {
        List<SpawnCluster> clusters = new ArrayList<>();

        for (NpcSpawn spawn : spawns)
        {
            boolean addedToCluster = false;

            for (SpawnCluster cluster : clusters)
            {
                if (cluster.isNearby(spawn.worldPoint, CLUSTER_DISTANCE))
                {
                    cluster.add(spawn);
                    addedToCluster = true;
                    break;
                }
            }

            if (!addedToCluster)
            {
                SpawnCluster newCluster = new SpawnCluster();
                newCluster.add(spawn);
                clusters.add(newCluster);
            }
        }

        return clusters.stream()
            .map(cluster -> new SpawnLocation(
                getAreaName(cluster.getCenter()),
                cluster.getCount(),
                cluster.getCenter(),
                isMembersArea(cluster.getCenter())
            ))
            .collect(Collectors.toList());
    }

    private static String getAreaName(WorldPoint point)
    {
        for (Region region : REGIONS)
        {
            if (region.contains(point))
            {
                if (region.name.equals("Gielinor Surface"))
                {
                    return getNearestSurfaceArea(point);
                }
                return region.name;
            }
        }

        return String.format("Unknown Area (%d, %d)", point.getX(), point.getY());
    }

    private static String getNearestSurfaceArea(WorldPoint point)
    {
        Region nearestRegion = null;
        int minDistance = Integer.MAX_VALUE;

        for (Region region : REGIONS)
        {
            if (region.name.equals("Gielinor Surface") || 
                region.name.contains("Dungeon") || 
                region.name.contains("Cave") || 
                region.name.contains("Underground"))
            {
                continue;
            }

            WorldPoint regionCenter = region.getCenter();
            int distance = point.distanceTo(regionCenter);

            if (distance < minDistance)
            {
                minDistance = distance;
                nearestRegion = region;
            }
        }

        if (nearestRegion != null)
        {
            return nearestRegion.name;
        }

        return "Gielinor Surface";
    }

    private static boolean isMembersArea(WorldPoint point)
    {
        int x = point.getX();
        int y = point.getY();

        if (x >= 2944 && x <= 3392 && y >= 3136 && y <= 3712 && point.getPlane() == 0)
        {
            return false;
        }

        return true;
    }

    public static List<SpawnLocation> getSpawnLocations(String mobName, Gson gsonInstance)
    {
        if (!initialized)
        {
            initialize(gsonInstance);
        }
        
        String searchTerm = mobName.toLowerCase().trim();
        System.out.println("Searching for: '" + searchTerm + "'");
        
        if (MOB_SPAWNS.containsKey(searchTerm))
        {
            System.out.println("Found exact match for: " + searchTerm);
            return MOB_SPAWNS.get(searchTerm);
        }
        
        List<SpawnLocation> matchingSpawns = new ArrayList<>();
        for (Map.Entry<String, List<SpawnLocation>> entry : MOB_SPAWNS.entrySet())
        {
            if (entry.getKey().contains(searchTerm))
            {
                System.out.println("Found partial match: " + entry.getKey());
                matchingSpawns.addAll(entry.getValue());
            }
        }
        
        System.out.println("Total matches found: " + matchingSpawns.size());
        return matchingSpawns;
    }

    public static Set<String> getAllMobNames()
    {
        if (!initialized)
        {
            initialize();
        }
        return new HashSet<>(MOB_SPAWNS.keySet());
    }

    private static class Region
    {
        final String name;
        final int minX;
        final int minY;
        final int maxX;
        final int maxY;

        Region(String name, int minX, int minY, int maxX, int maxY)
        {
            this.name = name;
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        boolean contains(WorldPoint point)
        {
            int x = point.getX();
            int y = point.getY();
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }

        int getArea()
        {
            return (maxX - minX) * (maxY - minY);
        }

        WorldPoint getCenter()
        {
            int centerX = (minX + maxX) / 2;
            int centerY = (minY + maxY) / 2;
            return new WorldPoint(centerX, centerY, 0);
        }
    }

    private static class NpcSpawn
    {
        final WorldPoint worldPoint;
        final String name;

        NpcSpawn(WorldPoint worldPoint, String name)
        {
            this.worldPoint = worldPoint;
            this.name = name;
        }
    }

    private static class SpawnCluster
    {
        private final List<WorldPoint> points = new ArrayList<>();

        void add(NpcSpawn spawn)
        {
            points.add(spawn.worldPoint);
        }

        boolean isNearby(WorldPoint point, int maxDistance)
        {
            for (WorldPoint existing : points)
            {
                if (existing.distanceTo(point) <= maxDistance)
                {
                    return true;
                }
            }
            return false;
        }

        WorldPoint getCenter()
        {
            if (points.isEmpty())
            {
                return new WorldPoint(0, 0, 0);
            }

            int avgX = (int) points.stream().mapToInt(WorldPoint::getX).average().orElse(0);
            int avgY = (int) points.stream().mapToInt(WorldPoint::getY).average().orElse(0);
            int plane = points.get(0).getPlane();

            return new WorldPoint(avgX, avgY, plane);
        }

        int getCount()
        {
            return points.size();
        }
    }

    public static class SpawnLocation
    {
        private final String areaName;
        private final int count;
        private final WorldPoint approximateCenter;
        private final boolean members;

        public SpawnLocation(String areaName, int count, WorldPoint approximateCenter, boolean members)
        {
            this.areaName = areaName;
            this.count = count;
            this.approximateCenter = approximateCenter;
            this.members = members;
        }

        public String getAreaName() { return areaName; }
        public int getCount() { return count; }
        public WorldPoint getApproximateCenter() { return approximateCenter; }
        public boolean isMembers() { return members; }
    }
}
