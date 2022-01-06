package MainBot;
import battlecode.common.*;

import javax.annotation.processing.SupportedSourceVersion;
import java.util.*;
import java.lang.Math;
import java.util.Random;


public strictfp class RobotPlayer {

    static int turnCount = 0;
    static Random rng;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
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
            } catch (GameActionException e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    static int minerCount = 0;
    static int soldierCount = 0;
    static int builderCount = 0;
    static int minerRounds = 100;
    static void runArchon(RobotController rc) throws GameActionException {
        RobotType toBuild = null;
        if(rc.getRoundNum() <= minerRounds) toBuild = RobotType.MINER;
        else{
            if(minerCount > soldierCount) toBuild = RobotType.SOLDIER;
            else toBuild = RobotType.MINER;
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
        MapLocation me = rc.getLocation();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                MapLocation mineLocation = new MapLocation(me.x + dx, me.y + dy);
                while (rc.canMineGold(mineLocation)) {
                    rc.mineGold(mineLocation);
                }
                while (rc.canMineLead(mineLocation)) {
                    rc.mineLead(mineLocation);
                }
            }
        }
        if(target == null || target.equals(rc.getLocation())) target = new MapLocation(rng.nextInt(rc.getMapWidth()), rng.nextInt(rc.getMapHeight()));
        navigateToLocation(rc, target);
    }

    static void runSoldier(RobotController rc) throws GameActionException {
        int radius = rc.getType().actionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
        if (enemies.length > 0) {
            MapLocation toAttack = enemies[0].location;
            if (rc.canAttack(toAttack)) {
                rc.attack(toAttack);
            }
        }
        explore(rc);
    }

    static MapLocation target;
    static void explore(RobotController rc) throws GameActionException {
        if(target == null || target.equals(rc.getLocation())) target = new MapLocation(rng.nextInt(rc.getMapWidth()), rng.nextInt(rc.getMapHeight()));
        navigateToLocation(rc, target);
    }

    static void navigateToLocation(RobotController rc, MapLocation end) throws GameActionException {
        float averageRubble = 0;
        MapLocation[] locations = rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), 20);
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
            rc.setIndicatorString(end.x + " " + end.y + " ");
            if(estimate < bestEstimate || (estimate == bestEstimate && start.add(direction).distanceSquaredTo(end) < start.add(bestDirection).distanceSquaredTo(end))){
                bestEstimate = estimate;
                bestDirection = direction;
            }
        }
        if(bestDirection != Direction.CENTER) rc.move(bestDirection);
    }
    
    static final double rubbleThreshold = 50;
    static void navigateToLocationBug(RobotController rc, MapLocation target) throws GameActionException {
        Direction direction = rc.getLocation().directionTo(target);
        for (int i = 0; i < 8; ++i) {
            if (rc.canMove(direction) && rc.senseRubble(rc.getLocation().add(direction)) <= rubbleThreshold) {
                rc.move(direction);
                direction = direction.rotateLeft();
                break;
            }
            direction = direction.rotateRight();
        }
    }
}

