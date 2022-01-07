    static MapLocation readCoordinate(RobotController rc, int index) throws GameActionException {
        int value = rc.readSharedArray(index);
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
