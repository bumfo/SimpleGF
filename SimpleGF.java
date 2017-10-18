/*
 * This is free and unencumbered software released into the public domain.

 * Anyone is free to copy, modify, publish, use, compile, sell, or
 * distribute this software, either in source code form or as a compiled
 * binary, for any purpose, commercial or non-commercial, and by any
 * means.

 * In jurisdictions that recognize copyright laws, the author or authors
 * of this software dedicate any and all copyright interest in the
 * software to the public domain. We make this dedication for the benefit
 * of the public at large and to the detriment of our heirs and
 * successors. We intend this dedication to be an overt act of
 * relinquishment in perpetuity of all present and future rights to this
 * software under copyright law.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.

 * For more information, please refer to <http://unlicense.org/>
 */

package aaa.simple;
import robocode.*;
import robocode.util.Utils;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.*;
import java.util.Arrays;
 
// SimpleGF by Xor, modified verison of GFTargetingBot by PEZ
 
public class SimpleGF extends AdvancedRobot {
  
  private static SimpleMovement movement;
  private static SimpleGun gun;
 
  public SimpleGF() {
    movement = new SimpleMovement(this); 
    gun = new SimpleGun(this); 
  }
 

  public void run() {
    // setColors(Color.BLUE, Color.BLACK, Color.YELLOW);
    setColors(Color.BLUE, Color.BLUE, Color.BLUE);
    gun.run();
    setAdjustRadarForGunTurn(true);
    setAdjustGunForRobotTurn(true);
    do {
      turnRadarRightRadians(Double.POSITIVE_INFINITY); 
    } while (true);
  }
 
  public void onScannedRobot(ScannedRobotEvent e) {
    double enemyAbsoluteBearing = getHeadingRadians() + e.getBearingRadians();

    movement.onScannedRobot(e);
    gun.onScannedRobot(e);
    setTurnRadarRightRadians(Utils.normalRelativeAngle(enemyAbsoluteBearing - getRadarHeadingRadians()) * 2);
  }


  public void onPaint(Graphics2D g) {
    gun.onPaint(g);
  }

}

class SimpleGun {
  private static final double BULLET_POWER = 1.9;

  private static final int BINS = 25;
  private static final double MAX_DISTANCE = 1000;
  
  private static double lateralDirection;
  private static double lastEnemyVelocity;

  private static Object statBuffers;

  private static Point2D targetLocation;

  private SimpleWave lastWave = null;

  private enum ATTRIBUTE {
    DISTANCE(0, 5) {
      public int getIndex(GunData data) {
        return (int) (data.distance / (MAX_DISTANCE / this.slices));
      }
    },
    VELOCITY(1, 5) {
      public int getIndex(GunData data) {
        return (int) Math.abs(data.velocity / 2);
      }
    },
    ACCEL(2, 3) {
      public int getIndex(GunData data) {
        double accel = (data.velocity > 0.0 ? 1.0 : -1.0) * (data.velocity - data.lastVelocity);
        return (Math.abs(accel) > 0.4 ? (accel > 0 ? 2 : 0) : 1);
      }
    };

    protected final int value;
    protected final int slices;
    private ATTRIBUTE(int value, int slices) {
      this.value = value;
      this.slices = slices;
    }

    public abstract int getIndex(GunData data);
  }

  private static final ATTRIBUTE[] attributes = {
    ATTRIBUTE.DISTANCE,
    ATTRIBUTE.VELOCITY,
    ATTRIBUTE.ACCEL,
  };

  static {
    initBuffers();
  }

  private static void initBuffers() {
    if (attributes.length != 0) {
      int capacity = 1;
      for (int i = 0; i < attributes.length; ++i) {
        capacity *= attributes[i].slices;
      }

      statBuffers = new int[capacity][BINS];
    } else {
      statBuffers = new int[BINS];
    }
  }

  private AdvancedRobot robot;

  public SimpleGun(AdvancedRobot robot) {
    this.robot = robot;
  }

  private int[] getBuffers(int[] indexes) {
    if (attributes.length != 0) {
      int index = indexes[attributes.length - 1];
      int dimension = 1;
      for (int i = attributes.length - 2; i >= 0; --i) {
        dimension *= attributes[i + 1].slices;
        index += dimension * indexes[i];
      }

      return ((int[][])statBuffers)[index];
    } else {
      return (int[])statBuffers;
    }
  }

  private int[] getIndexes(GunData data) {
    int[] indexes = new int[attributes.length];
    for (int i = 0; i < attributes.length; ++i) {
      indexes[i] = attributes[i].getIndex(data);
    }

    System.out.println(Arrays.toString(indexes));

    return indexes;
  }

  void run() {
    lateralDirection = 1;
    lastEnemyVelocity = 0;
  }

  public void onScannedRobot(ScannedRobotEvent e) {
    double enemyAbsoluteBearing = robot.getHeadingRadians() + e.getBearingRadians();
    double enemyDistance = e.getDistance();
    double enemyVelocity = e.getVelocity();
    if (enemyVelocity != 0) {
      lateralDirection = SimpleUtils.sign(enemyVelocity * Math.sin(e.getHeadingRadians() - enemyAbsoluteBearing));
    }
    SimpleWave wave = new SimpleWave(robot);
    wave.gunLocation = new Point2D.Double(robot.getX(), robot.getY());
    targetLocation = SimpleUtils.project(wave.gunLocation, enemyAbsoluteBearing, enemyDistance);
    wave.lateralDirection = lateralDirection;
    wave.bulletPower = BULLET_POWER;

    GunData data = new GunData();

    data.distance = enemyDistance;
    data.velocity = enemyVelocity;
    data.lastVelocity = lastEnemyVelocity;

    wave.setSegmentations(data);
    lastEnemyVelocity = enemyVelocity;
    wave.bearing = enemyAbsoluteBearing;
    robot.setTurnGunRightRadians(Utils.normalRelativeAngle(enemyAbsoluteBearing - robot.getGunHeadingRadians() + wave.mostVisitedBearingOffset()));
    robot.setFire(wave.bulletPower);
    if (robot.getEnergy() >= BULLET_POWER) {
      robot.addCustomEvent(wave);
    }

    lastWave = wave;
  }

  public void onPaint(Graphics2D g) {
    int w = 10;
    if (lastWave != null) {
      g.setColor(new Color(1f, 1f, 1f, 0.5f));
      int max = 0;
      for (int i = 0; i < lastWave.buffer.length; ++i) {
        if (lastWave.buffer[i] > max) {
          max = lastWave.buffer[i];
        }
      }
      for (int i = 0; i < lastWave.buffer.length; ++i) {
        g.fill(new Rectangle2D.Double(75 + i * w, 25, w, 1.0 * lastWave.buffer[i] / max * 100));
      }
    }
  }


  class SimpleWave extends Condition {   
    double bulletPower;
    Point2D gunLocation;
    double bearing;
    double lateralDirection;
   
    private static final int DISTANCE_INDEXES = 5;
    private static final int VELOCITY_INDEXES = 5;
    private static final int MIDDLE_BIN = (BINS - 1) / 2;
    private static final double MAX_ESCAPE_ANGLE = 0.7;
    private static final double BIN_WIDTH = MAX_ESCAPE_ANGLE / (double)MIDDLE_BIN;
   
    //private static int[][][][] statBuffers = new int[DISTANCE_INDEXES][VELOCITY_INDEXES][VELOCITY_INDEXES][BINS];
    
    // private static int[] statBuffers = new int[BINS];

   
    private int[] buffer;
    private AdvancedRobot robot;
    private double distanceTraveled;
   
    SimpleWave(AdvancedRobot _robot) {
      this.robot = _robot;
    }
   
    public boolean test() {
      advance();
      if (hasArrived()) {
        buffer[currentBin()]++;
        robot.removeCustomEvent(this);
      }
      return false;
    }
   
    double mostVisitedBearingOffset() {
      return (lateralDirection * BIN_WIDTH) * (mostVisitedBin() - MIDDLE_BIN);
    }
   
    void setSegmentations(GunData data) {
      

      buffer = getBuffers(getIndexes(data));
      //buffer = statBuffers[distanceIndex][velocityIndex][lastVelocityIndex];
      // buffer = statBuffers;
    }
   
    private void advance() {
      distanceTraveled += SimpleUtils.bulletVelocity(bulletPower);
    }
   
    private boolean hasArrived() {
      return distanceTraveled > gunLocation.distance(targetLocation) - 18;
    }
   
    private int currentBin() {
      int bin = (int)Math.round(((Utils.normalRelativeAngle(SimpleUtils.absoluteBearing(gunLocation, targetLocation) - bearing)) /
          (lateralDirection * BIN_WIDTH)) + MIDDLE_BIN);
      return SimpleUtils.minMax(bin, 0, BINS - 1);
    }
   
    private int mostVisitedBin() {
      int mostVisited = MIDDLE_BIN;
      for (int i = 0; i < BINS; i++) {
        if (buffer[i] > buffer[mostVisited]) {
          mostVisited = i;
        }
      }
      return mostVisited;
    } 

  }

  private class GunData {
    double distance, velocity, lastVelocity;
  }
}
 
 
class SimpleUtils {
  static double bulletVelocity(double power) {
    return 20 - 3 * power;
  }
 
  static Point2D project(Point2D sourceLocation, double angle, double length) {
    return new Point2D.Double(sourceLocation.getX() + Math.sin(angle) * length,
        sourceLocation.getY() + Math.cos(angle) * length);
  }
 
  static double absoluteBearing(Point2D source, Point2D target) {
    return Math.atan2(target.getX() - source.getX(), target.getY() - source.getY());
  }
 
  static int sign(double v) {
    return v < 0 ? -1 : 1;
  }
 
  static int minMax(int v, int min, int max) {
    return Math.max(min, Math.min(max, v));
  }
}
 
class SimpleMovement {
  private static final double BATTLE_FIELD_WIDTH = 800;
  private static final double BATTLE_FIELD_HEIGHT = 600;
  private static final double WALL_MARGIN = 18;
  private static final double MAX_TRIES = 125;
  private static final double REVERSE_TUNER = 0.421075;
  private static final double DEFAULT_EVASION = 1.2;
  private static final double WALL_BOUNCE_TUNER = 0.699484;
 
  private AdvancedRobot robot;
  private Rectangle2D fieldRectangle = new Rectangle2D.Double(WALL_MARGIN, WALL_MARGIN,
    BATTLE_FIELD_WIDTH - WALL_MARGIN * 2, BATTLE_FIELD_HEIGHT - WALL_MARGIN * 2);
  private double enemyFirePower = 3;
  private double direction = 0.4;
 
  SimpleMovement(AdvancedRobot _robot) {
    this.robot = _robot;
  }
 
  public void onScannedRobot(ScannedRobotEvent e) {
    double enemyAbsoluteBearing = robot.getHeadingRadians() + e.getBearingRadians();
    double enemyDistance = e.getDistance();
    Point2D robotLocation = new Point2D.Double(robot.getX(), robot.getY());
    Point2D enemyLocation = SimpleUtils.project(robotLocation, enemyAbsoluteBearing, enemyDistance);
    Point2D robotDestination;
    double tries = 0;
    while (!fieldRectangle.contains(robotDestination = SimpleUtils.project(enemyLocation, enemyAbsoluteBearing + Math.PI + direction,
        enemyDistance * (DEFAULT_EVASION - tries / 100.0))) && tries < MAX_TRIES) {
      tries++;
    }
    if ((Math.random() < (SimpleUtils.bulletVelocity(enemyFirePower) / REVERSE_TUNER) / enemyDistance ||
        tries > (enemyDistance / SimpleUtils.bulletVelocity(enemyFirePower) / WALL_BOUNCE_TUNER))) {
      direction = -direction;
    }
    // Jamougha's cool way
    double angle = SimpleUtils.absoluteBearing(robotLocation, robotDestination) - robot.getHeadingRadians();
    robot.setAhead(Math.cos(angle) * 100);
    robot.setTurnRightRadians(Math.tan(angle));
  }
}
