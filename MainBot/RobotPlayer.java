package MainBot;
import battlecode.common.*;

import java.util.*;
import java.lang.Math;
import java.util.Random;


public strictfp class RobotPlayer {

    static Random rng;
    static int minerCount = 0;
    static int soldierCount = 0;
    static int builderCount = 0;
    static int minerRounds = 20;
    static int maxMinersPerArea = 10;
    static MapLocation target;
    static final double rubbleThreshold = 50;
    static RobotType[] attackPriority = new RobotType[] {RobotType.ARCHON, RobotType.SAGE,
            RobotType.SOLDIER, RobotType.WATCHTOWER, RobotType.LABORATORY, RobotType.MINER, RobotType.BUILDER};

    @SuppressWarnings("unused")
    public static void run(RobotController rc) {
        rng = new Random(rc.getID());
        while (true) {
            try {
                switch (rc.getType()) {
                    case ARCHON:     runArchon(rc);  break;
                    case MINER:      runMiner(rc);   break;
                    case SOLDIER:    runSoldier(rc); break;
                    case LABORATORY:
                    case WATCHTOWER:
                    case BUILDER:
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
        RobotType toBuild = null;
        if(rc.getRoundNum() <= minerRounds) toBuild = RobotType.MINER;
        else{
            if(minerCount * 2> soldierCount) toBuild = RobotType.SOLDIER;
            else toBuild = RobotType.MINER;
        }

        RobotInfo[] robots = rc.senseNearbyRobots();
        for(RobotInfo robot : robots){
            if(robot.team != rc.getTeam()){
                directSoldiers(rc, robot.getLocation(), 6);
                toBuild = RobotType.SOLDIER;
                break;
            }
        }

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
        for(RobotInfo robot : rc.senseNearbyRobots()) if(robot.type == RobotType.ARCHON) explore(rc);
        MapLocation[] golds = rc.senseNearbyLocationsWithGold(RobotType.MINER.visionRadiusSquared);
        if(golds.length > 0) {
            navigateToLocation(rc, golds[0]);
            while (rc.canMineGold(golds[0])) rc.mineGold(golds[0]);
        }
        else {
            MapLocation[] possibleLeads = rc.senseNearbyLocationsWithLead(2);
            List<MapLocation> leads = new ArrayList<>();
            for (MapLocation possibleLead : possibleLeads) if (rc.senseLead(possibleLead) > 1) leads.add(possibleLead);
            if(!leads.isEmpty()) {
                MapLocation lead = leads.get(0);
                while (rc.canMineLead(lead) && rc.senseLead(lead) > 1){
                    rc.mineLead(lead);
                }
            }
            else {
                possibleLeads = rc.senseNearbyLocationsWithLead(RobotType.MINER.visionRadiusSquared);
                leads = new ArrayList<>();
                for (MapLocation possibleLead : possibleLeads)
                    if (rc.senseLead(possibleLead) > 1) leads.add(possibleLead);
                if (!leads.isEmpty()) navigateToLocation(rc, leads.get(0));
                else explore(rc);
            }
        }
    }

    static void runSoldier(RobotController rc) throws GameActionException {
        rc.setIndicatorString(soldiersDestination(rc) + " " + soldiersDirected(rc));
        boolean exit = false;
        int radius = rc.getType().actionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        if(soldiersDirected(rc) > 0 && rc.getLocation().distanceSquaredTo(soldiersDestination(rc)) < 3)
            cancelSoldierDirections(rc);

        RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
        if (enemies.length > 0) {
            for(RobotType type : attackPriority) {
                for(RobotInfo enemy : enemies){
                    if(enemy.type != type) continue;
                    MapLocation toAttack = enemies[0].location;
                    int priority;
                    switch (type){
                        case BUILDER:
                        case MINER: priority = 2; break;
                        case SOLDIER:
                        case SAGE: priority = 3; break;
                        case WATCHTOWER:
                        case LABORATORY: priority = 4; break;
                        case ARCHON: priority = 5; break;
                        default: priority = 0; break;
                    }
                    directSoldiers(rc, toAttack, priority);
                    if (rc.canAttack(toAttack)) rc.attack(toAttack);
                    navigateToLocation(rc, toAttack);
                    exit = true;
                    break;
                }
                if(exit) break;
            }
        }
        else if(soldiersDirected(rc) > 0) navigateToLocation(rc, soldiersDestination(rc));
        else {
            explore(rc);
            directSoldiers(rc, target, 1);
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
        if(bestDirection != Direction.CENTER) rc.move(bestDirection);
    }

    static void navigateToLocationBug(RobotController rc, MapLocation target) throws GameActionException {
        Direction direction = rc.getLocation().directionTo(target);
        for (int i = 0; i < 8; ++i) {
            if (rc.canMove(direction) && rc.senseRubble(rc.getLocation().add(direction)) <= rubbleThreshold) {
                rc.move(direction);
                break;
            }
            direction = direction.rotateRight();
        }
    }

    static MapLocation soldiersDestination(RobotController rc) throws GameActionException{
        if(soldiersDirected(rc) > 0) return readCoordinate(rc, 0);
        else return null;
    }

    static int soldiersDirected(RobotController rc) throws GameActionException{
        return rc.readSharedArray(1);
    }

    static boolean directSoldiers(RobotController rc, MapLocation location, int priority) throws GameActionException{
        if(rc.readSharedArray(1) >= priority) return false;
        else{
            rc.writeSharedArray(1, priority);
            writeCoordinate(rc, 0, location.x, location.y);
            return true;
        }
    }

    static void cancelSoldierDirections(RobotController rc) throws GameActionException  {
        rc.writeSharedArray(1, 0);
    }

    static MapLocation readCoordinate(RobotController rc, int index) throws GameActionException {
        int value = rc.readSharedArray(index) % 4096;
        return(new MapLocation(value % 60, value / 60));
    }

    static void writeCoordinate(RobotController rc, int index, int xValue, int yValue) throws GameActionException {
        int flag = rc.readSharedArray(index) / 4096;
        rc.writeSharedArray(index, flag + (xValue + (yValue * 60)));
    }

    static boolean readFlag(RobotController rc, int index) throws GameActionException{
        int value = rc.readSharedArray(index);
        return(value / 4096 > 0);
    }

    static void writeFlag(RobotController rc, int index, boolean value) throws GameActionException{
        int coordinate = rc.readSharedArray(index) % 4096;
        rc.writeSharedArray(index, (value ? 1 : 0) * 4096 + coordinate);
    }
}

