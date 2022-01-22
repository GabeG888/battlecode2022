package MainBot;
import battlecode.common.*;

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
    static int laboratoryCount = 0;
    static int sageCount = 0;
    static int maxMinersPerArea = 10;
    static MapLocation target;
    static RobotType[] attackPriority = new RobotType[] {RobotType.SAGE, RobotType.WATCHTOWER,
            RobotType.SOLDIER, RobotType.ARCHON, RobotType.LABORATORY, RobotType.MINER, RobotType.BUILDER};
    static HashMap<RobotType, Integer> priorityMap = new HashMap<>();
    static int turnOrderIndex = 0;
    static int soldierIndexStart = 2;
    static int soldierIndexEnd = 63;
    static MapLocation startingLocation = null;
    static RobotType[] attackingTypes;

    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        attackingTypes = new RobotType[] {RobotType.SOLDIER, RobotType.SAGE, RobotType.WATCHTOWER};
        priorityMap.put(RobotType.BUILDER, 2);
        priorityMap.put(RobotType.MINER, 2);
        priorityMap.put(RobotType.SOLDIER, 3);
        priorityMap.put(RobotType.SAGE, 3);
        priorityMap.put(RobotType.WATCHTOWER, 4);
        priorityMap.put(RobotType.LABORATORY, 4);
        priorityMap.put(RobotType.ARCHON, 5);
        rng = new Random(rc.getID());
        startingLocation = rc.getLocation();
        while (true) {
            try {
                switch (rc.getType()) {
                    case ARCHON:     runArchon(rc);  break;
                    case MINER:      runMiner(rc);   break;
                    case SOLDIER:    runSoldier(rc); break;
                    case LABORATORY: runLaboratory(rc); break;
                    case WATCHTOWER: runWatchtower(rc); break;
                    case BUILDER: runBuilder(rc); break;
                    case SAGE: runSage(rc); break;
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
        if((rc.getTeamLeadAmount(rc.getTeam()) > 100 || rng.nextInt(rc.getArchonCount() - turnIndex) == 0)
                && rc.readSharedArray(1) != 1){
            RobotType toBuild;
            if(builderCount < 1 && rc.getRoundNum() > 25 && turnIndex == 0) toBuild = RobotType.BUILDER;
            else if(GetAllSoldierDestinations(rc).length == 0) toBuild = RobotType.MINER;
            else if(rc.getTeamGoldAmount(rc.getTeam()) > RobotType.SAGE.buildCostGold && sageCount * 5 < soldierCount)
                toBuild = RobotType.SAGE;
            else if(rc.getTeamLeadAmount(rc.getTeam()) > 2000 && builderCount < 10) toBuild = RobotType.BUILDER;
            else if(minerCount * 2 > soldierCount) toBuild = RobotType.SOLDIER;
            else toBuild = RobotType.MINER;
            for(Direction direction : Direction.values()) {
                if (rc.canBuildRobot(toBuild, direction)) {
                    rc.buildRobot(toBuild, direction);
                    switch (toBuild) {
                        case MINER: minerCount++; break;
                        case SOLDIER: soldierCount++; break;
                        case BUILDER: builderCount++; break;
                        case SAGE: sageCount++; break;
                    }
                    break;
                }
            }
        }
        for(RobotInfo robot : rc.senseNearbyRobots(20, rc.getTeam())){
            if(robot.type.equals(RobotType.SOLDIER) && robot.health < robot.type.getMaxHealth(1)
                    && rc.canRepair(robot.getLocation())) rc.repair(robot.getLocation());
        }
    }

    static void runMiner(RobotController rc) throws GameActionException {
        boolean archon = false;
        for(RobotInfo robot : rc.senseNearbyRobots(20, rc.getTeam()))
            if(robot.getType().equals(RobotType.ARCHON)){
                archon = true;
                break;
            }
        if(rc.getHealth() < 10 && !archon) MoveToArchon(rc);
        else if(archon && rc.getHealth() < 10) StartLeadFarm(rc);
        if(rc.senseNearbyRobots().length > maxMinersPerArea) explore(rc);
        for(RobotInfo robot: rc.senseNearbyRobots(20, rc.getTeam().opponent())){
            AddSoldierDestination(rc, robot.getLocation());
            if(Arrays.asList(attackingTypes).contains(robot.type)
                    && rc.canMove(rc.getLocation().directionTo(robot.getLocation()).opposite()))
                rc.move(rc.getLocation().directionTo(robot.getLocation()).opposite());
        }
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
                                && (rc.senseLead(rc.getLocation().add(direction)) > 1
                                || (rc.senseLead(rc.getLocation().add(direction)) > 1) && archon))
                            rc.mineLead(rc.getLocation().add(direction));
                }
            }
        }
    }

    static void runSoldier(RobotController rc) throws GameActionException {
        boolean archon = false;
        for(RobotInfo robot : rc.senseNearbyRobots(20, rc.getTeam()))
            if(robot.getType().equals(RobotType.ARCHON)){
                archon = true;
                break;
            }
        AttackLowestHealth(rc);
        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
        if(rc.getHealth() < 10 && !archon) MoveToArchon(rc);
        else if (enemies.length > 0) MoveBestRubble(rc);
        else if(archon && rc.getHealth() < 10) StartLeadFarm(rc);
        else if(GetAllSoldierDestinations(rc).length > 0) MoveToTarget(rc);
        else explore(rc);
    }

    static void runBuilder(RobotController rc) throws GameActionException{
        if(laboratoryCount < 1) rc.writeSharedArray(1, 1);
        else rc.writeSharedArray(1, 0);
        if(!RepairBuildings(rc)){
            if(laboratoryCount < 1) BuildLaboratories(rc);
            else if(rc.getTeamLeadAmount(rc.getTeam()) > 2000) BuildWatchtowers(rc);
            else StartLeadFarm(rc);
        }
    }

    static void runSage(RobotController rc) throws GameActionException{
        boolean archon = false;
        for(RobotInfo robot : rc.senseNearbyRobots(RobotType.ARCHON.actionRadiusSquared, rc.getTeam()))
            if(robot.getType().equals(RobotType.ARCHON)){
                archon = true;
                break;
            }
        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
        RobotInfo[] allies = rc.senseNearbyRobots(rc.getType().visionRadiusSquared, rc.getTeam());
        MapLocation location = rc.getLocation();
        if(rc.canEnvision(AnomalyType.CHARGE) && enemies.length > 3) rc.envision(AnomalyType.CHARGE);
        else AttackLowestHealth(rc);
        if((rc.getHealth() < 15 || (allies.length < 5 && enemies.length > 0)) && !archon) MoveToArchon(rc);
        if (enemies.length > 0) {
            RobotInfo closestEnemy = null;
            int closestDistance = 10000;
            for(RobotInfo enemy : enemies){
                if(location.distanceSquaredTo(enemy.location) < closestDistance
                        && Arrays.asList(attackingTypes).contains(enemy.type)){
                    closestEnemy = enemy;
                    closestDistance = location.distanceSquaredTo(enemy.location);
                }
            }
            if(closestEnemy != null){
                if(closestDistance > closestEnemy.type.actionRadiusSquared){
                    navigateToLocation(rc, enemies[0].location);
                }
                else{
                    navigateToLocation(rc, location.add(location.directionTo(closestEnemy.location).opposite()).add(
                            location.directionTo(closestEnemy.location).opposite()));
                }
            }
        }
        else if(archon && rc.getHealth() < rc.getType().getMaxHealth(rc.getLevel()) * 2 / 3) return;
        else if(GetAllSoldierDestinations(rc).length > 0) MoveToTarget(rc);
        else explore(rc);
    }

    static void runLaboratory(RobotController rc) throws GameActionException{
        if(rc.canTransmute() && rc.getTeamGoldAmount(rc.getTeam()) < RobotType.SAGE.buildCostGold * 2) rc.transmute();
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

    static void SacrificeArchon(RobotController rc) throws GameActionException{
        for(Direction direction : Direction.values()){
            if(rc.canBuildRobot(RobotType.MINER, direction)){
                rc.buildRobot(RobotType.MINER, direction);
                rc.disintegrate();
            }
        }
    }

    static void BuildLaboratories(RobotController rc) throws GameActionException{
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
            RobotInfo archon = null;
            for(RobotInfo robot : rc.senseNearbyRobots(20, rc.getTeam()))
                if(robot.type.equals(RobotType.ARCHON)) {
                    archon = robot;
                    break;
                }
            Direction direction = rc.getLocation().directionTo(
                    new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2)).opposite();
            if(archon != null && rc.onTheMap(rc.getLocation().add(direction))
                    && !rc.isLocationOccupied(rc.getLocation().add(direction)))
                navigateToLocation(rc, rc.getLocation().add(direction).add(direction).add(direction));
            else{
                for(Direction direction1 : Direction.values()) if(rc.canBuildRobot(RobotType.LABORATORY, direction1)){
                    rc.buildRobot(RobotType.LABORATORY, direction1);
                    laboratoryCount++;
                    break;
                }
            }
        }
    }

    static void BuildWatchtowers(RobotController rc) throws GameActionException{
        MapLocation[] locations = rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), 20);
        MapLocation bestLocation = null;
        int bestDistance = 10000;
        for(MapLocation location : locations){
            if(rc.senseRobotAtLocation(location) == null
                    && rc.getLocation().distanceSquaredTo(location) < bestDistance){
                bestDistance = rc.getLocation().distanceSquaredTo(location);
                bestLocation = location;
            }
        }
        if(bestLocation == null) return;
        if(!rc.getLocation().add(rc.getLocation().directionTo(bestLocation)).equals(bestLocation))
            navigateToLocation(rc, bestLocation);
        else if(rc.canBuildRobot(RobotType.WATCHTOWER, rc.getLocation().directionTo(bestLocation)))
            rc.buildRobot(RobotType.WATCHTOWER, rc.getLocation().directionTo(bestLocation));
    }

    static boolean RepairBuildings(RobotController rc) throws GameActionException{
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
            return true;
        }
        return false;
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

    static void AttackLowestHealth(RobotController rc) throws GameActionException{
        int radius = rc.getType().actionRadiusSquared;
        Team opponent = rc.getTeam().opponent();
        boolean exit = false;
        RobotInfo[] enemies = rc.senseNearbyRobots(radius, opponent);
        if(enemies.length < 1) return;
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
                    AddSoldierDestination(rc, enemies[0].location);
                    exit = true;
                    break;
                }
            }
            if (exit) break;
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

    static void MoveBestRubble(RobotController rc) throws GameActionException{
        int bestRubble = 1000;
        Direction bestDirection = null;
        MapLocation start = rc.getLocation();
        for(Direction direction : Direction.values()){
            if(!rc.onTheMap(start.add(direction))) continue;
            int rubble = rc.senseRubble(start.add(direction));
            if(rubble < bestRubble){
                bestRubble = rubble;
                bestDirection = direction;
            }
        }
        if(bestDirection != null && rc.canMove(bestDirection)) rc.move(bestDirection);
    }

    static void MoveToArchon(RobotController rc) throws GameActionException{
        for(RobotInfo robot : rc.senseNearbyRobots(20, rc.getTeam()))
            if(robot.type.equals(RobotType.ARCHON)) return;
        navigateToLocation(rc, startingLocation);
    }

    static void MoveToTarget(RobotController rc) throws GameActionException{
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
}
