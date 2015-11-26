package cv.lecturesight.ptzcontrol.dummy;

import cv.lecturesight.ptz.api.CameraListener;
import cv.lecturesight.ptz.api.PTZCamera;
import cv.lecturesight.ptz.api.PTZCameraProfile;
import cv.lecturesight.util.Log;
import cv.lecturesight.util.conf.Configuration;
import cv.lecturesight.util.geometry.Position;
import java.util.LinkedList;
import java.util.List;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;

@Component(name = "cv.lecturesight.ptzcontrol.dummy", immediate = true)
@Service
public class SimulatedCamera implements PTZCamera {

  static final String PROPKEY_DELAY ="delay";
  static final String CAMERA_NAME = "Simulated Camera";
  static final int PAN_MAX_SPEED = 100;
  static final int TILT_MAX_SPEED = 100;
  static final int ZOOM_MAX_SPEED = 100;
  static final int PAN_MIN = -10000;
  static final int PAN_MAX = 10000;
  static final int TILT_MIN = -10000;
  static final int TILT_MAX = 10000;
  static final int ZOOM_MAX = 1000;
  final Position HOME_POS = new Position(0, 0);
  
  static Log log = new Log(CAMERA_NAME);
  
  @Reference
  Configuration config;
  
  PTZCameraProfile myProfile = new PTZCameraProfile(
          "ACME Inc.", CAMERA_NAME,
          PAN_MIN, PAN_MAX, PAN_MAX_SPEED,
          TILT_MIN, TILT_MAX, TILT_MAX_SPEED,
          0, ZOOM_MAX, ZOOM_MAX_SPEED,
          HOME_POS);
  
  int delay;
  boolean running = true;
  Position current_pos = HOME_POS.clone();
  Position target_pos = HOME_POS.clone();
  int current_zoom = 0;
  int target_zoom;
  int speedPan = 0;
  int speedTilt = 0;
  int speedZoom = 0;
  
  final Object mutex = new Object();
  List<CameraListener> listeners = new LinkedList<CameraListener>();
  
  protected void activate(ComponentContext cc) {
    delay = config.getInt(PROPKEY_DELAY);
    (new Thread(new Runnable() {

      @Override
      public void run() {
        log.info("Entering main loop");
        
        int dx,dy;
        while(running) {
          synchronized(mutex) {
            
            dx = target_pos.getX() - current_pos.getX();
            dy = target_pos.getY() - current_pos.getY();
            
            // update X
            if (dx > 0 ) {
              current_pos.setX(current_pos.getX() + speedPan);
              if (current_pos.getX() > target_pos.getX()) {
                current_pos.setX(target_pos.getX());
                speedPan = 0;
              }
            } else if (dx < 0) {
              current_pos.setX(current_pos.getX() - speedPan);
              if (current_pos.getX() < target_pos.getX()) {
                current_pos.setX(target_pos.getX());
                speedPan = 0;
              }
            } else {
              speedPan = 0;
            }

            // update Y
            if (dy > 0 ) {
              current_pos.setY(current_pos.getY() + speedTilt);
              if (current_pos.getY() > target_pos.getY()) {
                current_pos.setY(target_pos.getY());
                speedTilt = 0;
              }
            } else if (dy < 0) {
              current_pos.setY(current_pos.getY() - speedTilt);
              if (current_pos.getY() < target_pos.getY()) {
                current_pos.setY(target_pos.getY());
                speedTilt = 0;
              }
            } else {
              speedTilt = 0;
            }
            
            informListeners();
          }
          
          try {
            Thread.sleep(delay);
          } catch (InterruptedException e) {
            log.warn("Worker thread interrupted.");
          }
        }
        
        log.info("Exited main loop");
      }
      
    })).start();
    log.info("Activated. Delay is " + delay + " ms");
  }
  
  protected void deactivate(ComponentContext cc) {
    running = false;
    log.info("Deactivating");
  }
  
  @Override
  public String getName() {
    return CAMERA_NAME;
  }

  @Override
  public PTZCameraProfile getProfile() {
    return myProfile;
  }

  @Override
  public void reset() {
    // TODO ??
  }

  @Override
  public void cancel() {
    synchronized(mutex) {
      target_pos = current_pos.clone();
      target_zoom = current_zoom;
    }
  }

  @Override
  public void stopMove() {
    cancel();
  }

  @Override
  public void moveHome() {
    synchronized(mutex) {
      target_pos = HOME_POS.clone();
      speedPan = PAN_MAX_SPEED / 2;
      speedTilt = TILT_MAX_SPEED / 2;
    }
  }

  @Override
  public void movePreset(int preset) {
    moveHome();       // the only preset in this camera is the home position
  }

  @Override
  public void moveUp(int speed) {
    log.debug("move up (" + speed + ")");
    synchronized(mutex) {
      target_pos.setX(current_pos.getX());
      target_pos.setY(TILT_MAX);
      speedTilt = speed;
    }
  }

  @Override
  public void moveDown(int speed) {
    log.debug("move down (" + speed + ")");
    synchronized(mutex) {
      target_pos.setX(current_pos.getX());
      target_pos.setY(TILT_MIN);
      speedTilt = speed;
    }
  }

  @Override
  public void moveLeft(int speed) {
    log.debug("move left (" + speed + ")");
    synchronized(mutex) {
      target_pos.setX(PAN_MIN);
      target_pos.setY(current_pos.getY());
      speedPan = speed;
    }
  }

  @Override
  public void moveRight(int speed) {
    log.debug("move right (" + speed + ")");
    synchronized(mutex) {
      target_pos.setX(PAN_MAX);
      target_pos.setY(current_pos.getY());
      speedPan = speed;
    }
  }

  @Override
  public void moveUpLeft(int panSpeed, int tiltSpeed) {
    log.debug("move up-left (" + panSpeed + ", " + tiltSpeed + ")");
    synchronized(mutex) {
      target_pos.setX(PAN_MIN);
      target_pos.setY(TILT_MAX);
      speedPan = panSpeed;
      speedTilt = tiltSpeed;
    }
  }

  @Override
  public void moveUpRight(int panSpeed, int tiltSpeed) {
    log.debug("move up-right (" + panSpeed + ", " + tiltSpeed + ")");
    synchronized(mutex) {
      target_pos.setX(PAN_MAX);
      target_pos.setY(TILT_MAX);
      speedPan = panSpeed;
      speedTilt = tiltSpeed;
    }
  }

  @Override
  public void moveDownLeft(int panSpeed, int tiltSpeed) {
    log.debug("move down-left (" + panSpeed + ", " + tiltSpeed + ")");
    synchronized(mutex) {
      target_pos.setX(PAN_MIN);
      target_pos.setY(TILT_MIN);
      speedPan = panSpeed;
      speedTilt = tiltSpeed;
    }
  }

  @Override
  public void moveDownRight(int panSpeed, int tiltSpeed) {
    log.debug("move down-right (" + panSpeed + ", " + tiltSpeed + ")");
    synchronized(mutex) {
      target_pos.setX(PAN_MAX);
      target_pos.setY(TILT_MIN);
      speedPan = panSpeed;
      speedTilt = tiltSpeed;
    }
  }

  @Override
  public void moveAbsolute(int panSpeed, int tiltSpeed, Position target) {
    log.debug("move absolute (" + panSpeed + ", " + tiltSpeed + "," + target + ")");
    synchronized(mutex) {
      clampPosition(target);
      target_pos.setX(target.getX());
      target_pos.setY(target.getY());
      speedPan = panSpeed;
      speedTilt = tiltSpeed;
    }
  }

  @Override
  public void moveRelative(int panSpeed, int tiltSpeed, Position target) {
    log.debug("move relative (" + panSpeed + ", " + tiltSpeed + "," + target + ")");
    synchronized(mutex) {
      Position new_target = current_pos.clone();
      new_target.setX(current_pos.getX() + target.getX());
      new_target.setY(current_pos.getY() + target.getY());
      clampPosition(new_target);
      target_pos.setX(new_target.getX());
      target_pos.setY(new_target.getY());
      speedPan = panSpeed;
      speedTilt = tiltSpeed;
    }
  }
  
  void clampPosition(Position in) {
    int x = in.getX();
    if (x < PAN_MIN) in.setX(PAN_MIN);
    if (x > PAN_MAX) in.setX(PAN_MAX);
    int y = in.getY();
    if (y < TILT_MIN) in.setX(TILT_MIN);
    if (x < TILT_MAX) in.setX(TILT_MAX);
  }

  @Override
  public void clearLimits() {
    log.warn("method clearLimits() currently not implemented");
  }

  @Override
  public void setLimitUpRight(int pan, int tilt) {
    log.warn("method setLimitUpRight() currently not implemented");
  }

  @Override
  public void setLimitDownLeft(int pan, int tilt) {
    log.warn("method setLimitDownLeft() currently not implemented");
  }

  @Override
  public Position getPosition() {
    Position out;
    synchronized(mutex) {
      out = current_pos.clone();
    }
    return out;
  }

  @Override
  public void stopZoom() {
    synchronized(mutex) {
      speedZoom = 0;
      target_zoom = current_zoom;
    }
  }

  @Override
  public void zoomIn(int speed) {
    synchronized(mutex) {
      target_zoom = ZOOM_MAX;
      speedZoom = speed;
    }
  }

  @Override
  public void zoomOut(int speed) {
    synchronized(mutex) {
      target_zoom = 0;
      speedZoom = speed;
    }
  }

  @Override
  public void zoom(int zoom) {
    synchronized(mutex) {
      current_zoom = zoom;
    }
  }

  @Override
  public int getZoom() {
    return current_zoom;
  }

  @Override
  public void addCameraListener(CameraListener l) {
    listeners.add(l);
  }

  @Override
  public void removeCameraListener(CameraListener l) {
    listeners.remove(l);
  }
  
  private void informListeners() {
    for (CameraListener l : listeners) {
      log.debug("informListeners()");
      l.positionUpdated(current_pos.clone());
    }
  }
  
}