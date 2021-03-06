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
package cv.lecturesight.framesource.videofile;

import cv.lecturesight.framesource.FrameGrabber;
import cv.lecturesight.framesource.FrameSourceException;
import cv.lecturesight.framesource.FrameGrabberFactory;
import java.io.File;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Properties;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.gstreamer.Gst;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.log.LogService;

/** Implementation of Service API
 *
 */
@Component(name="lecturesight.framesource.videofile",immediate=true)
@Service
@Properties({
@Property(name="cv.lecturesight.framesource.name",value="Video File"),
@Property(name="cv.lecturesight.framesource.type",value="file")
})
public class VideoFileFrameSourceFactory implements FrameGrabberFactory {

  @Reference
  private LogService log;

  private List<VideoFilePipeline> children = new LinkedList<VideoFilePipeline>();

  protected void activate(ComponentContext cc) {
    log.log(log.LOG_INFO, "Activating VideoFileFrameSource");
    // init gstreamer
    Gst.init();
  }

  protected void deactivate(ComponentContext cc) {
    log.log(log.LOG_INFO, "Deactivating VideoFileFrameSource");
    // stop all created pipelines
    for (Iterator<VideoFilePipeline> it = children.iterator(); it.hasNext();) {
      VideoFilePipeline child = it.next();
      child.stop();
    }
    // deinit gstreamer
    Gst.deinit();
  }

  @Override
  public FrameGrabber createFrameGrabber(String input, Map<String,String> conf) throws FrameSourceException {
    File videoFile = new File(input);
    if (!videoFile.exists() || !videoFile.isFile()) {
      throw new FrameSourceException("Not a valid file: " + input);
    }

    // attempt to create the gst pipline
    try {
      VideoFilePipeline grabber = new VideoFilePipeline(videoFile);
      children.add(grabber);
      log.log(log.LOG_INFO, "Created FrameGrabber on video file " + input);
      return grabber;
    } catch (UnableToLinkElementsException e) {
      throw new FrameSourceException("Error while creating FrameGrabber: " + e.getMessage());
    }
  }

  @Override
  public void destroyFrameGrabber(FrameGrabber fg) throws FrameSourceException {
   try {
      ((VideoFilePipeline)fg).stop();
    } catch (Exception e) {
      throw new FrameSourceException("Failed to shut down VideoFilePipeline. ", e);
    }
  }
}
