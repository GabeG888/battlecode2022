package MainBot;
import battlecode.common.*;


import java.awt.*;
import java.util.*;
import java.lang.Math;
import java.util.List;
import java.util.Random;


public strictfp class RobotPlayer {

    static Random rng;
    static int turnIndex = 99;
    static int minerCount = 0;
    static int soldierCount = 0;
    static int builderCount = 0;
    static int maxMinersPerArea = 10;
    static MapLocation target;
    static RobotType[] attackPriority = new RobotType[] {RobotType.SAGE, RobotType.WATCHTOWER,
            RobotType.SOLDIER, RobotType.ARCHON, RobotType.LABORATORY, RobotType.MINER, RobotType.BUILDER};
    static HashMap<RobotType, Integer> priorityMap = new HashMap<>();
    static int turnOrderIndex = 0;
    static int soldierIndexStart = 1;
    static int soldierIndexEnd = 63;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        priorityMap.put(RobotType.BUILDER, 2);
        priorityMap.put(RobotType.MINER, 2);
        priorityMap.put(RobotType.SOLDIER, 3);
        priorityMap.put(RobotType.SAGE, 3);
        priorityMap.put(RobotType.WATCHTOWER, 4);
        priorityMap.put(RobotType.LABORATORY, 4);
        priorityMap.put(RobotType.ARCHON, 5);
        rng = new Random(rc.getID());
        while (true) {
            try {
                switch (rc.getType()) {
                    case ARCHON:     runArchon(rc);  break;
                    case MINER:      runMiner(rc);   break;
                    case SOLDIER:    runSoldier(rc); break;
                    case LABORATORY: break;
                    case WATCHTOWER: runWatchtower(rc); break;
                    case BUILDER: runBuilder(rc); break;
                    case SAGE:       break;
                }
            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    static void runArchon(RobotController rc) throws GameActionException {
        for(RobotInfo robot: rc.senseNearbyRobots(20, rc.getTeam().opponent()))
            if(robot.type.equals(RobotType.SOLDIER)) AddSoldierDestination(rc, robot.getLocation());
        if(rc.readSharedArray(turnOrderIndex) > turnIndex) turnIndex = 0;
        else turnIndex = rc.readSharedArray(turnOrderIndex);
        rc.writeSharedArray(turnOrderIndex, turnIndex + 1);
        RobotType toBuild = null;
        if(false) {
            if(minerCount > soldierCount * 2) toBuild = RobotType.SOLDIER;
            else toBuild = RobotType.MINER;
        }
        else{
            if(minerCount * 2 > soldierCount) toBuild = RobotType.SOLDIER;
            else toBuild = RobotType.MINER;
        }

        if(rc.getTeamLeadAmount(rc.getTeam()) < 100 && rng.nextInt(rc.getArchonCount() - turnIndex) != 0) return;

        RobotInfo[] robots = rc.senseNearbyRobots();
        for(RobotInfo robot : robots){
            if(robot.team != rc.getTeam()){
                toBuild = RobotType.SOLDIER;
                break;
            }
        }

        if(rc.getTeamLeadAmount(rc.getTeam()) > 2000 && builderCount < 10
                && toBuild == RobotType.SOLDIER && rng.nextBoolean())
            toBuild = RobotType.BUILDER;

        if(toBuild == null) return;
        for(Direction direction : Direction.values()) {
            if (rc.canBuildRobot(toBuild, direction)) {
                rc.buildRobot(toBuild, direction);
                switch (toBuild) {
                    case MINER: minerCount++; break;
                    case SOLDIER: soldierCount++; break;
                    case BUILDER: builderCount++; break;
                }
                break;
            }
        }
    }

    static void runMiner(RobotController rc) throws GameActionException {
        if(rc.senseNearbyRobots().length > maxMinersPerArea) explore(rc);
        for(RobotInfo robot: rc.senseNearbyRobots(20, rc.getTeam().opponent()))
            if(robot.type.equals(RobotType.SOLDIER)) AddSoldierDestination(rc, robot.getLocation());
        for(RobotInfo robot : rc.senseNearbyRobots()) if(robot.type == RobotType.SOLDIER && !robot.getTeam().isPlayer())
            if(rc.canMove(rc.getLocation().directionTo(robot.getLocation()).opposite()))
                rc.move(rc.getLocation().directionTo(robot.getLocation()).opposite());
        MapLocation[] golds = rc.senseNearbyLocationsWithGold(RobotType.MINER.visionRadiusSquared);
        if(golds.length > 0) {
            navigateToLocation(rc, golds[0]);
            while (rc.canMineGold(golds[0])) rc.mineGold(golds[0]);
        }
        else {
            MapLocation[] possibleLeads = rc.senseNearbyLocationsWithLead(2);
            List<MapLocation> leads = new ArrayList<>();
            for (MapLocation possibleLead : possibleLeads)
                if (rc.senseLead(possibleLead) > 1) {
                    boolean isTaken = false;
                    for(Direction direction : Direction.values()) {
                        if(possibleLead.add(direction).distanceSquaredTo(rc.getLocation()) > 20) continue;
                        if(!rc.onTheMap(possibleLead.add(direction))) continue;
                        RobotInfo robot = rc.senseRobotAtLocation(possibleLead.add(direction));
                        if(robot != null && robot.getTeam().isPlayer() && robot.type.equals(RobotType.MINER)
                                && robot.getID() > rc.getID()){
                            isTaken = true;
                            break;
                        }
                    }
                    if(!isTaken) leads.add(possibleLead);
                }
            if(!leads.isEmpty()) {
                MapLocation lead = leads.get(0);
                while (rc.canMineLead(lead) && rc.senseLead(lead) > 1) rc.mineLead(lead);
            }
            else {
                possibleLeads = rc.senseNearbyLocationsWithLead(RobotType.MINER.visionRadiusSquared);
                leads = new ArrayList<>();
                for (MapLocation possibleLead : possibleLeads)
                    if (rc.senseLead(possibleLead) > 1) {
                        boolean isTaken = false;
                        for(Direction direction : Direction.values()) {
                            if(possibleLead.add(direction).distanceSquaredTo(rc.getLocation()) > 20) continue;
                            if(!rc.onTheMap(possibleLead.add(direction))) continue;
                            RobotInfo robot = rc.senseRobotAtLocation(possibleLead.add(direction));
                            if(robot != null && robot.getTeam().isPlayer() && robot.type.equals(RobotType.MINER)
                                    && robot.getID() > rc.getID()){
                                isTaken = true;
                                break;
                            }
                        }
                        if(!isTaken) leads.add(possibleLead);
                    }
                if (!leads.isEmpty()) navigateToLocation(rc, leads.get(rng.nextInt(leads.size())));
                else {
                    explore(rc);
                    for(Direction direction : Direction.values())
                        if(rc.canMineLead(rc.getLocation().add(direction))
                                && rc.senseLead(rc.getLocation().add(direction)) > 1)
                            rc.mineLead(rc.getLocation().add(direction));
                }
            }
        }
    }

    static void runSoldier(RobotController rc) throws GameActionException {
        boolean exit = false;
        int radius = rc.getType().actionRadiusSquared;
        Team opponent = rc.getTeam().opponent();

        RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
        if (enemies.length > 0) {
            AddSoldierDestination(rc, enemies[0].location);
            for (RobotType type : attackPriority) {
                int bestHealth = 10000;
                RobotInfo bestEnemy = null;
                for (RobotInfo enemy : enemies) {
                    if (enemy.type != type) continue;
                    if(enemy.getHealth() < bestHealth){
                        bestHealth = enemy.getHealth();
                        bestEnemy = enemy;
                    }
                    if(bestEnemy != null && rc.canAttack(bestEnemy.getLocation())) {
                        rc.attack(bestEnemy.getLocation());
                        exit = true;
                        break;
                    }
                }
                if (exit) break;
            }
            RobotInfo closestEnemy = null;
            int closestDistance = 10000;
            for(RobotInfo enemy : enemies){
                if(rc.getLocation().distanceSquaredTo(enemy.getLocation()) < closestDistance){
                    closestDistance = rc.getLocation().distanceSquaredTo(enemy.getLocation());
                    closestEnemy = enemy;
                }
            }
            if(closestEnemy == null) return;
            if(closestEnemy.type.equals(RobotType.SOLDIER)){
                if(rc.getLocation().distanceSquaredTo(closestEnemy.getLocation()) > 13)
                    navigateToLocation(rc, closestEnemy.getLocation());
                else if(rc.getLocation().distanceSquaredTo(closestEnemy.getLocation()) < 13)
                    navigateToLocation(rc, rc.getLocation().translate(rc.getLocation().x, rc.getLocation().y)
                            .translate(-closestEnemy.location.x, -closestEnemy.location.y));
            }
        }
        else{
            MapLocation[] enemyLocations = GetAllSoldierDestinations(rc);
            MapLocation closestEnemy = null;
            int closestDistance = 10000;
            for(MapLocation enemyLocation : enemyLocations){
                if(rc.getLocation().distanceSquaredTo(enemyLocation) < closestDistance){
                    closestDistance = rc.getLocation().distanceSquaredTo(enemyLocation);
                    closestEnemy = enemyLocation;
                }
            }
            if(closestEnemy != null) {
                if(closestEnemy.distanceSquaredTo(rc.getLocation()) < 10) RemoveSoldierDestination(rc, closestEnemy);
                navigateToLocation(rc, closestEnemy);
            }
            else explore(rc);
        }
    }

    static void runBuilder(RobotController rc) throws GameActionException{
        BuildWatchtowers(rc);
    }

    static void runWatchtower(RobotController rc) throws GameActionException{
        if(rc.getMode() == RobotMode.TURRET){
            RobotInfo[] enemies = rc.senseNearbyRobots(RobotType.WATCHTOWER.actionRadiusSquared, rc.getTeam().opponent());
            RobotInfo closestEnemy = null;
            int closestDistance = 10000;
            for(RobotInfo enemy : enemies){
                if(rc.getLocation().distanceSquaredTo(enemy.getLocation()) < closestDistance){
                    closestDistance = rc.getLocation().distanceSquaredTo(enemy.getLocation());
                    closestEnemy = enemy;
                }
            }
            if(closestEnemy != null && rc.canAttack(closestEnemy.getLocation())) rc.attack(closestEnemy.getLocation());
            if(closestEnemy == null && rc.canTransform())  rc.transform();
        }
        else if(rc.getMode() == RobotMode.PORTABLE){
            if(rc.senseNearbyRobots(RobotType.WATCHTOWER.actionRadiusSquared, rc.getTeam().opponent()).length > 0
                    && rc.canTransform())
                rc.transform();
            else explore(rc);
        }
    }

    static void BuildWatchtowers(RobotController rc) throws GameActionException{
        MapLocation[] locations = rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), 20);
        MapLocation bestPrototype = null;
        int bestDistance = 10000;
        for(MapLocation location : locations){
            RobotInfo robot = rc.senseRobotAtLocation(location);
            if(robot != null && robot.mode == RobotMode.PROTOTYPE && rc.onTheMap(location)
                    && rc.getLocation().distanceSquaredTo(robot.getLocation()) < bestDistance){
                bestDistance = rc.getLocation().distanceSquaredTo(robot.getLocation());
                bestPrototype = robot.getLocation();
            }
        }
        if(bestPrototype != null){
            if(rc.canRepair(bestPrototype)) rc.repair(bestPrototype);
            else navigateToLocation(rc, bestPrototype);
        }
        else{
            MapLocation bestLocation = null;
            bestDistance = 10000;
            for(MapLocation location : locations){
                if(location.x % 2 == 0 && location.y % 2 == 0 && rc.senseRobotAtLocation(location) == null
                        && rc.getLocation().distanceSquaredTo(location) < bestDistance){
                    bestDistance = rc.getLocation().distanceSquaredTo(location);
                    bestLocation = location;
                }
            }
            if(bestLocation == null) return;
            if(!rc.getLocation().add(rc.getLocation().directionTo(bestLocation)).equals(bestLocation))
                navigateToLocation(rc, bestLocation);
            else if(rc.getTeamLeadAmount(rc.getTeam()) > 2000
                    && rc.canBuildRobot(RobotType.WATCHTOWER, rc.getLocation().directionTo(bestLocation)))
                rc.buildRobot(RobotType.WATCHTOWER, rc.getLocation().directionTo(bestLocation));
        }
    }

    static void StartLeadFarm(RobotController rc) throws GameActionException{
        if(rc.senseLead(rc.getLocation()) == 0) rc.disintegrate();
        int bestDistance = 10000;
        MapLocation bestLocation = null;
        for(MapLocation location : rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), 20)){
            if(rc.senseLead(location) == 0 && rc.getLocation().distanceSquaredTo(location) < bestDistance
                    && rc.senseRobotAtLocation(location) != null){
                bestDistance = rc.getLocation().distanceSquaredTo(location);
                bestLocation = location;
            }
        };
        if(bestLocation == null) explore(rc);
        else {
            navigateToLocation(rc, bestLocation);
            rc.setIndicatorString(bestLocation.x + " " + bestLocation.y);
        }
    }

    static void explore(RobotController rc) throws GameActionException {
        if(target == null || target.equals(rc.getLocation())) target = new MapLocation(rng.nextInt(rc.getMapWidth()), rng.nextInt(rc.getMapHeight()));
        navigateToLocation(rc, target);
    }

    static void navigateToLocation(RobotController rc, MapLocation end) throws GameActionException {
        float averageRubble = 0;
        MapLocation[] locations = rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), rc.getType().visionRadiusSquared);
        for (MapLocation location : locations) {
            averageRubble += rc.senseRubble(location);
        }
        averageRubble /= locations.length;
        averageRubble += 1;
        MapLocation start = rc.getLocation();
        int bestEstimate = 100000;
        Direction bestDirection = Direction.CENTER;
        for (Direction direction : Direction.values()) {
            if(direction == Direction.CENTER) continue;
            if(!rc.canMove(direction)) continue;
            if(start.distanceSquaredTo(end) <= start.add(direction).distanceSquaredTo(end)) continue;
            int estimate = rc.senseRubble(start.add(direction));
            estimate += averageRubble * Math.sqrt((start.add(direction)).distanceSquaredTo(end));
            if(estimate < bestEstimate || (estimate == bestEstimate && start.add(direction).distanceSquaredTo(end) < start.add(bestDirection).distanceSquaredTo(end))){
                bestEstimate = estimate;
                bestDirection = direction;
            }
        }
        if(bestDirection != Direction.CENTER){
            if(rc.canMove(bestDirection)) rc.move(bestDirection);
            else if(rc.canMove(bestDirection.rotateLeft())) rc.move(bestDirection.rotateLeft());
            else if(rc.canMove(bestDirection.rotateRight())) rc.move(bestDirection.rotateRight());
        }
    }

    static MapLocation[] GetAllSoldierDestinations(RobotController rc) throws GameActionException{
        List<MapLocation> locations = new ArrayList<>();
        for(int i = soldierIndexStart; i <= soldierIndexEnd; i++)
            if(rc.readSharedArray(i) > 0) locations.add(GetSoldierDestination(rc, i));
        return locations.toArray(new MapLocation[0]);
    }

    static void AddSoldierDestination(RobotController rc, MapLocation location) throws GameActionException{
        RemoveSoldierDestination(rc, location);
        for(int i = soldierIndexStart; i <= soldierIndexEnd; i++){
            if(rc.readSharedArray(i) == 0){
                rc.writeSharedArray(i, location.x + location.y * 60);
                break;
            }
        }
    }

    static void RemoveSoldierDestination(RobotController rc, MapLocation location) throws GameActionException{
        for(int i = soldierIndexStart; i <= soldierIndexEnd; i++){
            if(GetSoldierDestination(rc, i).equals(location)){
                rc.writeSharedArray(i, 0);
                return;
            }
        }
    }

    static MapLocation GetSoldierDestination(RobotController rc, int index) throws GameActionException{
        int value = rc.readSharedArray(index);
        return new MapLocation(value % 60, (value % 3600) / 60);
    }
}
