/* Copyright (C) 2012 Benjamin Wulff
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */
package cv.lecturesight.ptz.steering.impl;

import cv.lecturesight.ptz.api.PTZCamera;
import cv.lecturesight.ptz.steering.api.CameraSteeringWorker;
import cv.lecturesight.ptz.steering.api.UISlave;
import cv.lecturesight.util.Log;
import cv.lecturesight.util.conf.Configuration;
import cv.lecturesight.util.geometry.NormalizedPosition;
import cv.lecturesight.util.geometry.Position;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;

/**
 * Camera Steering SteeringWorker Implementation
 *
 */
@Component(name = "lecturesight.ptz.steering.worker", immediate = true)
@Service
public class CameraSteeringWorkerImpl implements CameraSteeringWorker {

  private Log log = new Log("Camera Steering Worker");

  @Reference
  Configuration config;       // service configuration

  @Reference
  PTZCamera camera;           // PTZCamera implementation

  CameraPositionModel model;  // model mapping normalized coords <--> camera coords

  SteeringWorker worker;              // worker updating the pan and tilt speed
  ScheduledExecutorService executor;  // executor service running the worker

  int pan_min, pan_max;               // pan limits
  int tilt_min, tilt_max;             // tilt limits
  int maxspeed_pan, maxspeed_tilt;    // max pan and tilt speeds 
  int alpha_x, alpha_y;               // alpha environment size in x and y direction
  boolean moving = false;             // indicates if the camera if moving

  List<UISlave> slaves;       // list of listeners

  private class SteeringWorker implements Runnable {

    Position camera_pos;
    boolean steering = false;
    int last_ps = 0;
    int last_ts = 0;

    @Override
    public void run() {

      camera_pos = model.getCameraPosition();
      
      // update camera position model
      try {
        Position new_pos = camera.getPosition();
        if (new_pos.getX() != camera_pos.getX() || new_pos.getY() != camera_pos.getY()) {
          moving = true;
          model.setCameraPosition(new_pos);
          camera_pos = new_pos;
        } else {
          moving = false;
          last_ps = 0;
          last_ts = 0;
        }
      } catch (Exception e) {
        log.warn("Unable to update camera postion: " + e.getMessage());
        return;
      }

      if (steering) {
        Position target_pos = model.getTargetPosition();

        int dx = camera_pos.getX() - target_pos.getX();
        int dy = camera_pos.getY() - target_pos.getY();
        int dx_abs = Math.abs(dx);
        int dy_abs = Math.abs(dy);

        // adjust pan speed
        int ps;
        if (dx_abs < alpha_x) {
          ps = (int) (((float) dx_abs / (float) alpha_x) * maxspeed_pan);
        } else {
          ps = maxspeed_pan;
        }

        // adjust tilt speed
        int ts;
        if (dy_abs < alpha_y) {
          ts = (int) (((float) dy_abs / (float) alpha_y) * maxspeed_tilt);
        } else {
          ts = maxspeed_tilt;
        }
        
        if (ps != last_ps || ts != last_ts) {
          System.out.println("Changing pan/tilt speeds:  pan " + last_ps + " > " + ps + "  tilt " + last_ts + " > " + ts);
          camera.moveAbsolute(ps, ts, target_pos);
        }
        
        last_ps = ps;
        last_ts = ts;
      }
      informUISlaves();
    }
  }

  protected void activate(ComponentContext cc) throws Exception {
    model = initModel();
    slaves = new LinkedList<UISlave>();
    maxspeed_pan = camera.getProfile().getPanMaxSpeed();
    maxspeed_tilt = camera.getProfile().getTiltMaxSpeed();
    alpha_x = config.getInt(Constants.PROPKEY_ALPHAX);
    alpha_y = config.getInt(Constants.PROPKEY_ALPHAY);
    worker = new SteeringWorker();
    worker.camera_pos = camera.getPosition();
    startWorker();
    log.info("Activated. Steering " + camera.getName());
    if (config.getBoolean(Constants.PROPKEY_AUTOSTART)) {
      setSteering(true);
    }
  }

  protected void deactivate(ComponentContext cc) throws Exception {
    stopWorker();
    log.info("Deactivated");
  }
  
  private CameraPositionModel initModel() {
    // initialize limits for pan and tilt, if not configured by camera calibration
    // the limits from the camera profile are taken
    String val = config.get(Constants.PROPKEY_LIMIT_LEFT);
    if (val.isEmpty() || val.equalsIgnoreCase("none")) {
      pan_min = camera.getProfile().getPanMin();
    } else {
      pan_min = config.getInt(Constants.PROPKEY_LIMIT_LEFT);
    }

    val = config.get(Constants.PROPKEY_LIMIT_RIGHT);
    if (val.isEmpty() || val.equalsIgnoreCase("none")) {
      pan_max = camera.getProfile().getPanMax();
    } else {
      pan_max = config.getInt(Constants.PROPKEY_LIMIT_RIGHT);
    }

    val = config.get(Constants.PROPKEY_LIMIT_TOP);
    if (val.isEmpty() || val.equalsIgnoreCase("none")) {
      tilt_max = camera.getProfile().getTiltMax();
    } else {
      tilt_max = config.getInt(Constants.PROPKEY_LIMIT_TOP);
    }

    val = config.get(Constants.PROPKEY_LIMIT_BOTTOM);
    if (val.isEmpty() || val.equalsIgnoreCase("none")) {
      tilt_min = camera.getProfile().getTiltMin();
    } else {
      tilt_min = config.getInt(Constants.PROPKEY_LIMIT_BOTTOM);
    }

    return new CameraPositionModel(pan_min, pan_max, tilt_min, tilt_max);
  }

  public void startWorker() {
    executor = Executors.newScheduledThreadPool(1);
    executor.scheduleAtFixedRate(worker, 0,
            config.getInt(Constants.PROPKEY_INTERVAL), TimeUnit.MILLISECONDS);
    log.info("Worker started.");
  }

  public void stopWorker() {
    setSteering(false);
    executor.shutdownNow();
    executor = null;
    log.info("Worker stopped.");
  }

  public void stopMoving() {
    setTargetPosition(model.getCameraPositionNorm());
    try {
      camera.stopMove();
    } catch (Exception e) {
      log.warn("Unable to stop camera movement: " + e.getMessage());
    }
  }

  @Override
  public boolean isSteering() {
    return worker.steering;
  }

  @Override
  public void setSteering(boolean b) {
    worker.steering = b;
    if (b) {
      log.info("Steering is now ON");
    } else {
      stopMoving();
      log.info("Steering is now OFF");
    }
  }

  @Override
  public boolean isMoving() {
    return moving;
  }

  @Override
  public void setTargetPosition(NormalizedPosition pos) {
    model.setTargetPositionNorm(pos);
    informUISlaves();
  }

  @Override
  public NormalizedPosition getTargetPosition() {
    return model.getTargetPositionNorm();
  }

  @Override
  public NormalizedPosition getActualPosition() {
    return model.getCameraPositionNorm();
  }

  @Override
  public void setZoom(float zoom, float speed) {
    // TODO implement!
  }

  @Override
  public float getZoom() {
    return ((float) camera.getZoom()) / ((float) camera.getProfile().getZoomMax());
  }

  @Override
  public int getPanMin() {
    return pan_min;
  }

  @Override
  public int getPanMax() {
    return pan_max;
  }

  @Override
  public int getTiltMin() {
    return tilt_min;
  }

  @Override
  public int getTiltMax() {
    return tilt_max;
  }

  @Override
  public Position toCameraCoordinates(NormalizedPosition posn) {
    return model.toCameraCoordinates(posn);
  }

  @Override
  public void addUISlave(UISlave slave) {
    slaves.add(slave);
  }

  @Override
  public void removeUISlave(UISlave slave) {
    slaves.remove(slave);
  }

  private void informUISlaves() {
    for (UISlave s : slaves) {
      s.refresh();
    }
  }
}
