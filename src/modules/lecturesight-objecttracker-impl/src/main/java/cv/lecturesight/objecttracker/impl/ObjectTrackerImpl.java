package cv.lecturesight.objecttracker.impl;

import cv.lecturesight.decorator.api.DecoratorManager;
import cv.lecturesight.decorator.api.DecoratorManager.CallType;
import cv.lecturesight.framesource.FrameSourceProvider;
import cv.lecturesight.videoanalysis.foreground.ForegroundService;
import cv.lecturesight.decorator.color.ColorHistogram;
import cv.lecturesight.framesource.FrameSource;
import cv.lecturesight.objecttracker.ObjectTracker;
import cv.lecturesight.objecttracker.TrackerObject;
import cv.lecturesight.opencl.OpenCLService;
import cv.lecturesight.opencl.api.OCLSignal;
import cv.lecturesight.opencl.api.Triggerable;
import cv.lecturesight.regiontracker.Region;
import cv.lecturesight.regiontracker.RegionTracker;
import cv.lecturesight.util.Log;
import cv.lecturesight.util.conf.Configuration;
import cv.lecturesight.util.geometry.BoundingBox;
import cv.lecturesight.util.geometry.Position;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;

/** Object Tracker Service
 *
 */
@Component(name = "lecturesight.objecttracker.simpel", immediate = true)
@Service
public class ObjectTrackerImpl implements ObjectTracker {

  private Log log = new Log("Object Tracker");
  
  @Reference
  private Configuration config;
  
  @Reference
  OpenCLService ocl;
  
  @Reference
  private RegionTracker rTracker;
  
  @Reference
  private DecoratorManager dManager;
  
  OCLSignal sig_DONE;
  private TrackerUpdate trackerUpdate;
  private Map<Integer, TrackerObject> allObjects = new TreeMap<Integer, TrackerObject>();
  private List<TrackerObject> trackedObjects = new LinkedList<TrackerObject>();
  private List<TrackerObject> hypotheses = new LinkedList<TrackerObject>();
//  private List<Region> regions = new LinkedList<Region>();
  int width_min, width_max, height_min, height_max, timeToLive, channel_number;
  float matchThreshold, chThreshold, distanceThreshold;
  private List<Color> color_list = new LinkedList<Color>();
  final static String OBJ_PROPKEY_BW_PIXELS = "obj.bw_pixels";
  final static String OBJ_PROPKEY_MOVEMENT = "obj.movement";
  final static String OBJ_PROPKEY_INSCENE = "obj.in_scene";

  @Reference
  ForegroundService fgs;
  @Reference
  private FrameSourceProvider fsp;

  protected void activate(ComponentContext cc) throws Exception {
    updateConfiguration();
    sig_DONE = ocl.getSignal(Constants.SIGNAME_DONE);
    trackerUpdate = new TrackerUpdate();
    ocl.registerTriggerable(rTracker.getSignal(RegionTracker.Signal.DONE_CORRELATION), trackerUpdate);
    log.info("Activated");
    color_list.add(Color.BLUE);
    color_list.add(Color.CYAN);
    color_list.add(Color.GREEN);
    color_list.add(Color.MAGENTA);
    color_list.add(Color.ORANGE);
    color_list.add(Color.PINK);
    color_list.add(Color.RED);
  }

  protected void deactivate(ComponentContext cc) throws Exception {
    ocl.unregisterTriggerable(rTracker.getSignal(RegionTracker.Signal.DONE_CORRELATION), trackerUpdate);
    log.info("Deactivated");
  }

  private void updateConfiguration() {
    width_min = config.getInt(Constants.PROPKEY_WIDTHMIN);
    width_max = config.getInt(Constants.PROPKEY_WIDTHMAX);
    height_min = config.getInt(Constants.PROPKEY_HEIGHTMIN);
    height_max = config.getInt(Constants.PROPKEY_HEIGHTMAX);
    timeToLive = config.getInt(Constants.PROPKEY_TTL);
    chThreshold = config.getFloat(Constants.PROPKEY_CHTHRESHOLD);
    matchThreshold = config.getFloat(Constants.PROPKEY_MATCHTHRESH);
    distanceThreshold = config.getFloat(Constants.PROPKEY_DISTANCETHRESH);
    channel_number = config.getInt(Constants.PROPKEY_CHANNEL_NUMBER);
  }

  //<editor-fold defaultstate="collapsed" desc="Getters and Setters">
  @Override
  public OCLSignal getSignal() {
    return sig_DONE;
  }

  @Override
  public TrackerObject getObject(int id) {
    return allObjects.get(id);
  }

  @Override
  public boolean isCurrentlyTracked(TrackerObject object) {
    return trackedObjects.contains(object);
  }

  @Override
  public void discardObject(TrackerObject object) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  @Override
  public Map<Integer, TrackerObject> getAllObjects() {
 //   List<TrackerObject> out = new LinkedList<TrackerObject>();
 //   out.addAll(allObjects.values());
 //   return out;
    return allObjects;
  }

  @Override
  public List<TrackerObject> getCurrentlyTracked() {
//    List<TrackerObject> out = new LinkedList<TrackerObject>();
//    for(Iterator<TrackerObject>it = trackedObjects.iterator(); it.hasNext();) {
//      TrackerObject object = it.next();
//      if(object.lastSeen() - System.currentTimeMillis() < 6000) {
//        out.add(object);
//      }
//    }
//    return out;
    return trackedObjects;
  }
  //</editor-fold>

  /** This Triggerable is fired every time the RegionTracker signals that it
   *  has finished its work.
   * 
   *  This is where the magic happens...
   */
  private class TrackerUpdate implements Triggerable {

    long currentTime;
    WritableRaster img;
    WritableRaster imgc;
    List<Region> regions;
    
    @Override
    public void triggered(OCLSignal ocls) {
      // get operational parameters from config each round so params can be changed in runtime
      updateConfiguration();
          
      BufferedImage sil = fgs.getForegroundMapHost();
      FrameSource fsrc = fsp.getFrameSource();
      BufferedImage scene = fsrc.getImageHost();
      img = sil.getRaster();
      imgc = scene.getRaster();

      currentTime = System.currentTimeMillis();

      // first get list of current foreground regions
      List<Region> regions1 = rTracker.getRegions();
      
//      if(!hypotheses.isEmpty()) {
//        for(TrackerObject obj : hypotheses) {
//          regions1 = merge(regions1, obj);
//        }
//      }
//      
      // find candidate regions by their size
      List<Region> candidates = new LinkedList<Region>();
      regions = new LinkedList<Region>();
      
      for (Region region : regions1) {
        BoundingBox bbox = region.getBoundingBox();
        if (bbox.getWidth() >= width_min
                && bbox.getWidth() <= width_max
                && bbox.getHeight() >= height_min
                && bbox.getHeight() <= height_max
                && (currentTime - region.getLastMoveTime() < timeToLive/2)) {
          candidates.add(region);
        }
        else {
          if(bbox.getWidth() <= width_max && bbox.getHeight() <= height_max
                && bbox.getWidth() > width_min/5 && bbox.getHeight() > height_min/5
                && (currentTime - region.getLastMoveTime() < timeToLive/2)) {
            regions.add(region);
          }
        }
      }
      
      List<TrackerObject> trackedObjects1 = new LinkedList<TrackerObject>();
      for(TrackerObject obj : hypotheses) {
        if(currentTime - obj.lastSeen() < timeToLive) {
          trackedObjects1.add(obj);
        }
      }

      List<TrackerObject> newHypotheses = assign_new(trackedObjects1, candidates);
      hypotheses = newHypotheses;
      
      List<TrackerObject> newTrackedObjects = new LinkedList<TrackerObject>();
      for(TrackerObject obj : hypotheses) {
        int movement = (Integer) obj.getProperty(OBJ_PROPKEY_MOVEMENT);
        if(movement > 15) {
          newTrackedObjects.add(obj);
          if(!allObjects.containsValue(obj) &&
             !allObjects.containsKey(obj.getId())) {
            allObjects.put(obj.getId(), obj);
          }
        }
      }
      
      trackedObjects = newTrackedObjects;
      
      ocl.castSignal(sig_DONE);
    }

   /**
    * Recursively assigns a list of TrackerObjects to a List of Regions
    * @param List<TrackerObject> trackerObjects
    * @param List<Region> candidates
    * @return new list with assigned and updated TrackerObjects
    */
    private List<TrackerObject> assign_new(List<TrackerObject> trackerObjects, List<Region> candidates) {
     if(trackerObjects.isEmpty() && candidates.isEmpty()) {
       return new LinkedList<TrackerObject>();
     }
     else{
       // mehr Kandidaten als TrackedObjects -> erst zuweisen, dann neu erstellen!
       if(trackerObjects.size() < candidates.size()) {
         if(trackerObjects.isEmpty()) {
           // einfach mal mit dem ersten Kandidaten anfangen
           Region cand = candidates.get(0);
           // bestes TrackerObject suchen
           TrackerObject obj = matchColorHistogram(cand, currentTime);
           // wenn es das nicht gibt, Neues erstellen
           if(obj == null) {
             obj = createTrackerObject(cand, currentTime);
             candidates.remove(cand);
             List<TrackerObject> new_list = assign_new(trackerObjects, candidates);
             new_list.add(obj);
             return new_list;
           }
           // wenn es das gibt, updaten
           else {
             updateTrackerObject(obj, cand, currentTime);
             candidates.remove(cand);
             List<TrackerObject> new_list = assign_new(trackerObjects, candidates);
             new_list.add(obj);
             return new_list;
           }
         }
         // es gibt mehr als 0 TrackerObjects und mehr als 1 Candidate
         else {
           // wir ermitteln dasjenige Paar, welches die kleinste Distanz hat.
           TrackerObject winner = null;
           Region best_region = null;
           double min_distance1 = Double.MAX_VALUE;
           for(TrackerObject obj : trackerObjects) {
             Region winner_region = null;
             double min_distance = Double.MAX_VALUE;
             for(Region r : candidates) {
               double distance = distance(obj, r);
               if(distance < min_distance) {
                 winner_region = r;
                 min_distance = distance;
               }
             }
             if(min_distance < min_distance1 && min_distance < matchThreshold) {
               min_distance1 = min_distance;
               winner = obj;
               best_region = winner_region;
             }
           }
           // wir haben ein Paar gefunden!
           if(winner != null) {
             // updaten und weiter!
             updateTrackerObject(winner, best_region, currentTime);
             candidates.remove(best_region);
             trackerObjects.remove(winner);
             List<TrackerObject> new_list = assign_new(trackerObjects, candidates);
             new_list.add(winner);
             return new_list;
           }
           // wir haben kein Paar gefunden... :(
           else {
             // wir ermitteln dasjenige TrackerObject, welches die größte Distanz
             // zu allen Kandidaten aufweist
             Region loser = null;
             double max_distance = 0;
             for(Region cand : candidates) {
               double min_distance = Double.MAX_VALUE;
               for(TrackerObject obj : trackerObjects) {
                 double distance = distance(obj, cand);
                 if(distance < min_distance) {
                   min_distance = distance;
                 }
               }
               if(min_distance > max_distance) {
                 max_distance = min_distance;
                 loser = cand;
               }
             }
             // wir sehen nach ob wir ein passendes TrackerObject auf Halde haben
             TrackerObject loser_obj = matchColorHistogram(loser, currentTime);
             // wir haben! also: schnell updaten!
             if(loser_obj != null) {
               updateTrackerObject(loser_obj, loser, currentTime);
             }
             // wir haben nüscht jefunden, ergo: neu erstellen!
             else {
               loser_obj = createTrackerObject(loser, currentTime);
             }
             candidates.remove(loser);
             List<TrackerObject> new_list = assign_new(trackerObjects, candidates);
             new_list.add(loser_obj);
           }
         }
       }
       if(trackerObjects.size() == candidates.size()) {
         // wir suchen die besten matches raus, wenn einer schlechter als 
         // threshold ist, heißt das wohl, dass wir nen neuen kandidaten haben.
         TrackerObject winner = null;
         Region best_region = null;
         double min_distance1 = Double.MAX_VALUE;
         for(TrackerObject obj : trackerObjects) {
           Region winner_region = null;
           double min_distance = Double.MAX_VALUE;
           for(Region r : candidates) {
             double distance = distance(obj, r);
             if(distance < min_distance) {
               winner_region = r;
               min_distance = distance;
             }
           }
           if(min_distance < min_distance1) {
             min_distance1 = min_distance;
             winner = obj;
             best_region = winner_region;
           }
         }
         // wir haben da nen guten Match, also hauen wir das auch aufeinander!!
         if(min_distance1 < matchThreshold) {
           updateTrackerObject(winner, best_region, currentTime);
           candidates.remove(best_region);
           trackerObjects.remove(winner);
           List<TrackerObject> new_list = assign_new(trackerObjects, candidates);
           new_list.add(winner);
           return new_list;
         }
         // der Match war wohl nicht so toll... na dann: neu erstellen!
         else {
           TrackerObject new_to = createTrackerObject(best_region, currentTime);
           candidates.remove(best_region);
           List<TrackerObject> new_list = assign_new(trackerObjects, candidates);
           new_list.add(new_to);
           return new_list;
         }
       }
       if(trackerObjects.size() > candidates.size()) {
         // wir haben da wohl mindestens ein TrackerObject zu viel, bzw. nen Kandidaten zu wenig...
         // Idee ist: Wenn es nen Kandidaten gibt, wird der wohl mal zuerst gematcht.
         // falls das nicht der Fall ist, suchen wir uns die nächstbeste Region...
         if(!candidates.isEmpty()) {
           TrackerObject winner = null;
           Region best_region = null;
           double min_distance1 = Double.MAX_VALUE;
           for(TrackerObject obj : trackerObjects) {
             Region winner_region = null;
             double min_distance = Double.MAX_VALUE;
             for(Region r : candidates) {
               double distance = distance(obj, r);
               if(distance < min_distance) {
                 winner_region = r;
                 min_distance = distance;
               }
             }
             if(min_distance < min_distance1) {
               min_distance1 = min_distance;
               winner = obj;
               best_region = winner_region;
             }
           }
           // wir haben da nen guten Match, also hauen wir das auch aufeinander!!
           if(min_distance1 < matchThreshold) {
             updateTrackerObject(winner, best_region, currentTime);
             candidates.remove(best_region);
             trackerObjects.remove(winner);
             List<TrackerObject> new_list = assign_new(trackerObjects, candidates);
             new_list.add(winner);
             return new_list;
           }
           // der Match war wohl nicht so toll... na dann: neu erstellen!
           else {
             TrackerObject new_to = createTrackerObject(best_region, currentTime);
             candidates.remove(best_region);
             List<TrackerObject> new_list = assign_new(trackerObjects, candidates);
             new_list.add(new_to);
             return new_list;
           }
         }
         // Ach Mist, es gibt keine Kandidaten, aber noch TrackerObjects...
         else {
           // wir suchen uns einfach die beste Region!
           double min_distance = Double.MAX_VALUE;
           TrackerObject winner = null;
           Region winner_region = null;
           for(TrackerObject obj : trackerObjects) {
             Region w_region = null;
             double min_distance1 = Double.MAX_VALUE;
             for(Region r : regions) {
               double dist = distance(obj, r);
               if(dist < min_distance1) {
                 min_distance1 = dist;
                 w_region = r;
               }
             }
             if(min_distance1 < min_distance && min_distance1 < distanceThreshold) {
               min_distance = min_distance1;
               winner = obj;
               winner_region = w_region;
             }
           }
           // wir haben eine gefunden... :)
           if(winner != null) {
             // jetzt wird das TrackerObject um die entsprechende X/Y-Koordinate
             // bewegt...
             moveTrackerObject(winner, winner_region.getCentroid(), currentTime);
             regions.remove(winner_region);
             trackerObjects.remove(winner);
             List<TrackerObject> new_list = assign_new(trackerObjects, candidates);
             new_list.add(winner);
             return new_list;
           }
           // wir haben keine gefunden?? na egal, einfach alle TrackerObjects behalten
           else {
             List<TrackerObject> new_list = new LinkedList<TrackerObject>();
             new_list.addAll(trackerObjects);
             return new_list;
           }
         }
       }
     }
     return null;
    }
    
    private Region findMatchingRegion(TrackerObject object, List<Region> regions) {
      Region result = null;
      //BoundingBox bbox = (BoundingBox)object.getProperty(Constants.OBJ_PROPKEY_BBOX);
      Position centroid = (Position)object.getProperty(OBJ_PROPKEY_CENTROID);
      double error = Double.MAX_VALUE;
      for (Region region : regions) {
        BoundingBox bbox = region.getBoundingBox();
        double err = centroid.distance(region.getCentroid());
        //System.out.println("Error = " + err);
        if (err <= distanceThreshold && bbox.getWidth() >= width_min/3
                && bbox.getWidth() <= width_max
                && bbox.getHeight() >= height_min/3
                && bbox.getHeight() <= height_min) {
          if (err < error) {
            error = err;
            result = region;
          }
        }
      }
      return result;
    }
    
    private Region findMatchingRegion(Region region, List<Region> regions) {
      Region result = null;
      Position centroid = region.getCentroid();
      double error = Double.MAX_VALUE;
      for (Region r1 : regions) {
        double err = centroid.distance(r1.getCentroid());
        if(err <= distanceThreshold) {
          if(err < error) {
            error = err;
            result = r1;
          }
        }
      }
      return result;
    }

    private TrackerObject matchColorHistogram(Region r, long currentTime) {
      // we set the actual element to the element to which we have to compare the
      // rest. This is to assure, that match does not fail if called with an empty
      // list.
      if(allObjects.isEmpty()) {
        return null;
      }
      
      double min_distance = Double.MAX_VALUE;
      TrackerObject result = null;
      ColorHistogram ch = new ColorHistogram(img, imgc, r.getBoundingBox(), channel_number);
      
      for(TrackerObject act : allObjects.values()) {
        if(act.lastSeen() - currentTime != 0) {
          try {
            ColorHistogram ch1 = (ColorHistogram) act.getProperty(OBJ_PROPKEY_COLOR_HISTOGRAM);
            double distance = ch.bhattacharya_distance(ch1);
            if(distance < min_distance && distance < chThreshold) {
              min_distance = distance;
              result = act;
            }
          } catch (Exception ex) {
            Logger.getLogger(ObjectTrackerImpl.class.getName()).log(Level.SEVERE, null, ex);
          }
        }
      }
      return result;
    }

    private TrackerObject createTrackerObject(Region region, long time) {
      TrackerObject object = new TrackerObject(time);
      object.setProperty(OBJ_PROPKEY_REGION, region);
      object.setProperty(OBJ_PROPKEY_CENTROID, region.getCentroid().clone());
      object.setProperty(OBJ_PROPKEY_BBOX, region.getBoundingBox().clone());
      object.setProperty(OBJ_PROPKEY_WEIGHT, region.getWeight());
      object.setProperty(OBJ_PROPKEY_MOVEMENT, 0);
      object.setProperty(OBJ_PROPKEY_INSCENE, 0);
//      allObjects.put(object.getId(), object);
      dManager.applyDecorators(CallType.EACHFRAME, object);
      Color c = Color.yellow;
      if(!color_list.isEmpty()) {
        c = color_list.remove(color_list.size()-1);
      }
      object.setProperty(OBJ_PROPKEY_COLOR, c);
      return object;
    }

    private TrackerObject updateTrackerObject(TrackerObject object, Region region, long time) {
      object.setLastSeen(time);
      int in_scene = (Integer) object.getProperty(OBJ_PROPKEY_INSCENE);
      object.setProperty(OBJ_PROPKEY_INSCENE, ++in_scene);
      Position centroid = (Position) object.getProperty(OBJ_PROPKEY_CENTROID);
      Position centroid1 = region.getCentroid();
      double distance = centroid.distance(centroid1);
      int movement = (Integer) object.getProperty(OBJ_PROPKEY_MOVEMENT);
      if(distance < width_max/5 && distance > 0) {
        object.setProperty(OBJ_PROPKEY_MOVEMENT, ++movement);
      }
//      else {
//        object.setProperty(OBJ_PROPKEY_MOVEMENT, --movement);
//      }
      object.setProperty(OBJ_PROPKEY_REGION, region);
      object.setProperty(OBJ_PROPKEY_CENTROID, centroid1.clone());
      BoundingBox bbox = region.getBoundingBox().clone();
      object.setProperty(OBJ_PROPKEY_BBOX, bbox);
      object.setProperty(OBJ_PROPKEY_WEIGHT, region.getWeight());
      if(!checkSplitter(region) && bbox.getWidth() >= width_min
                && bbox.getWidth() <= width_max
                && bbox.getHeight() >= height_min
                && bbox.getHeight() <= height_max) {
        dManager.applyDecorators(CallType.EACHFRAME, object);
      }
      return object;
    }
    
    private TrackerObject moveTrackerObject(TrackerObject object, Position pos, long time) {
      Position centroid = ((Position) object.getProperty(OBJ_PROPKEY_CENTROID)).clone();
      double d_x = -(centroid.getX()-pos.getX())*0.6;
      double d_y = -(centroid.getY()-pos.getY())*0.6;
      BoundingBox bbox = ((BoundingBox) object.getProperty(OBJ_PROPKEY_BBOX)).clone();
      if(Math.abs(d_x-d_y) < 1) {
        bbox.setMin(new Position((int)(bbox.getMin().getX()+d_x),(int)(bbox.getMin().getY()+d_y)));
        bbox.setMax(new Position((int)(bbox.getMax().getX()+d_x),(int)(bbox.getMax().getY()+d_y)));
        centroid.setX((int)(centroid.getX()+d_x));
        centroid.setY((int)(centroid.getY()+d_y));
      }
      else {
        if(d_x > d_y) {
          bbox.setMin(new Position((int)(bbox.getMin().getX()+d_x),bbox.getMin().getY()));
          bbox.setMax(new Position((int)(bbox.getMax().getX()+d_x),bbox.getMax().getY()));
          centroid.setX((int)(centroid.getX()+d_x));
        }
        if(d_y > d_x) {
          bbox.setMin(new Position(bbox.getMin().getX(),(int) (bbox.getMin().getY()+d_y)));
          bbox.setMax(new Position(bbox.getMax().getX(),(int) (bbox.getMax().getY()+d_y)));
          centroid.setY((int)(centroid.getY()+d_y));
        }
      }
      double distance = Math.sqrt(d_x*d_x + d_y*d_y);
      int movement = (Integer) object.getProperty(OBJ_PROPKEY_MOVEMENT);
      if(distance < width_max/5 && distance > 0) {
        object.setProperty(OBJ_PROPKEY_MOVEMENT, ++movement);
      }
      object.setLastSeen(time);
      int in_scene = (Integer) object.getProperty(OBJ_PROPKEY_INSCENE);
      object.setProperty(OBJ_PROPKEY_INSCENE, ++in_scene);
      object.setProperty(OBJ_PROPKEY_CENTROID, centroid);
      object.setProperty(OBJ_PROPKEY_BBOX, bbox);
      return object;
    }

    private boolean checkSplitter(Region lastRegion) {
      if(lastRegion.isSplitter()) {
        Set<Region> regions1 = lastRegion.getGroupMembers();
        for(Region r : regions1) {
          BoundingBox bbox = r.getBoundingBox();
          if(bbox.getWidth()     >= width_min
             && bbox.getWidth()  <= width_max
             && bbox.getHeight() >= height_min
             && bbox.getHeight() <= height_max) {
            return true;
          }
        }
      }
      return false;
    }

    /**
     * Measures the hypothetic distance between a region and a trackerObject
     *
     * @param TrackerObject obj
     * @param Region r
     *
     * @return returns distance measure.
     **/
    private double distance(TrackerObject object, Region r) {
      try {
        // Compute ColorHistogram distance
        BoundingBox bbox = r.getBoundingBox();
        ColorHistogram ch1 = new ColorHistogram(img, imgc, bbox, channel_number);
        ColorHistogram ch2 = (ColorHistogram) object.getProperty(OBJ_PROPKEY_COLOR_HISTOGRAM);
        double d2 = ch1.bhattacharya_distance(ch2);
        // if some region splitted beforehand, we don't consider the physical
        // distance
        if(!checkSplitter(r)) {
          // compute physical distance
          Position centroid = (Position) object.getProperty(OBJ_PROPKEY_CENTROID);
          Position r_centroid = r.getCentroid();
          double diag = Math.sqrt(img.getWidth()*img.getWidth()+img.getHeight()*img.getHeight())*2;
          double d1 = centroid.distance(r_centroid)/diag;
//          if(d1 < distanceThreshold) {
          return Math.sqrt(d1*d2);
//          }
        }
        return d2;
      } catch (Exception ex) {
        Logger.getLogger(ObjectTrackerImpl.class.getName()).log(Level.SEVERE, null, ex);
      }
      return Double.MAX_VALUE;
    } 
  }
  
  private List<Region> merge(List<Region> regions, TrackerObject obj) {
    BoundingBox bbox = ((BoundingBox) obj.getProperty(OBJ_PROPKEY_BBOX)).clone();
    bbox.setMin(new Position(bbox.getMin().getX()-width_min/2,bbox.getMin().getY()-width_min/2));
    bbox.setMax(new Position(bbox.getMax().getX()+width_min/2,bbox.getMax().getY()+width_min/2));
    List<Region> return_regions = new LinkedList();
    for(Region r : regions) {
      Position p = r.getCentroid();
      if(bbox.contains(p)) {
        return_regions.add(r);
      }
    }
    if(return_regions.size() > 1) {
      regions.removeAll(return_regions);

      int min_x = Integer.MAX_VALUE, min_y = Integer.MAX_VALUE, max_x = 0, max_y = 0;
      for(Region r : return_regions) {
        if(r.getBoundingBox().getMin().getX() < min_x)
          min_x = r.getBoundingBox().getMin().getX();
        if(r.getBoundingBox().getMin().getY() < min_y)
          min_y = r.getBoundingBox().getMin().getY();
        if(r.getBoundingBox().getMax().getX() > max_x)
          max_x = r.getBoundingBox().getMax().getX();
        if(r.getBoundingBox().getMax().getY() > max_y)
          max_y = r.getBoundingBox().getMax().getY();
      }
      Region r = return_regions.get(0);
      BoundingBox bbox1 = new BoundingBox(
              new Position(min_x, min_y), new Position(max_x, max_y));
      Position new_centroid = new Position(min_x+(max_x - min_x)/2,
              min_y+(max_y - min_y)/2);
      r.update(r.getLabel(), r.getLastMoveTime(), new_centroid, bbox1, r.getWeight());
      
      log.info("Ich habe "+return_regions.size()+" Regions gemergt!");

      regions.add(r);
    }
    return regions;
  }
}
